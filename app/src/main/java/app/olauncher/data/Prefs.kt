package app.olauncher.data

import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class Prefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app.olauncher", Context.MODE_PRIVATE)

    var firstOpen by boolean("FIRST_OPEN", true)
    var firstOpenTime by long("FIRST_OPEN_TIME", 0L)
    var firstSettingsOpen by boolean("FIRST_SETTINGS_OPEN", true)
    var firstHide by boolean("FIRST_HIDE", true)
    var userState by string("USER_STATE", Constants.UserState.START)
    var lockModeOn by boolean("LOCK_MODE", false)
    var autoShowKeyboard by boolean("AUTO_SHOW_KEYBOARD", true)
    var keyboardMessageShown by boolean("KEYBOARD_MESSAGE", false)
    var dailyWallpaper by boolean("DAILY_WALLPAPER", false)
    var dailyWallpaperUrl by string("DAILY_WALLPAPER_URL", "")
    var homeAppsNum by int("HOME_APPS_NUM", 4)
    var homeAlignment by int("HOME_ALIGNMENT", Gravity.START)
    var homeBottomAlignment by boolean("HOME_BOTTOM_ALIGNMENT", false)
    var appLabelAlignment by int("APP_LABEL_ALIGNMENT", Gravity.START)
    var showStatusBar by boolean("STATUS_BAR", false)
    var dateTimeVisibility by int("DATE_TIME_VISIBILITY", Constants.DateTime.ON)
    var swipeLeftEnabled by boolean("SWIPE_LEFT_ENABLED", true)
    var swipeRightEnabled by boolean("SWIPE_RIGHT_ENABLED", true)
    var appTheme by int("APP_THEME", AppCompatDelegate.MODE_NIGHT_YES)
    var textSizeScale by float("TEXT_SIZE_SCALE", 1.0f)
    var proMessageShown by boolean("PRO_MESSAGE_SHOWN", false)
    var hideSetDefaultLauncher by boolean("HIDE_SET_DEFAULT_LAUNCHER", false)
    var screenTimeLastUpdated by long("SCREEN_TIME_LAST_UPDATED", 0L)
    var launcherRestartTimestamp by long("LAUNCHER_RECREATE_TIMESTAMP", 0L)
    var shownOnDayOfYear by int("SHOWN_ON_DAY_OF_YEAR", 0)
    var hiddenAppsUpdated by boolean("HIDDEN_APPS_UPDATED", false)
    var toShowHintCounter by int("SHOW_HINT_COUNTER", 1)
    var aboutClicked by boolean("ABOUT_CLICKED", false)
    var rateClicked by boolean("RATE_CLICKED", false)
    var wallpaperMsgShown by boolean("WALLPAPER_MSG_SHOWN", false)
    var shareShownTime by long("SHARE_SHOWN_TIME", 0L)
    var swipeDownAction by int("SWIPE_DOWN_ACTION", Constants.SwipeDownAction.NOTIFICATIONS)

    var appNameSwipeLeft by string("APP_NAME_SWIPE_LEFT", "Camera")
    var appPackageSwipeLeft by string("APP_PACKAGE_SWIPE_LEFT", "")
    var appActivityClassNameSwipeLeft by string("APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT", "")
    var appUserSwipeLeft by string("APP_USER_SWIPE_LEFT", "")

    var appNameSwipeRight by string("APP_NAME_SWIPE_RIGHT", "Phone")
    var appPackageSwipeRight by string("APP_PACKAGE_SWIPE_RIGHT", "")
    var appActivityClassNameRight by string("APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT", "")
    var appUserSwipeRight by string("APP_USER_SWIPE_RIGHT", "")

    var clockAppPackage by string("CLOCK_APP_PACKAGE", "")
    var clockAppUser by string("CLOCK_APP_USER", "")
    var clockAppClassName by string("CLOCK_APP_CLASS_NAME", "")

    var calendarAppPackage by string("CALENDAR_APP_PACKAGE", "")
    var calendarAppUser by string("CALENDAR_APP_USER", "")
    var calendarAppClassName by string("CALENDAR_APP_CLASS_NAME", "")

    var hiddenApps: Set<String>
        get() = prefs.getStringSet("HIDDEN_APPS", null) ?: emptySet()
        set(value) = prefs.edit { putStringSet("HIDDEN_APPS", value) }

    fun setHomeApp(index: Int, app: AppModel) {
        prefs.edit {
            putString("APP_NAME_$index", app.appLabel)
            putString("APP_PACKAGE_$index", app.appPackage)
            putString("APP_USER_$index", app.user.toString())
            putString("APP_ACTIVITY_CLASS_NAME_$index", app.activityClassName)
        }
    }

    fun setHomeAppName(index: Int, name: String) {
        prefs.edit { putString("APP_NAME_$index", name) }
    }

    fun getAppName(index: Int): String = prefs.getString("APP_NAME_$index", "") ?: ""
    fun getAppPackage(index: Int): String = prefs.getString("APP_PACKAGE_$index", "") ?: ""
    fun getAppUser(index: Int): String = prefs.getString("APP_USER_$index", "") ?: ""
    fun getAppActivityClassName(index: Int): String = prefs.getString("APP_ACTIVITY_CLASS_NAME_$index", "") ?: ""

    fun getAppRenameLabel(appPackage: String): String =
        prefs.getString("RENAME_$appPackage", null) ?: prefs.getString(appPackage, "") ?: ""

    fun setAppRenameLabel(appPackage: String, renameLabel: String) =
        prefs.edit { putString("RENAME_$appPackage", renameLabel) }

    @Suppress("SameParameterValue")
    private fun string(key: String, defaultValue: String) = delegate(key, defaultValue, SharedPreferences::getString, SharedPreferences.Editor::putString)
    @Suppress("SameParameterValue")
    private fun int(key: String, defaultValue: Int) = delegate(key, defaultValue, SharedPreferences::getInt, SharedPreferences.Editor::putInt)
    @Suppress("SameParameterValue")
    private fun boolean(key: String, defaultValue: Boolean) = delegate(key, defaultValue, SharedPreferences::getBoolean, SharedPreferences.Editor::putBoolean)
    @Suppress("SameParameterValue")
    private fun long(key: String, defaultValue: Long) = delegate(key, defaultValue, SharedPreferences::getLong, SharedPreferences.Editor::putLong)
    @Suppress("SameParameterValue")
    private fun float(key: String, defaultValue: Float) = delegate(key, defaultValue, SharedPreferences::getFloat, SharedPreferences.Editor::putFloat)

    private fun <T> delegate(
        key: String,
        defaultValue: T,
        getter: SharedPreferences.(String, T) -> T?,
        setter: SharedPreferences.Editor.(String, T) -> SharedPreferences.Editor
    ) = object : ReadWriteProperty<Any, T> {
        override fun getValue(thisRef: Any, property: KProperty<*>): T =
            prefs.getter(key, defaultValue) ?: defaultValue

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            prefs.edit { setter(key, value) }
        }
    }
}
