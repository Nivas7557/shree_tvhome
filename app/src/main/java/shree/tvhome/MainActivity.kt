// Important instructions:
// - Keep MainActivity focused on lifecycle, receivers, sleep timer countdown, and delegating UI/panels to HomeScreen and PanelHub.
// - Unregister all dynamic receivers and remove handler callbacks cleanly in onDestroy to prevent leaks and extra RAM usage.
// - Avoid forced focus traps; let natural D-Pad and FocusRouter handle navigation cleanly.
package shree.tvhome

import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.LiveBackdrop
import shree.tvhome.core.SleepReminderReceiver
import shree.tvhome.core.Themes
import shree.tvhome.design.AppTileDesign
import shree.tvhome.design.AppsShelfDesign
import shree.tvhome.design.ClockWeatherCardDesign
import shree.tvhome.panels.PictureAudioPanel
import shree.tvhome.panels.WifiPanel

class MainActivity : Activity() {
    companion object {
        private const val REQ_PICK_WALLPAPER = 1001
        private const val REQ_PICK_PROFILE_PIC = 1002
        const val ACTION_ENTER_AMBIENT = "shree.tvhome.ACTION_ENTER_AMBIENT"
    }
    private lateinit var rootLayout: FrameLayout
    private lateinit var settings: AppSettings
    private lateinit var homeScreen: HomeScreen
    private lateinit var panelHub: PanelHub
    private lateinit var clockWeatherWidget: ClockWeatherCardDesign
    private lateinit var drawerLayout: LinearLayout
    private lateinit var drawerScrim: View
    private var isAmbientActive = false
    private val sleepHandler = Handler(Looper.getMainLooper())
    // Enters full-screen ambient screensaver mode automatically when the user's sleep timer expires.
    private val sleepTriggerRunnable = Runnable {
        settings.sleepTimerMins = 0
        toggleAmbientScreensaver(true)
    }
    // Schedules or cancels the sleep timer countdown whenever sleepTimerMins changes in settings.
    private val sleepTimerListener: () -> Unit = {
        sleepHandler.removeCallbacks(sleepTriggerRunnable)
        val mins = settings.sleepTimerMins
        if (mins > 0) {
            sleepHandler.postDelayed(sleepTriggerRunnable, mins * 60_000L)
        }
    }
    // Refreshes the app catalog whenever apps are installed, updated, or removed on the TV.
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            AppsShelfDesign.clearCache()
            syncAppShelf(allowFocusRestore = false)
        }
    }
    // Enters full-screen ambient clock mode when requested by system actions or panels.
    private val ambientReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            toggleAmbientScreensaver(true)
        }
    }
    // Receives D-Pad simulation commands from the external TV Remote Simulator overlay app.
    private val remoteSimReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val actionName = intent?.getStringExtra("remote_action") ?: return
            val keyCode = when (actionName) {
                "UP" -> KeyEvent.KEYCODE_DPAD_UP
                "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                "OK", "LONG_OK" -> KeyEvent.KEYCODE_DPAD_CENTER
                "BACK" -> KeyEvent.KEYCODE_BACK
                else -> return
            }
            val now = android.os.SystemClock.uptimeMillis()
            if (actionName == "LONG_OK") {
                val target = window?.currentFocus
                if (target != null && target.performLongClick()) return
                dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 1, 0, -1, 0, KeyEvent.FLAG_LONG_PRESS))
                dispatchKeyEvent(KeyEvent(now, now + 20L, KeyEvent.ACTION_UP, keyCode, 0))
            } else {
                dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
                dispatchKeyEvent(KeyEvent(now, now + 20L, KeyEvent.ACTION_UP, keyCode, 0))
            }
        }
    }
    // Scales base font/layout metrics adaptively according to display density and screen size.
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = LayoutMetrics.calculateAdaptiveScale(newBase)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
    // Initializes settings, immersive TV display, screen hierarchy, picture tint, sleep timer, and receivers.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        settings.addStyleListener(sleepTimerListener)
        sleepTimerListener()
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        buildLayout()
        applyInitialPictureMode()
        SleepReminderReceiver.scheduleNextNudge(this)
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pkgReceiver, pkgFilter, RECEIVER_EXPORTED)
            registerReceiver(ambientReceiver, IntentFilter(ACTION_ENTER_AMBIENT), RECEIVER_NOT_EXPORTED)
            registerReceiver(remoteSimReceiver, IntentFilter("shree.remote.sim.ACTION_REMOTE_KEY"), RECEIVER_EXPORTED)
        } else {
            registerReceiver(pkgReceiver, pkgFilter)
            registerReceiver(ambientReceiver, IntentFilter(ACTION_ENTER_AMBIENT))
            registerReceiver(remoteSimReceiver, IntentFilter("shree.remote.sim.ACTION_REMOTE_KEY"))
        }
    }
    // Unregisters receivers, clears sleep timer callbacks, and releases cached assets to keep RAM clean.
    override fun onDestroy() {
        super.onDestroy()
        sleepHandler.removeCallbacksAndMessages(null)
        if (::settings.isInitialized) settings.removeStyleListener(sleepTimerListener)
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(ambientReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(remoteSimReceiver) } catch (_: Exception) {}
        AppsShelfDesign.clearCache()
    }
    // Syncs app shelf and refreshes weather data when returning to the home screen.
    override fun onResume() {
        super.onResume()
        if (::homeScreen.isInitialized) syncAppShelf(allowFocusRestore = false)
        if (::clockWeatherWidget.isInitialized) clockWeatherWidget.triggerWeatherRefresh(false)
    }
    // Handles back navigation across open panels, hidden apps ribbon, and default home behavior.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            ::panelHub.isInitialized && panelHub.isOpen -> panelHub.close()
            ::homeScreen.isInitialized && homeScreen.isHiddenRibbonVisible() -> homeScreen.closeHiddenRibbon()
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }
    // Routes D-Pad keys cleanly when the side panel hub is open or exits ambient mode on key press.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isAmbientActive && event.action == KeyEvent.ACTION_DOWN) {
            toggleAmbientScreensaver(false)
            return true
        }
        if (::panelHub.isInitialized && panelHub.isOpen &&
            FocusRouter.handleKeyEvent(
                event = event,
                isDrawerOpen = true,
                drawerLayout = drawerLayout,
                topNavBar = homeScreen.topBar,
                activeParentTag = panelHub.getParentNavTag(),
                activePanel = panelHub.getActivePanelView()
            )
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    // Processes selected gallery URIs for custom wallpapers or profile avatars with persistent read access.
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        when (req) {
            REQ_PICK_WALLPAPER -> {
                settings.prefs.edit()
                    .putString(AppSettings.PREF_WALLPAPER, uri.toString())
                    .putString(AppSettings.PREF_WP_SCALE_MODE, "fit")
                    .putFloat(AppSettings.PREF_WP_ZOOM, 1.08f)
                    .putInt(AppSettings.PREF_WP_OFFSET_X, 0)
                    .putInt(AppSettings.PREF_WP_OFFSET_Y, 0)
                    .apply()
                applyWallpaperOnly()
                panelHub.open(PanelHub.MODE_WALLPAPER_EDIT)
            }
            REQ_PICK_PROFILE_PIC -> {
                settings.profilePicUri = uri.toString()
                homeScreen.topBar.refreshProfileAvatar()
                Toast.makeText(this, "Profile avatar updated", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Reopens the active feature panel after runtime permissions (such as Wi-Fi location or Bluetooth) are granted.
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            WifiPanel.REQ_WIFI_LOCATION -> {
                if (granted && ::panelHub.isInitialized && panelHub.isOpen) panelHub.open(PanelHub.MODE_WIFI)
                else Toast.makeText(this, "Location permission needed for Wi-Fi scan", Toast.LENGTH_SHORT).show()
            }
            PictureAudioPanel.REQ_BLUETOOTH_CONNECT -> {
                if (granted && ::panelHub.isInitialized && panelHub.isOpen) panelHub.open(PanelHub.MODE_SUB_BLUETOOTH)
                else Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Applies the saved picture preset or custom eye-care warmth tint when the launcher starts.
    private fun applyInitialPictureMode() {
        if (settings.pictureMode == "Custom") {
            PictureAudioPanel.applyWarmTintOverlay(this, settings.customWarmthPct)
        } else {
            PictureAudioPanel.PICTURE_MODES.find { it.key == settings.pictureMode }?.let {
                PictureAudioPanel.applyWarmTintOverlay(this, it.warmth)
            }
        }
    }
    // Transitions smoothly between normal launcher view and full-screen ambient clock screensaver view.
    private fun toggleAmbientScreensaver(enable: Boolean) {
        if (isAmbientActive == enable) return
        isAmbientActive = enable
        if (enable) {
            if (::panelHub.isInitialized && panelHub.isOpen) panelHub.close()
            homeScreen.topBar.animate().alpha(0f).setDuration(320L).start()
            homeScreen.bodyContainer.animate().alpha(0f).setDuration(320L).withEndAction {
                homeScreen.bodyContainer.visibility = View.INVISIBLE
            }.start()
            clockWeatherWidget.animate()
                .scaleX(1.20f).scaleY(1.20f)
                .translationX(-LayoutMetrics.dp(this, 72).toFloat())
                .setDuration(420L).start()
        } else {
            homeScreen.bodyContainer.visibility = View.VISIBLE
            homeScreen.topBar.animate().alpha(1f).setDuration(280L).start()
            homeScreen.bodyContainer.animate().alpha(1f).setDuration(280L).start()
            clockWeatherWidget.animate()
                .scaleX(1f).scaleY(1f).translationX(0f)
                .setDuration(320L).start()
        }
    }
    // Launches the system document picker to choose wallpaper or profile images.
    private fun openImagePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, requestCode)
        } catch (_: Exception) {
            Toast.makeText(this, "No image picker found", Toast.LENGTH_SHORT).show()
        }
    }
    // Computes right-side stage/drawer width dynamically from screen width and user width percentage.
    private fun computeRightStageWidth(): Int {
        val minStageW = LayoutMetrics.dp(this, Themes.PANEL_MIN_WIDTH_DP)
        return maxOf(minStageW, (resources.displayMetrics.widthPixels * (settings.panelWidthPct / 100f)).toInt())
    }
    // Assembles the root backdrop, home screen, clock/weather stage, drawer, and PanelHub controller.
    private fun buildLayout() {
        rootLayout = FrameLayout(this)
        LiveBackdrop.apply(this, rootLayout, settings)
        val navCallbacks = object : TopHeaderBar.NavCallbacks {
            override fun onProfileClick() = panelHub.showProfileEditDialog()
            override fun onVoiceSearch() {
                val actions = listOf(Intent.ACTION_ASSIST, RecognizerIntent.ACTION_WEB_SEARCH, Intent.ACTION_VOICE_COMMAND)
                for (act in actions) {
                    try {
                        startActivity(Intent(act))
                        return
                    } catch (_: Exception) {}
                }
                Toast.makeText(this@MainActivity, "Voice Assistant not installed", Toast.LENGTH_SHORT).show()
            }
            override fun onWifiClick() = panelHub.toggle(PanelHub.MODE_WIFI)
            override fun onAlertsClick() = panelHub.toggle(PanelHub.MODE_ALERTS)
            override fun onCustomizeClick() = panelHub.toggle(PanelHub.MODE_CUSTOMIZE)
            override fun onQuickSettingsClick() = panelHub.toggle(PanelHub.MODE_QUICK)
            override fun onPowerClick() = panelHub.toggle(PanelHub.MODE_POWER)
        }
        homeScreen = HomeScreen(
            context = this,
            settings = settings,
            callbacks = navCallbacks,
            onSortChanged = { syncAppShelf(allowFocusRestore = false) },
            onHiddenRibbonClosed = { focusPkg -> syncAppShelf(allowFocusRestore = false, restorePkg = focusPkg) }
        )
        rootLayout.addView(homeScreen, FrameLayout.LayoutParams(-1, -1))
        val stageW = computeRightStageWidth()
        homeScreen.setRightStageInset(stageW)
        clockWeatherWidget = ClockWeatherCardDesign(this, settings)
        homeScreen.contentArea.addView(
            clockWeatherWidget,
            FrameLayout.LayoutParams(stageW, -2, Gravity.END or Gravity.CENTER_VERTICAL)
        )
        drawerScrim = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            isClickable = true
            setOnClickListener { panelHub.close() }
        }
        homeScreen.contentArea.addView(drawerScrim, FrameLayout.LayoutParams(-1, -1))
        drawerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            translationX = (stageW + LayoutMetrics.dp(this@MainActivity, 30)).toFloat()
        }
        homeScreen.contentArea.addView(
            drawerLayout,
            FrameLayout.LayoutParams(stageW, -1, Gravity.END).apply {
                topMargin = LayoutMetrics.dp(this@MainActivity, 6)
                bottomMargin = LayoutMetrics.dp(this@MainActivity, 10)
            }
        )
        panelHub = PanelHub(
            context = this,
            drawerLayout = drawerLayout,
            scrim = drawerScrim,
            mainPage = homeScreen,
            settings = settings,
            onLiveRefresh = ::applyLiveVisuals,
            onWallpaperRefresh = ::applyWallpaperOnly,
                        onPickWallpaper = { openImagePicker(REQ_PICK_WALLPAPER) },
            onPickProfilePic = { openImagePicker(REQ_PICK_PROFILE_PIC) },
            onOpenHiddenRibbon = ::openHiddenRibbon
        )
        panelHub.attachStageWidget(clockWeatherWidget)
        val nudgeView = homeScreen.topBar.createFloatingNudgeView()
        val nudgeParams = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            topMargin = LayoutMetrics.dp(this@MainActivity, 52)
        }
        rootLayout.addView(nudgeView, nudgeParams)
        homeScreen.topBar.post {
            val profileGroup = homeScreen.topBar.getChildAt(1)
            nudgeParams.leftMargin = (profileGroup?.right ?: LayoutMetrics.dp(this@MainActivity, 180)) + LayoutMetrics.dp(this@MainActivity, 12)
            nudgeView.requestLayout()
        }
        setContentView(rootLayout)
        syncAppShelf(allowFocusRestore = true)
    }
    // Populates hidden apps and handles restoring apps back to the visible home shelf.
    private fun openHiddenRibbon() {
        homeScreen.openHiddenRibbon { pkg, label ->
            val updated = settings.getHiddenApps().apply { remove(pkg) }
            settings.setHiddenApps(updated)
            Toast.makeText(this, "$label restored to Home", Toast.LENGTH_SHORT).show()
            syncAppShelf(allowFocusRestore = false, restorePkg = pkg)
            if (updated.isEmpty()) homeScreen.closeHiddenRibbon(pkg) else openHiddenRibbon()
        }
    }
    // Updates profile, greeting, backdrop, stage width, and app shelf dynamically without recreating views.
    private fun applyLiveVisuals() {
        homeScreen.topBar.refreshProfileAvatar()
        homeScreen.topBar.refreshGreeting()
        LiveBackdrop.updateThemeAndAccent(rootLayout, settings)
        val newW = computeRightStageWidth()
        homeScreen.setRightStageInset(newW)
        clockWeatherWidget.layoutParams.width = newW
        clockWeatherWidget.refreshStyle()
        clockWeatherWidget.triggerWeatherRefresh(true)
        val currentW = drawerLayout.layoutParams.width
        if (currentW != newW && currentW > 0 && panelHub.isOpen) {
            ValueAnimator.ofInt(currentW, newW).apply {
                duration = 380L
                interpolator = AnimationController.getInterpolator(settings.motionStyle)
                addUpdateListener { anim ->
                    drawerLayout.layoutParams.width = anim.animatedValue as Int
                    drawerLayout.requestLayout()
                }
                start()
            }
        } else if (currentW != newW) {
            drawerLayout.layoutParams.width = newW
            drawerLayout.requestLayout()
        }
        syncAppShelf(allowFocusRestore = false)
    }
    // Replaces only the backdrop layer without rebuilding the foreground UI hierarchy.
    private fun applyWallpaperOnly() {
        while (rootLayout.childCount > 0 && rootLayout.getChildAt(0) !== homeScreen) {
            rootLayout.removeViewAt(0)
        }
        LiveBackdrop.apply(this, rootLayout, settings)
    }
    // Populates favorites and all-apps grids and handles favorite/hide actions cleanly.
    private fun syncAppShelf(allowFocusRestore: Boolean, restorePkg: String? = null) {
        val isDrawerOpen = if (::panelHub.isInitialized) panelHub.isOpen else false
        AppsShelfDesign.populate(
            ctx = this,
            settings = settings,
            favSection = homeScreen.favSection,
            favRow = homeScreen.favRow,
            favCount = homeScreen.favCount,
            appGrid = homeScreen.appGrid,
            allCount = homeScreen.allCount,
            shouldRequestFocus = allowFocusRestore,
            restorePkg = restorePkg,
            isDrawerOpen = isDrawerOpen,
            actions = object : AppTileDesign.TileActions {
                override fun onToggleFavorite(pkg: String, label: String) {
                    val list = settings.getOrderedFavorites()
                    val added = !list.contains(pkg)
                    if (added) list.add(pkg) else list.remove(pkg)
                    settings.setFavorites(list)
                    Toast.makeText(this@MainActivity, if (added) "$label added to Favorites" else "$label removed", Toast.LENGTH_SHORT).show()
                    syncAppShelf(allowFocusRestore = false, restorePkg = pkg)
                }
                override fun onMoveFavorite(pkg: String, delta: Int) {
                    if (settings.moveFavorite(pkg, delta)) syncAppShelf(allowFocusRestore = false, restorePkg = pkg)
                }
                override fun onHideApp(pkg: String, label: String) {
                    val hidden = settings.getHiddenApps().apply { add(pkg) }
                    settings.setHiddenApps(hidden)
                    Toast.makeText(this@MainActivity, "$label hidden", Toast.LENGTH_SHORT).show()
                    syncAppShelf(allowFocusRestore = false)
                }
            }
        )
    }
}
