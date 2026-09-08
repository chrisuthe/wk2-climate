package com.wk2.climate.bus

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The real [VehicleBus], talking to the SYU vendor IPC framework.
 *
 * The framework is a three-interface AIDL surface in `com.syu.ipc` and is
 * **not permission-gated** — an ordinary third-party app may bind it and both
 * read and write. Proxies are hand-written because we do not have the AIDL.
 *
 * Transaction ids are assigned by declaration order, so guessing calls the
 * wrong method. These are recovered from the vendor APK and verified live:
 *
 * ```
 * IRemoteToolkit  getRemoteModule = 1
 * IRemoteModule   cmd = 1, get = 2, register = 3, unregister = 4
 * IModuleCallback update = 1
 * ```
 *
 * Two things are easy to get wrong and are handled here:
 *
 *  - **One callback per module.** `update()` carries the code but not the
 *    module, and code 2 is `U_STANDBY` on MAIN and `U_VOL` on SOUND. A shared
 *    callback cannot tell them apart.
 *  - **Package visibility.** The `<queries>` entry in this module's manifest
 *    is required or `bindService` fails *silently* on Android 11+ even though
 *    the vendor service is exported.
 *
 * This class carries **no derivation logic at all** — every interpretation
 * that could be wrong lives in the unit-tested value types — which is why the
 * absence of unit tests here costs little.
 */
class SyuVehicleBus(private val context: Context) : VehicleBus {

    private val _state = MutableStateFlow(ClimateState.EMPTY)
    override val state: StateFlow<ClimateState> = _state.asStateFlow()

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Module id -> the module's remote binder. */
    private val modules = mutableMapOf<Int, IBinder>()

    /** Module id -> the callback registered against it. Held to keep it alive. */
    private val callbacks = mutableMapOf<Int, ModuleCallback>()

    fun connect() {
        val intent = Intent(TOOLKIT_ACTION).setPackage(VENDOR_PACKAGE)
        val requested = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        if (!requested) {
            Log.e(TAG, "bindService refused — check the <queries> package visibility entry")
        }
    }

    fun disconnect() {
        unregisterAll()
        runCatching { context.unbindService(serviceConnection) }
        modules.clear()
        _connected.value = false
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            modules.clear()
            for (module in Signal.modules()) {
                val binder = runCatching { getRemoteModule(service, module) }.getOrNull()
                if (binder == null) {
                    Log.e(TAG, "module $module unavailable")
                    continue
                }
                modules[module] = binder
            }
            registerAll()
            _connected.value = modules.isNotEmpty()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _connected.value = false
            modules.clear()
            callbacks.clear()
        }
    }

    // ---- IRemoteToolkit.getRemoteModule(int) = txn 1 ----

    private fun getRemoteModule(toolkit: IBinder, moduleId: Int): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(TOOLKIT_DESC)
            data.writeInt(moduleId)
            toolkit.transact(TXN_GET_MODULE, data, reply, 0)
            reply.readException()
            reply.readStrongBinder()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    // ---- IRemoteModule.register(cb, code, flag) = txn 3 ----

    private fun registerAll() {
        for ((module, binder) in modules) {
            val callback = callbacks.getOrPut(module) { ModuleCallback(module) }
            val signals = Signal.inModule(module)
            var ok = 0
            for (signal in signals) {
                if (runCatching { register(binder, callback, signal.code) }.isSuccess) {
                    ok++
                } else {
                    Log.w(TAG, "register failed for $signal")
                }
            }
            Log.i(TAG, "module $module: registered $ok/${signals.size} codes")
        }
    }

    private fun register(module: IBinder, callback: ModuleCallback, code: Int) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(MODULE_DESC)
            data.writeStrongBinder(callback.asBinder())
            data.writeInt(code)
            data.writeInt(REGISTER_FLAG)
            module.transact(TXN_REGISTER, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun unregisterAll() {
        for ((module, binder) in modules) {
            val callback = callbacks[module] ?: continue
            for (signal in Signal.inModule(module)) {
                runCatching {
                    val data = Parcel.obtain()
                    val reply = Parcel.obtain()
                    try {
                        data.writeInterfaceToken(MODULE_DESC)
                        data.writeStrongBinder(callback.asBinder())
                        data.writeInt(signal.code)
                        binder.transact(TXN_UNREGISTER, data, reply, 0)
                        reply.readException()
                    } finally {
                        reply.recycle()
                        data.recycle()
                    }
                }
            }
        }
        callbacks.clear()
    }

    // ---- IRemoteModule.cmd(code, int[], float[], String[]) = txn 1 ----

    override fun send(command: Command) {
        val binder = modules[command.module]
        if (binder == null) {
            Log.w(TAG, "dropping $command — module ${command.module} not bound")
            return
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(MODULE_DESC)
            data.writeInt(command.code)
            data.writeIntArray(command.payload)
            data.writeFloatArray(null)
            data.writeStringArray(null)
            binder.transact(TXN_CMD, data, reply, 0)
            reply.readException()
        } catch (t: Throwable) {
            Log.e(TAG, "send $command failed", t)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    // ---- IModuleCallback.update(code, int[], float[], String[]) = txn 1 ----

    /**
     * One instance per module. The module id is captured here because the
     * vendor callback does not carry it, and codes are unique only within a
     * module.
     */
    private inner class ModuleCallback(private val module: Int) : Binder(), IInterface {

        init {
            attachInterface(this, CALLBACK_DESC)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(CALLBACK_DESC)
                return true
            }
            if (code != TXN_UPDATE) return super.onTransact(code, data, reply, flags)

            data.enforceInterface(CALLBACK_DESC)
            val signalCode = data.readInt()
            val ints = data.createIntArray()
            // Read the remaining parameters even though we do not use them:
            // the parcel must be consumed in declaration order.
            data.createFloatArray()
            data.createStringArray()

            onUpdate(module, signalCode, ints)
            reply?.writeNoException()
            return true
        }
    }

    private fun onUpdate(module: Int, code: Int, ints: IntArray?) {
        val signal = Signal.of(module, code) ?: return
        val value = ints?.firstOrNull() ?: return
        _state.value = _state.value.with(signal, value)
    }

    companion object {
        private const val TAG = "wk2-bus"

        private const val VENDOR_PACKAGE = "com.syu.ms"
        private const val TOOLKIT_ACTION = "com.syu.ms.toolkit"

        private const val TOOLKIT_DESC = "com.syu.ipc.IRemoteToolkit"
        private const val MODULE_DESC = "com.syu.ipc.IRemoteModule"
        private const val CALLBACK_DESC = "com.syu.ipc.IModuleCallback"

        private const val TXN_GET_MODULE = 1
        private const val TXN_CMD = 1
        private const val TXN_REGISTER = 3
        private const val TXN_UNREGISTER = 4
        private const val TXN_UPDATE = 1

        /** The flag value the OEM's own client passes. */
        private const val REGISTER_FLAG = 1
    }
}
