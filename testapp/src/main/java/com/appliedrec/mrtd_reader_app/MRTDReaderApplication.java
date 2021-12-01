package com.appliedrec.mrtd_reader_app;

import android.app.Application;
import android.content.pm.PackageManager;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDFactory;
import com.appliedrec.verid.core2.VerIDFactoryDelegate;
import com.microblink.MicroblinkSDK;

public class MRTDReaderApplication extends Application implements VerIDFactoryDelegate {

    private boolean microblinkEnabled = false;
    private VerID verID;
    private Exception verIDError;
    private final Object verIDLoadLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            String microblinkKey = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA).metaData.getString("com.appliedrec.microblink_key");
            if (microblinkKey != null) {
                MicroblinkSDK.setLicenseKey(microblinkKey, this);
                microblinkEnabled = true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        VerIDFactory verIDFactory = new VerIDFactory(this);
        verIDFactory.setDelegate(this);
        verIDFactory.createVerID();
    }

    public boolean isMicroblinkEnabled() {
        return microblinkEnabled;
    }

    public VerID getVerID() throws Exception {
        synchronized (verIDLoadLock) {
            while (verID == null && verIDError == null) {
                verIDLoadLock.wait();
            }
        }
        if (verID != null) {
            return verID;
        }
        throw verIDError;
    }

    @Override
    public void onVerIDCreated(VerIDFactory factory, VerID verID) {
        synchronized (verIDLoadLock) {
            this.verID = verID;
            verIDLoadLock.notifyAll();
        }
    }

    @Override
    public void onVerIDCreationFailed(VerIDFactory factory, Exception error) {
        this.verIDError = error;
        verIDLoadLock.notifyAll();
    }
}
