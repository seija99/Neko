package eu.kanade.tachiyomi.widget.preference

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder

open class ListMatPreference @JvmOverloads constructor(
    activity: Activity?,
    context: Context,
    attrs: AttributeSet? =
        null
) :
    MatPreference(activity, context, attrs) {

    var sharedPref: String? = null
    var otherPref: Preference? = null
    var entryValues: List<String> = emptyList()
    var entriesRes: Array<Int>
        get() = emptyArray()
        set(value) { entries = value.map { context.getString(it) } }
    private var defValue: String = ""
    var entries: List<String> = emptyList()

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        defValue = defaultValue as? String ?: defValue
    }

    override var customSummaryProvider: SummaryProvider<MatPreference>? = SummaryProvider<MatPreference> {
        val index = entryValues.indexOf(prefs.getStringPref(key, defValue).get())
        if (entries.isEmpty() || index == -1) ""
        else entries[index]
    }

    override fun dialog(): MaterialAlertDialogBuilder {
        return super.dialog().apply {
            setListItems()
        }
    }

    @SuppressLint("CheckResult")
    open fun MaterialAlertDialogBuilder.setListItems() {
        val default = entryValues.indexOf(
            if (sharedPref != null) {
                val settings = context.getSharedPreferences(sharedPref, Context.MODE_PRIVATE)
                settings.getString(key, "")
            } else prefs.getStringPref(key, defValue).get()
        )
        setSingleChoiceItems(entries.toTypedArray(), default) { dialog, pos ->
            val value = entryValues[pos]
            if (sharedPref != null) {
                val oldDef = if (default > -1) entries[default] else ""
                val settings = context.getSharedPreferences(sharedPref, Context.MODE_PRIVATE)
                val edit = settings.edit()
                edit.putString(key, value)
                edit.apply()
                otherPref?.callChangeListener(value)
                if (oldDef == otherPref?.summary || otherPref?.summary.isNullOrEmpty()) otherPref?.summary =
                    entries[pos]
                else otherPref?.summary = otherPref?.summary?.toString()?.replace(
                    oldDef,
                    entries[pos]
                ) ?: entries[pos]
            } else {
                prefs.getStringPref(key, defValue).set(value)
                this@ListMatPreference.summary = this@ListMatPreference.summary
                callChangeListener(value)
            }
            dialog.dismiss()
        }
    }
}
