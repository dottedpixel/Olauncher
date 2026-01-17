package app.olauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.ActivityMainBinding
import app.olauncher.helper.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var timerJob: Job? = null

    override fun attachBaseContext(context: Context) {
        val newConfig = Configuration(context.resources.configuration)
        newConfig.fontScale = Prefs(context).textSizeScale
        applyOverrideConfiguration(newConfig)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        if (isEinkDisplay()) prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = findNavController(R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupOnBackPressed()
        handleFirstOpen()
        
        initClickListeners()
        initObservers()
        
        viewModel.getAppList()
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)
    }

    private fun setupOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navController.currentDestination?.id != R.id.mainFragment) {
                    navController.popBackStack()
                } else if (binding.messageLayout.visibility == View.VISIBLE) {
                    binding.messageLayout.visibility = View.GONE
                }
            }
        })
    }

    private fun handleFirstOpen() {
        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
            prefs.firstOpenTime = System.currentTimeMillis()
            viewModel.setDefaultClockApp()
            viewModel.resetLauncherLiveData.call()
        }
    }

    override fun onStart() {
        super.onStart()
        restartLauncherOrCheckTheme()
        viewModel.checkForMessages()
    }

    override fun onStop() {
        backToHomeScreen()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        backToHomeScreen()
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        backToHomeScreen()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        if (prefs.dailyWallpaper && AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            setPlainWallpaper()
            viewModel.setWallpaperWorker()
            recreate()
        }
    }

    private fun initClickListeners() {
        binding.ivClose.setOnClickListener {
            binding.messageLayout.visibility = View.GONE
        }
    }

    private fun initObservers() {
        viewModel.launcherResetFailed.observe(this) { resetFailed ->
            if (resetFailed) {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
        }
        
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                resetLauncherViaFakeActivity()
            else
                showLauncherSelector(Constants.REQUEST_CODE_LAUNCHER_SELECTOR)
        }

        viewModel.showDialog.observe(this) { dialogType ->
            handleDialog(dialogType)
        }
    }

    private fun handleDialog(type: String) {
        when (type) {
            Constants.Dialog.ABOUT -> {
                showMessageDialog(R.string.app_name, R.string.welcome_to_olauncher_settings, R.string.okay)
            }
            Constants.Dialog.WALLPAPER -> {
                prefs.wallpaperMsgShown = true
                prefs.userState = Constants.UserState.REVIEW
                showMessageDialog(R.string.did_you_know, R.string.wallpaper_message, R.string.enable) {
                    prefs.dailyWallpaper = true
                    viewModel.setWallpaperWorker()
                    showToast(getString(R.string.your_wallpaper_will_update_shortly))
                }
            }
            Constants.Dialog.REVIEW -> {
                prefs.userState = Constants.UserState.RATE
                showMessageDialog(R.string.hey, R.string.review_message, R.string.leave_a_review) {
                    prefs.rateClicked = true
                    showToast("😇❤️")
                    rateApp()
                }
            }
            Constants.Dialog.RATE -> {
                prefs.userState = Constants.UserState.SHARE
                showMessageDialog(R.string.app_name, R.string.rate_us_message, R.string.rate_now) {
                    prefs.rateClicked = true
                    showToast("🤩❤️")
                    rateApp()
                }
            }
            Constants.Dialog.SHARE -> {
                prefs.shareShownTime = System.currentTimeMillis()
                showMessageDialog(R.string.hey, R.string.share_message, R.string.share_now) {
                    showToast("😊❤️")
                    shareApp()
                }
            }
            Constants.Dialog.HIDDEN -> showMessageDialog(R.string.hidden_apps, R.string.hidden_apps_message, R.string.okay)
            Constants.Dialog.KEYBOARD -> showMessageDialog(R.string.app_name, R.string.keyboard_message, R.string.okay)
            Constants.Dialog.DIGITAL_WELLBEING -> {
                showMessageDialog(R.string.screen_time, R.string.app_usage_message, R.string.permission) {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            Constants.Dialog.PRO_MESSAGE -> {
                showMessageDialog(R.string.hey, R.string.pro_message, R.string.olauncher_pro) {
                    openUrl(Constants.URL_OLAUNCHER_PRO)
                }
            }
            "NEW_YEAR" -> showMessageDialog(R.string.hey, R.string.new_year_wish, R.string.cheers)
            "NEW_YEAR_1" -> showMessageDialog(R.string.hey, R.string.new_year_wish_1, R.string.cheers)
        }
    }

    private fun showMessageDialog(title: Int, message: Int, action: Int, clickListener: (() -> Unit)? = null) {
        binding.tvTitle.text = getString(title)
        binding.tvMessage.text = getString(message)
        binding.tvAction.text = getString(action)
        binding.tvAction.setOnClickListener {
            clickListener?.invoke()
            binding.messageLayout.visibility = View.GONE
        }
        binding.messageLayout.visibility = View.VISIBLE
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (isTablet(this) || Build.VERSION.SDK_INT == Build.VERSION_CODES.O)
            return
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun backToHomeScreen() {
        binding.messageLayout.visibility = View.GONE
        if (navController.currentDestination?.id != R.id.mainFragment)
            navController.popBackStack(R.id.mainFragment, false)
    }

    private fun setPlainWallpaper() {
        val color = if (isDarkThemeOn()) android.R.color.black else android.R.color.white
        setPlainWallpaper(this, color)
    }

    private fun restartLauncherOrCheckTheme(forceRestart: Boolean = false) {
        if (forceRestart || prefs.launcherRestartTimestamp.hasBeenHours(4)) {
            prefs.launcherRestartTimestamp = System.currentTimeMillis()
            cacheDir.deleteRecursively()
            recreate()
        } else {
            checkTheme()
        }
    }

    private fun checkTheme() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            delay(200)
            val primaryColor = getColorFromAttr(R.attr.primaryColor)
            val shouldRestart = when (prefs.appTheme) {
                AppCompatDelegate.MODE_NIGHT_YES -> primaryColor != getColor(R.color.white)
                AppCompatDelegate.MODE_NIGHT_NO -> primaryColor != getColor(R.color.black)
                else -> false
            }
            if (shouldRestart) restartLauncherOrCheckTheme(true)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Constants.REQUEST_CODE_ENABLE_ADMIN -> {
                if (resultCode == Activity.RESULT_OK) prefs.lockModeOn = true
            }
            Constants.REQUEST_CODE_LAUNCHER_SELECTOR -> {
                if (resultCode == Activity.RESULT_OK) resetLauncherViaFakeActivity()
            }
        }
    }
}
