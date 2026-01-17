package app.olauncher

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.*
import app.olauncher.helper.usageStats.EventLogWrapper
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs(appContext)

    private val _firstOpen = MutableLiveData<Boolean>()
    val firstOpen: LiveData<Boolean> = _firstOpen

    private val _refreshHome = MutableLiveData<Boolean>()
    val refreshHome: LiveData<Boolean> = _refreshHome

    val toggleDateTime = SingleLiveEvent<Unit>()
    val updateSwipeApps = SingleLiveEvent<Unit>()

    private val _appList = MutableLiveData<List<AppModel>?>()
    val appList: LiveData<List<AppModel>?> = _appList

    private val _hiddenApps = MutableLiveData<List<AppModel>?>()
    val hiddenApps: LiveData<List<AppModel>?> = _hiddenApps

    private val _isOlauncherDefault = MutableLiveData<Boolean>()
    val isOlauncherDefault: LiveData<Boolean> = _isOlauncherDefault

    private val _launcherResetFailed = MutableLiveData<Boolean>()
    val launcherResetFailed: LiveData<Boolean> = _launcherResetFailed

    private val _homeAppAlignment = MutableLiveData<Int>()
    val homeAppAlignment: LiveData<Int> = _homeAppAlignment

    private val _screenTimeValue = MutableLiveData<String>()
    val screenTimeValue: LiveData<String> = _screenTimeValue

    val showDialog = SingleLiveEvent<String>()
    val resetLauncherLiveData = SingleLiveEvent<Unit?>()

    fun selectedApp(appModel: AppModel, flag: Int) {
        when (flag) {
            Constants.FLAG_LAUNCH_APP, Constants.FLAG_HIDDEN_APPS -> {
                launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)
            }

            in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8 -> {
                prefs.setHomeApp(flag, appModel)
                refreshHome(false)
            }

            Constants.FLAG_SET_SWIPE_LEFT_APP -> {
                prefs.appNameSwipeLeft = appModel.appLabel
                prefs.appPackageSwipeLeft = appModel.appPackage
                prefs.appUserSwipeLeft = appModel.user.toString()
                prefs.appActivityClassNameSwipeLeft = appModel.activityClassName ?: ""
                updateSwipeApps.call()
            }

            Constants.FLAG_SET_SWIPE_RIGHT_APP -> {
                prefs.appNameSwipeRight = appModel.appLabel
                prefs.appPackageSwipeRight = appModel.appPackage
                prefs.appUserSwipeRight = appModel.user.toString()
                prefs.appActivityClassNameRight = appModel.activityClassName ?: ""
                updateSwipeApps.call()
            }

            Constants.FLAG_SET_CLOCK_APP -> {
                prefs.clockAppPackage = appModel.appPackage
                prefs.clockAppUser = appModel.user.toString()
                prefs.clockAppClassName = appModel.activityClassName ?: ""
            }

            Constants.FLAG_SET_CALENDAR_APP -> {
                prefs.calendarAppPackage = appModel.appPackage
                prefs.calendarAppUser = appModel.user.toString()
                prefs.calendarAppClassName = appModel.activityClassName ?: ""
            }
        }
    }

    fun firstOpen(value: Boolean) {
        _firstOpen.postValue(value)
    }

    fun refreshHome(appCountUpdated: Boolean) {
        _refreshHome.value = appCountUpdated
    }

    fun toggleDateTime() {
        toggleDateTime.call()
    }

    private fun launchApp(packageName: String, activityClassName: String?, userHandle: UserHandle) {
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityInfo = launcher.getActivityList(packageName, userHandle)

        val component = if (activityClassName.isNullOrBlank()) {
            when (activityInfo.size) {
                0 -> {
                    appContext.showToast(appContext.getString(R.string.app_not_found))
                    return
                }
                1 -> ComponentName(packageName, activityInfo[0].name)
                else -> ComponentName(packageName, activityInfo.last().name)
            }
        } else {
            ComponentName(packageName, activityClassName)
        }

        try {
            launcher.startMainActivity(component, userHandle, null, null)
        } catch (_: Exception) {
            try {
                launcher.startMainActivity(component, android.os.Process.myUserHandle(), null, null)
            } catch (_: Exception) {
                appContext.showToast(appContext.getString(R.string.unable_to_open_app))
            }
        }
    }

    fun getAppList(includeHiddenApps: Boolean = false) {
        viewModelScope.launch {
            _appList.value = getAppsList(appContext, prefs, includeRegularApps = true, includeHiddenApps)
        }
    }

    fun getHiddenApps() {
        viewModelScope.launch {
            _hiddenApps.value = getAppsList(appContext, prefs, includeRegularApps = false, includeHiddenApps = true)
        }
    }

    fun isOlauncherDefault() {
        _isOlauncherDefault.value = appContext.isDefaultLauncher()
    }

    fun setWallpaperWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val uploadWorkRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(8, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager
            .getInstance(appContext)
            .enqueueUniquePeriodicWork(
                Constants.WALLPAPER_WORKER_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                uploadWorkRequest
            )
    }

    fun cancelWallpaperWorker() {
        WorkManager.getInstance(appContext).cancelUniqueWork(Constants.WALLPAPER_WORKER_NAME)
        prefs.dailyWallpaperUrl = ""
        prefs.dailyWallpaper = false
    }

    fun updateHomeAlignment(gravity: Int) {
        prefs.homeAlignment = gravity
        _homeAppAlignment.value = prefs.homeAlignment
    }

    fun getTodaysScreenTime() {
        if (prefs.screenTimeLastUpdated.hasBeenMinutes(1).not()) return

        val eventLogWrapper = EventLogWrapper(appContext)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val timeSpent = eventLogWrapper.aggregateSimpleUsageStats(
            eventLogWrapper.aggregateForegroundStats(
                eventLogWrapper.getForegroundStatsByTimestamps(startTime, endTime)
            )
        )
        val viewTimeSpent = appContext.formattedTimeSpent(timeSpent)
        _screenTimeValue.postValue(viewTimeSpent)
        prefs.screenTimeLastUpdated = endTime
    }

    fun setDefaultClockApp() {
        viewModelScope.launch {
            try {
                Constants.CLOCK_APP_PACKAGES.firstOrNull { appContext.isPackageInstalled(it) }?.let { packageName ->
                    appContext.packageManager.getLaunchIntentForPackage(packageName)?.component?.className?.let {
                        prefs.clockAppPackage = packageName
                        prefs.clockAppClassName = it
                        prefs.clockAppUser = android.os.Process.myUserHandle().toString()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun checkForMessages() {
        if (prefs.firstOpenTime == 0L)
            prefs.firstOpenTime = System.currentTimeMillis()

        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        
        // Special New Year logic
        if ((dayOfYear == 1 || dayOfYear == 32) && dayOfYear != prefs.shownOnDayOfYear) {
            prefs.shownOnDayOfYear = dayOfYear
            showDialog.postValue(if (dayOfYear == 1) Constants.Dialog.NEW_YEAR else Constants.Dialog.NEW_YEAR_1)
            return
        }

        when (prefs.userState) {
            Constants.UserState.START -> {
                if (prefs.firstOpenTime.hasBeenMinutes(10)) {
                    prefs.userState = Constants.UserState.WALLPAPER
                    checkForMessages()
                }
            }

            Constants.UserState.WALLPAPER -> {
                if (prefs.wallpaperMsgShown || prefs.dailyWallpaper) {
                    prefs.userState = Constants.UserState.REVIEW
                    checkForMessages()
                } else if (appContext.isDefaultLauncher()) {
                    showDialog.postValue(Constants.Dialog.WALLPAPER)
                }
            }

            Constants.UserState.REVIEW -> {
                if (prefs.rateClicked) {
                    prefs.userState = Constants.UserState.SHARE
                    checkForMessages()
                } else if (appContext.isDefaultLauncher() && prefs.firstOpenTime.hasBeenHours(1)) {
                    showDialog.postValue(Constants.Dialog.REVIEW)
                }
            }

            Constants.UserState.RATE -> {
                if (prefs.rateClicked) {
                    prefs.userState = Constants.UserState.SHARE
                    checkForMessages()
                } else if (appContext.isDefaultLauncher()
                    && prefs.firstOpenTime.isDaySince() >= 7
                    && calendar.get(Calendar.HOUR_OF_DAY) >= 16
                ) {
                    showDialog.postValue(Constants.Dialog.RATE)
                }
            }

            Constants.UserState.SHARE -> {
                if (appContext.isDefaultLauncher() && prefs.firstOpenTime.hasBeenDays(14)
                    && prefs.shareShownTime.isDaySince() >= 70
                    && calendar.get(Calendar.HOUR_OF_DAY) >= 16
                ) {
                    showDialog.postValue(Constants.Dialog.SHARE)
                }
            }
        }
    }
}
