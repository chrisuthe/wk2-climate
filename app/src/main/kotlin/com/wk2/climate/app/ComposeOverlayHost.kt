package com.wk2.climate.app

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Hosts Compose content in a raw `WindowManager` window.
 *
 * `ComposeView` needs a lifecycle owner, a saved-state registry and a
 * ViewModel store, all of which an Activity supplies and a bare window does
 * not. Without them the view attaches and then renders nothing, which is a
 * confusing failure -- it looks like a layout bug rather than a missing owner.
 * That is why this lives in one commented place rather than being repeated at
 * each call site.
 *
 * One instance owns one window. The bar keeps one alive for the life of the
 * service; the climate panel creates and destroys one on demand, so `hide()`
 * and `destroy()` are load-bearing.
 */
class ComposeOverlayHost(private val context: Context) :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: View? = null

    val isShowing: Boolean get() = view != null

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun show(params: WindowManager.LayoutParams, content: @Composable () -> Unit) {
        if (view != null) return
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@ComposeOverlayHost)
            setViewTreeViewModelStoreOwner(this@ComposeOverlayHost)
            setViewTreeSavedStateRegistryOwner(this@ComposeOverlayHost)
            setContent { content() }
        }
        // Must be RESUMED before attach: ComposeView will not compose while the
        // owner it found in the view tree is below STARTED.
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        windowManager.addView(composeView, params)
        view = composeView
    }

    fun hide() {
        val v = view ?: return
        view = null
        runCatching { windowManager.removeView(v) }
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun destroy() {
        hide()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
