package com.appliedrec.mrtd_reader_sample;

import android.app.Application;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.ndk.CrashlyticsNdk;

import io.fabric.sdk.android.Fabric;

public class MRTDReaderApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(getApplicationContext(), new Crashlytics(), new CrashlyticsNdk());
    }
}
