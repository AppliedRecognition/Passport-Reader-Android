package com.appliedrec.mrtd_reader_app

import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.appliedrec.mrtdreader.BACSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BACSpecModel : ViewModel(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val _liveData: MutableLiveData<BACSpec?> = MutableLiveData<BACSpec?>()
    private var preferences: SharedPreferences? = null
    private var _bacSpec: BACSpec? = null

    @MainThread
    fun setSharedPreferences(preferences: SharedPreferences?) {
        this.preferences = preferences
        this.preferences?.registerOnSharedPreferenceChangeListener(this)
        _bacSpec = bacSpecFromPreferences()
        _liveData.setValue(_bacSpec)
    }

    var bacSpec: BACSpec?
        get() = _bacSpec
        set(bacSpec) {
            _bacSpec = bacSpec
            if (preferences == null) {
                return
            }
            if (this._bacSpec == null) {
                preferences!!.edit().remove(PREF_KEY).apply()
            } else {
                preferences!!.edit().putString(PREF_KEY, Json.encodeToString(this._bacSpec)).apply()
            }
            _liveData.postValue(_bacSpec)
        }

    val liveData: LiveData<BACSpec?> = _liveData

    override fun onCleared() {
        super.onCleared()
        preferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun bacSpecFromPreferences(): BACSpec? {
        val bacSpecsString: String = preferences?.getString(PREF_KEY, null) ?: return null
        if (bacSpecsString.isEmpty()) {
            return null
        }
        return Json.decodeFromString(bacSpecsString)
    }

    companion object {
        private const val PREF_KEY = "bacSpec"
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (PREF_KEY == key) {
            _bacSpec = bacSpecFromPreferences()
            _liveData.postValue(_bacSpec)
        }
    }
}