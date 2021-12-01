package com.appliedrec.mrtd_reader_app;

import android.content.SharedPreferences;

import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.appliedrec.mrtdreader.BACSpec;
import com.google.gson.Gson;

public class BACSpecModel extends ViewModel implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final MutableLiveData<BACSpec> liveData = new MutableLiveData<>();
    private SharedPreferences preferences;
    private final Gson gson = new Gson();
    private static final String PREF_KEY = "bacSpec";
    private BACSpec bacSpec;

    public BACSpecModel() {
    }

    @MainThread
    public void setSharedPreferences(SharedPreferences preferences) {
        this.preferences = preferences;
        this.preferences.registerOnSharedPreferenceChangeListener(this);
        this.bacSpec = bacSpecFromPreferences();
        liveData.setValue(this.bacSpec);
    }

    public LiveData<BACSpec> getBACSpec() {
        return liveData;
    }

    public void setBACSpec(BACSpec bacSpec) {
        this.bacSpec = bacSpec;
        if (preferences == null) {
            return;
        }
        if (bacSpec == null) {
            preferences.edit().remove(PREF_KEY).apply();
        } else {
            preferences.edit().putString(PREF_KEY, gson.toJson(bacSpec)).apply();
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (preferences != null) {
            preferences.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_KEY.equals(key)) {
            this.bacSpec = bacSpecFromPreferences();
            liveData.postValue(this.bacSpec);
        }
    }

    private BACSpec bacSpecFromPreferences() {
        if (preferences == null) {
            return null;
        }
        String bacSpecsString = preferences.getString(PREF_KEY, null);
        if (bacSpecsString == null || bacSpecsString.isEmpty()) {
            return null;
        }
        return gson.fromJson(bacSpecsString, BACSpec.class);
    }
}
