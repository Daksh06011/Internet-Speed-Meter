package com.netspeedtest

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.netspeedtest.data.AppSettings
import com.netspeedtest.speedtest.TestError
import com.netspeedtest.ui.components.Haptics
import com.netspeedtest.ui.nav.Route
import com.netspeedtest.ui.nav.Screen
import com.netspeedtest.ui.nav.ScreenEnv
import com.netspeedtest.ui.nav.ScreenHost
import com.netspeedtest.ui.nav.SheetHost
import com.netspeedtest.ui.screens.BatteryScreen
import com.netspeedtest.ui.screens.ChargingScreen
import com.netspeedtest.ui.screens.HistoryScreen
import com.netspeedtest.ui.screens.HomeScreen
import com.netspeedtest.ui.screens.MemoryScreen
import com.netspeedtest.ui.screens.NetworkScreen
import com.netspeedtest.ui.screens.ResultScreen
import com.netspeedtest.ui.screens.SettingsScreen
import com.netspeedtest.ui.screens.TemperatureScreen
import com.netspeedtest.ui.theme.Palette
import com.netspeedtest.ui.theme.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The single activity. It builds the view tree in code (no inflation), wires the
 * navigator to the screen host and maps lifecycle to "foreground work allowed".
 */
class MainActivity : Activity() {
    private lateinit var host: ScreenHost
    private lateinit var sheets: SheetHost
    private var scope: CoroutineScope? = null
    private var backCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = graph
        val settings = graph.settings.settings.value
        val palette = Palette.resolve(this, settings.theme)
        val ui = Ui(this, palette)
        Haptics.enabled = settings.haptics

        sheets = SheetHost(ui)
        val env = ScreenEnv(ui, graph, sheets)
        host = ScreenHost(ui) { route -> createScreen(env, route) }
        val root = FrameLayout(this).apply {
            setBackgroundColor(palette.background)
            addView(host, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(sheets, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        root.setOnApplyWindowInsetsListener { _, insets -> applyInsets(insets) }
        setContentView(root)
        setupWindow(palette) // after setContentView: the insets controller needs the decor view
        if (savedInstanceState == null) playLaunchAnimation(root)

        graph.navigator.listener = { route, forward ->
            sheets.dismiss()
            host.show(route, forward)
            updateBackCallback()
        }
        host.show(graph.navigator.current, forward = null)
        updateBackCallback()

        val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = activityScope
        activityScope.launch {
            // Theme changes rebuild the UI; the test and back stack survive in the app graph.
            graph.settings.settings.map { it.theme }.distinctUntilChanged().drop(1).collect { recreate() }
        }
        activityScope.launch {
            combine(graph.settings.settings, graph.speedTest.uiState) { s: AppSettings, t -> s to t.isRunning }
                .collect { (s, running) ->
                    Haptics.enabled = s.haptics
                    if (s.keepScreenOn && running) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
        }
    }

    /**
     * Android 12+ shows the animated gauge icon on the launch screen. On a cold start we
     * let it finish (it's under a second) and then fade/zoom the splash away smoothly.
     * Skipped entirely when the user has turned animations off.
     */
    private fun playLaunchAnimation(content: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !android.animation.ValueAnimator.areAnimatorsEnabled()) return
        val start = android.os.SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (android.os.SystemClock.uptimeMillis() - start < LAUNCH_ANIMATION_MS) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        splashScreen.setOnExitAnimationListener { splash ->
            splash.animate()
                .alpha(0f)
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(220)
                .setInterpolator(com.netspeedtest.ui.components.Motion.Standard)
                .withEndAction { splash.remove() }
                .start()
        }
    }

    private fun createScreen(env: ScreenEnv, route: Route): Screen = when (route) {
        Route.Home -> HomeScreen(env)
        is Route.Result -> ResultScreen(env, route.timestampMillis, route.fresh)
        Route.History -> HistoryScreen(env)
        Route.Settings -> SettingsScreen(env)
        Route.Battery -> BatteryScreen(env)
        Route.Charging -> ChargingScreen(env)
        Route.Temperature -> TemperatureScreen(env)
        Route.Network -> NetworkScreen(env)
        Route.Memory -> MemoryScreen(env)
    }

    @Suppress("DEPRECATION")
    private fun setupWindow(palette: Palette) {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(palette.background))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (palette.isDark) 0 else light, light)
        } else {
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (!palette.isDark) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = flags
        }
    }

    @Suppress("DEPRECATION")
    private fun applyInsets(insets: WindowInsets): WindowInsets {
        val (l, t, r, b) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            listOf(bars.left, bars.top, bars.right, bars.bottom)
        } else {
            listOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
        host.setInsets(l, t, r, b)
        sheets.setBottomInset(b)
        return insets
    }

    override fun onStart() {
        super.onStart()
        host.setForeground(true)
    }

    override fun onStop() {
        super.onStop()
        host.setForeground(false)
        // Never keep testing unseen: leaving the app stops the test (rotation does not).
        if (!isChangingConfigurations && graph.speedTest.uiState.value.isRunning) {
            graph.speedTest.cancel(TestError.Backgrounded)
        }
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        host.dispose()
        if (graph.navigator.listener != null && !isChangingConfigurations) graph.speedTest.cancel()
        graph.navigator.listener = null
        backCallback?.let { if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        super.onDestroy()
    }

    /** True if something in the app consumed the back gesture. */
    private fun handleBack(): Boolean = when {
        sheets.isShowing -> { sheets.dismiss(); true }
        host.screen?.onBack() == true -> true
        else -> graph.navigator.pop()
    }

    /** On Android 13+ register a callback only while there is somewhere to go back to, so predictive back works. */
    private fun updateBackCallback() {
        if (Build.VERSION.SDK_INT < 33) return
        val needed = graph.navigator.canGoBack || sheets.isShowing
        val existing = backCallback
        if (needed && existing == null) {
            val callback = OnBackInvokedCallback { if (!handleBack()) finish(); updateBackCallback() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            backCallback = callback
        } else if (!needed && existing != null) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(existing)
            backCallback = null
        }
    }

    @Deprecated("Used below Android 13; newer versions use OnBackInvokedCallback.")
    override fun onBackPressed() {
        if (!handleBack()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private companion object {
        /** Matches windowSplashScreenAnimationDuration in values-v31/themes.xml. */
        const val LAUNCH_ANIMATION_MS = 1_000L
    }
}
