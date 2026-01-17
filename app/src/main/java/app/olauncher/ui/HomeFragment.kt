package app.olauncher.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentHomeBinding
import app.olauncher.helper.*
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.listener.ViewSwipeTouchListener
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeAppViews by lazy {
        listOf(
            binding.homeApp1, binding.homeApp2, binding.homeApp3, binding.homeApp4,
            binding.homeApp5, binding.homeApp6, binding.homeApp7, binding.homeApp8
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListeners()
        initClickListeners()
    }

    override fun onResume() {
        super.onResume()
        populateHomeScreen(false)
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar() else hideStatusBar()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.clock -> openClockApp()
            R.id.date -> openCalendarApp()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> openScreenTimeDigitalWellbeing()
            else -> {
                val tag = view.tag?.toString()?.toIntOrNull()
                if (tag != null) homeAppClicked(tag)
            }
        }
    }

    private fun openClockApp() {
        if (prefs.clockAppPackage.isBlank()) {
            openAlarmApp(requireContext())
        } else {
            launchApp("Clock", prefs.clockAppPackage, prefs.clockAppClassName, prefs.clockAppUser)
        }
    }

    private fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank()) {
            openCalendar(requireContext())
        } else {
            launchApp("Calendar", prefs.calendarAppPackage, prefs.calendarAppClassName, prefs.calendarAppUser)
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            in listOf(R.id.homeApp1, R.id.homeApp2, R.id.homeApp3, R.id.homeApp4, R.id.homeApp5, R.id.homeApp6, R.id.homeApp7, R.id.homeApp8) -> {
                val index = homeAppViews.indexOf(view) + 1
                showAppList(index, prefs.getAppName(index).isNotEmpty(), true)
            }
            R.id.clock -> {
                showAppList(Constants.FLAG_SET_CLOCK_APP)
                prefs.clockAppPackage = ""
                prefs.clockAppClassName = ""
                prefs.clockAppUser = ""
            }
            R.id.date -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }
            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isOlauncherDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
        }
        return true
    }

    private fun initObservers() {
        binding.firstRunTips.isVisible = prefs.firstSettingsOpen
        if (prefs.firstSettingsOpen) binding.setDefaultLauncher.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) { populateHomeScreen(it) }
        
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner) { isDefault ->
            if (isDefault != true) {
                if (prefs.dailyWallpaper) {
                    prefs.dailyWallpaper = false
                    viewModel.cancelWallpaperWorker()
                }
                prefs.homeBottomAlignment = false
                setHomeAlignment()
            }
            if (!binding.firstRunTips.isVisible) {
                binding.setDefaultLauncher.isVisible = !isDefault && !prefs.hideSetDefaultLauncher
            }
        }
        
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) { setHomeAlignment(it) }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) { populateDateTime() }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) { it?.let { binding.tvScreenTime.text = it } }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSwipeTouchListeners() {
        val context = requireContext()
        binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
        homeAppViews.forEach { it.setOnTouchListener(getViewSwipeTouchListener(context, it)) }
    }

    private fun initClickListeners() {
        listOf(binding.lock, binding.clock, binding.date, binding.setDefaultLauncher, binding.tvScreenTime).forEach {
            it.setOnClickListener(this)
        }
        listOf(binding.clock, binding.date, binding.setDefaultLauncher).forEach {
            it.setOnLongClickListener(this)
        }
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        val verticalGravity = if (prefs.homeBottomAlignment) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        binding.homeAppsLayout.gravity = horizontalGravity or verticalGravity
        binding.dateTimeLayout.gravity = horizontalGravity
        homeAppViews.forEach { it.gravity = horizontalGravity }
    }

    private fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

        var dateText = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())

        if (!prefs.showStatusBar) {
            val battery = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery > 0) dateText = getString(R.string.day_battery, dateText, battery)
        }
        binding.date.text = dateText.replace(".,", ",")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun populateScreenTime() {
        if (!requireContext().appUsagePermissionGranted()) return

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime.isVisible = true

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val horizontalMargin = if (isLandscape) 64.dpToPx() else 10.dpToPx()
        val marginTop = if (isLandscape) {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 36.dpToPx() else 56.dpToPx()
        } else {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 45.dpToPx() else 72.dpToPx()
        }
        
        binding.tvScreenTime.layoutParams = (binding.tvScreenTime.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = marginTop
            marginStart = horizontalMargin
            marginEnd = horizontalMargin
            gravity = if (prefs.homeAlignment == Gravity.END) Gravity.START else Gravity.END
        }
        binding.tvScreenTime.setPadding(10.dpToPx())
    }

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        if (appCountUpdated) homeAppViews.forEach { it.isVisible = false }
        populateDateTime()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) populateScreenTime()

        val homeAppsNum = prefs.homeAppsNum
        homeAppViews.forEachIndexed { index, textView ->
            val position = index + 1
            if (position <= homeAppsNum) {
                textView.isVisible = true
                val appName = prefs.getAppName(position)
                val appPackage = prefs.getAppPackage(position)
                val appUser = prefs.getAppUser(position)
                
                val userHandle = getUserHandleFromString(requireContext(), appUser)
                if (requireContext().isPackageInstalled(appPackage, userHandle)) {
                    textView.text = appName
                } else {
                    textView.text = ""
                }
            } else {
                textView.isVisible = false
            }
        }
    }

    private fun homeAppClicked(location: Int) {
        val appName = prefs.getAppName(location)
        if (appName.isEmpty()) {
            requireContext().showToast(R.string.long_press_to_select_app)
        } else {
            launchApp(
                appName,
                prefs.getAppPackage(location),
                prefs.getAppActivityClassName(location),
                prefs.getAppUser(location)
            )
        }
    }

    private fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        viewModel.selectedApp(
            AppModel(
                appName,
                null,
                packageName,
                activityClassName,
                false,
                getUserHandleFromString(requireContext(), userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        viewModel.getAppList(includeHiddenApps)
        val args = bundleOf(Constants.Key.FLAG to flag, Constants.Key.RENAME to rename)
        try {
            findNavController().navigate(R.id.action_mainFragment_to_appListFragment, args)
        } catch (_: Exception) {
            findNavController().navigate(R.id.appListFragment, args)
        }
    }

    private fun swipeDownAction() {
        when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.SEARCH -> openSearch(requireContext())
            else -> expandNotificationDrawer(requireContext())
        }
    }

    private fun openSwipeRightApp() {
        if (!prefs.swipeRightEnabled) return
        if (prefs.appPackageSwipeRight.isNotEmpty()) {
            launchApp(prefs.appNameSwipeRight, prefs.appPackageSwipeRight, prefs.appActivityClassNameRight, prefs.appUserSwipeRight)
        } else {
            openDialerApp(requireContext())
        }
    }

    private fun openSwipeLeftApp() {
        if (!prefs.swipeLeftEnabled) return
        if (prefs.appPackageSwipeLeft.isNotEmpty()) {
            launchApp(prefs.appNameSwipeLeft, prefs.appPackageSwipeLeft, prefs.appActivityClassNameSwipeLeft, prefs.appUserSwipeLeft)
        } else {
            openCameraApp(requireContext())
        }
    }

    private fun lockPhone() {
        try {
            deviceManager.lockNow()
        } catch (_: SecurityException) {
            requireContext().showToast(R.string.please_turn_on_double_tap_to_unlock, Toast.LENGTH_LONG)
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
        } catch (_: Exception) {
            requireContext().showToast(R.string.launcher_failed_to_lock_device, Toast.LENGTH_LONG)
            prefs.lockModeOn = false
        }
    }

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    }

    private fun openScreenTimeDigitalWellbeing() {
        val intent = Intent(Intent.ACTION_MAIN)
        if (requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME)) {
            intent.setClassName(Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME, Constants.DIGITAL_WELLBEING_SAMSUNG_ACTIVITY)
        } else {
            intent.setClassName(Constants.DIGITAL_WELLBEING_PACKAGE_NAME, Constants.DIGITAL_WELLBEING_ACTIVITY)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
                requireContext().showToast(R.string.unable_to_open_app)
            }
        }
    }

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onSwipeLeft() = openSwipeLeftApp()
            override fun onSwipeRight() = openSwipeRightApp()
            override fun onSwipeUp() = showAppList(Constants.FLAG_LAUNCH_APP)
            override fun onSwipeDown() = swipeDownAction()

            override fun onLongClick() {
                try {
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                    viewModel.firstOpen(false)
                } catch (_: Exception) { }
            }

            override fun onDoubleClick() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) binding.lock.performClick()
                else if (prefs.lockModeOn) lockPhone()
            }

            override fun onTripleClick(e: MotionEvent) {
                val (width, height) = getScreenDimensions(requireContext())
                if (e.x > width * 0.8 && e.y > height * 0.8 && prefs.dailyWallpaper) {
                    requireContext().showToast("Loading new wallpaper...")
                    lifecycleScope.launch { setRandomWallpaper(requireContext()) }
                }
            }

            override fun onClick() = viewModel.checkForMessages()
        }
    }

    private fun getViewSwipeTouchListener(context: Context, view: View): View.OnTouchListener {
        return object : ViewSwipeTouchListener(context, view) {
            override fun onSwipeLeft() = openSwipeLeftApp()
            override fun onSwipeRight() = openSwipeRightApp()
            override fun onSwipeUp() = showAppList(Constants.FLAG_LAUNCH_APP)
            override fun onSwipeDown() = swipeDownAction()
            override fun onLongClick(view: View) { this@HomeFragment.onLongClick(view) }
            override fun onClick(view: View) = this@HomeFragment.onClick(view)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
