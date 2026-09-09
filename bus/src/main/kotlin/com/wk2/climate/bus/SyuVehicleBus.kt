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
            // Seed before announcing the connection, so the first frame the UI
            // draws already has values rather than dashes. See seedAll().
            seedAll()
            // connected means the climate module is bound, not that something
            // is bound: SOUND or MAIN alone is a degraded slot, but CANBUS
            // alone missing is every climate command silently dropped while
            // the bar still looks live.
            val climateBound = modules.containsKey(Signal.MODULE_CANBUS)
            if (!climateBound) {
                Log.e(TAG, "CANBUS (module ${Signal.MODULE_CANBUS}) unavailable — climate is unreachable, hiding the bar")
            }
            _connected.value = climateBound
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

    /**
     * Registers one callback per module against every code in that module.
     *
     * [verbose] is on for the connect-time call, whose `module 7: registered
     * 20/20 codes` line is the only positive evidence that the bus came up.
     * [refresh] turns it off: it runs up to four times in the first eight
     * seconds, and this unit's main log ring buffer is 256 KiB and wraps in
     * well under a minute, so a refresh must not spend it on lines that say
     * the same thing the connect line already said.
     *
     * Returns the number of codes that failed, so a quiet caller can still
     * report a problem.
     */
    private fun registerAll(verbose: Boolean = true): Int {
        var failed = 0
        for ((module, binder) in modules) {
            val callback = callbacks.getOrPut(module) { ModuleCallback(module) }
            val signals = Signal.inModule(module)
            var ok = 0
            for (signal in signals) {
                if (runCatching { register(binder, callback, signal.code) }.isSuccess) {
                    ok++
                } else {
                    failed++
                    if (verbose) Log.w(TAG, "register failed for $signal")
                }
            }
            if (verbose) Log.i(TAG, "module $module: registered $ok/${signals.size} codes")
        }
        return failed
    }

    /**
     * The OEM's `notify()` mechanism: re-register the same callbacks.
     *
     * `Registrar.notify(int... codes)` in the decompiled vendor code does
     * nothing else, so re-registration *is* their refresh primitive. Reuses
     * the existing [ModuleCallback] per module from [callbacks], so no
     * duplicate callbacks accumulate and the vendor service is being handed
     * exactly the binder it already holds.
     *
     * Logs nothing on success — the caller logs the attempt, and this runs
     * repeatedly on a log buffer that wraps in seconds.
     */
    override fun refresh() {
        if (modules.isEmpty()) {
            Log.w(TAG, "refresh: no modules bound, nothing to re-register")
            return
        }
        val failed = registerAll(verbose = false)
        if (failed > 0) Log.w(TAG, "refresh: $failed codes failed to re-register")
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

    /**
     * Reads the current value of every registered signal and feeds it through
     * the same path a pushed update takes.
     *
     * Registering is not enough. The vendor service notifies on **change**, so
     * a client that only registers sees nothing until something moves.
     * Observed on the vehicle after an ignition cycle: the head unit restarts,
     * this process comes up with an empty state map, registration succeeds for
     * all 20 climate codes — and the bar still shows dashes and a seemingly
     * powered-off system until the driver presses a setpoint, whose change
     * finally produces the first callback.
     *
     * The probing rule "registered but silent means unchanged, not unfitted" is
     * about *reading* the bus. It is a trap when building a UI, because
     * unchanged is precisely the state a UI has to be able to draw. So ask,
     * rather than wait to be told.
     *
     * Runs synchronously before `connected` is announced, so no frame is ever
     * composed from an empty map.
     */
    private fun seedAll() {
        for ((module, binder) in modules) {
            val signals = Signal.inModule(module)
            var ok = 0
            for (signal in signals) {
                val read = runCatching { readValue(binder, signal.code) }
                read.exceptionOrNull()?.let { Log.w(TAG, "get failed for $signal", it) }
                val ints = read.getOrNull() ?: continue
                onUpdate(module, signal.code, ints)
                ok++
            }
            Log.i(TAG, "module $module: seeded $ok/${signals.size} values")
        }
    }

    /**
     * `IRemoteModule.get(code, int[], float[], String[])`, transaction 2.
     *
     * The reply is `writeNoException()`, then a presence `int` — 0 when the
     * service holds no `ModuleObject` for that code — then that object's three
     * arrays. Returns null for absent, so a code the vehicle does not report is
     * distinguishable from one reporting a value.
     */
    private fun readValue(module: IBinder, code: Int): IntArray? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(MODULE_DESC)
            data.writeInt(code)
            // An EMPTY array, not null. The OEM's own helper takes `int...
            // params` and calls `get(code, params, null, null)` with no
            // arguments, which is `new int[0]` — length 0, not the -1 that
            // writeIntArray(null) puts on the wire. A server reading
            // params.length gets a very different parcel from each.
            data.writeIntArray(EMPTY_PARAMS)
            data.writeFloatArray(null)
            data.writeStringArray(null)
            module.transact(TXN_GET, data, reply, 0)
            reply.readException()
            if (reply.readInt() == 0) return null
            return reply.createIntArray()
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
        private const val TXN_GET = 2
        private const val TXN_REGISTER = 3
        private const val TXN_UNREGISTER = 4
        private const val TXN_UPDATE = 1

        /** The flag value the OEM's own client passes. */
        private const val REGISTER_FLAG = 1

        private val EMPTY_PARAMS = IntArray(0)
    }
}
