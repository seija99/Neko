package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy

object ThemeUtil {

    /** Migration method */
    fun convertTheme(preferences: PreferencesHelper, theme: Int) {
        preferences.nightMode().set(
            when (theme) {
                0, 1 -> AppCompatDelegate.MODE_NIGHT_NO
                2, 3, 4, 9 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        preferences.lightTheme().set(Themes.DEFAULT)
        preferences.darkTheme().set(Themes.DEFAULT)
    }

    /** Migration method */
    fun convertNewThemes(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val lightTheme = prefs.getString(PreferenceKeys.lightTheme, "DEFAULT")
        val darkTheme = prefs.getString(PreferenceKeys.darkTheme, "DEFAULT")

        prefs.edit {
            putString(
                PreferenceKeys.lightTheme,
                when (lightTheme) {
                    "SPRING" -> Themes.SPRING_AND_DUSK
                    "STRAWBERRY_DAIQUIRI" -> Themes.STRAWBERRIES
                    else -> Themes.DEFAULT
                }.name
            )
            putString(
                PreferenceKeys.darkTheme,
                when (darkTheme) {
                    "DUSK" -> Themes.SPRING_AND_DUSK
                    "CHOCOLATE_STRAWBERRIES" -> Themes.STRAWBERRIES
                    else -> Themes.DEFAULT
                }.name
            )
        }
    }

    fun isColoredTheme(theme: Themes): Boolean {
        return false
    }

    fun isPitchBlack(context: Context): Boolean {
        val preferences: PreferencesHelper by injectLazy()
        return context.isInNightMode() && preferences.themeDarkAmoled().get()
    }

    fun hasDarkActionBarInLight(context: Context, theme: Themes): Boolean {
        return !context.isInNightMode()
    }

    fun readerBackgroundColor(theme: Int): Int {
        return when (theme) {
            1 -> Color.BLACK
            else -> Color.WHITE
        }
    }
}

fun AppCompatActivity.setThemeByPref(preferences: PreferencesHelper) {
    if (preferences.nightMode().isNotSet() && preferences.oldTheme().isSet()) {
        ThemeUtil.convertTheme(preferences, preferences.oldTheme().get())
        preferences.oldTheme().delete()
    }
    val theme = getPrefTheme(preferences)
    setTheme(theme.styleRes)
}

fun AppCompatActivity.getThemeWithExtras(
    theme: Resources.Theme,
    preferences: PreferencesHelper,
    oldTheme: Resources.Theme?,
): Resources.Theme {
    val useAmoled =
        (isInNightMode() || preferences.nightMode().get() == AppCompatDelegate.MODE_NIGHT_YES) &&
            preferences.themeDarkAmoled().get()
    if (oldTheme != null && useAmoled) {
        val array = oldTheme.obtainStyledAttributes(intArrayOf(R.attr.background))
        val bg = array.getColor(0, 0)
        if (bg == Color.BLACK) {
            return oldTheme
        }
    }
    if (useAmoled) {
        theme.applyStyle(R.style.ThemeOverlay_Tachiyomi_Amoled, true)
        val prefTheme = getPrefTheme(preferences)
        if (ThemeUtil.isColoredTheme(prefTheme)) {
        }
    }
    return theme
}

fun Context.getPrefTheme(preferences: PreferencesHelper): Themes {
    // Using a try catch in case I start to remove themes
    return try {
        (
            if ((applicationContext.isInNightMode() || preferences.nightMode()
                    .get() == AppCompatDelegate.MODE_NIGHT_YES) &&
                preferences.nightMode().get() != AppCompatDelegate.MODE_NIGHT_NO
            ) preferences.darkTheme() else preferences.lightTheme()
            ).get()
    } catch (e: Exception) {
        ThemeUtil.convertNewThemes(preferences.context)
        getPrefTheme(preferences)
    }
}
