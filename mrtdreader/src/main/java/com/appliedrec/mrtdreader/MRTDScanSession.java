package com.appliedrec.mrtdreader;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * @since 2.0.0
 */
public class MRTDScanSession implements Application.ActivityLifecycleCallbacks, NFCPermissionListener, NfcAdapter.ReaderCallback {

    private static final String EXTRA_SESSION_ID = "com.appliedrec.mrtdreader.EXTRA_SESSION_ID";
    private final WeakReference<Context> contextRef;
    private final BACSpec bacSpec;
    private final int sessionId;
    private static final AtomicInteger SESSION_ID = new AtomicInteger(0);
    private MRTDScanSessionListener listener;
    private WeakReference<MRTDScanActivity> mrtdScanActivityRef;
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private NfcAdapter nfcAdapter;
    private Disposable readerDisposable;

    /**
     * Session constructor
     * @param context
     * @param bacSpec
     * @since 2.0.0
     */
    public MRTDScanSession(@NonNull Context context, @NonNull BACSpec bacSpec) {
        contextRef = new WeakReference<>(context);
        this.bacSpec = bacSpec;
        sessionId = SESSION_ID.getAndIncrement();
    }

    /**
     * @return Session listener
     * @since 2.0.0
     */
    public Optional<MRTDScanSessionListener> getListener() {
        return Optional.ofNullable(listener);
    }

    /**
     * @param listener Session listener
     * @since 2.0.0
     */
    public void setListener(@Nullable MRTDScanSessionListener listener) {
        this.listener = listener;
    }

    /**
     * Start session
     * @since 2.0.0
     */
    public void start() {
        if (isStarted.compareAndSet(false, true)) {
            new Handler(Looper.getMainLooper()).post(() -> {
                getContext().ifPresent(context -> {
                    registerActivityLifecycleListener();
                    Intent intent = new Intent(context, MRTDScanActivity.class);
                    intent.putExtra(EXTRA_SESSION_ID, sessionId);
                    if (!(context instanceof Activity)) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    context.startActivity(intent);
                });
            });
        }
    }

    /**
     * Cancel session
     * @since 2.0.0
     */
    public void cancel() {
        new Handler(Looper.getMainLooper()).post(() -> {
            getScanActivity().ifPresent(mrtdScanActivity -> {
                mrtdScanActivity.setResult(Activity.RESULT_CANCELED);
                mrtdScanActivity.finish();
            });
        });
    }

    /**
     * @return Context within which the session was created
     * @since 2.0.0
     */
    public Optional<Context> getContext() {
        return Optional.ofNullable(contextRef.get());
    }

    /**
     * @return Basic Access Control (BAC) spec
     * @since 2.0.0
     */
    public BACSpec getBACSpec() {
        return bacSpec;
    }

    private void registerActivityLifecycleListener() {
        getContext().ifPresent(context -> ((Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(this));
    }

    private void unregisterActivityLifecycleListener() {
        getContext().ifPresent(context -> ((Application) context.getApplicationContext()).unregisterActivityLifecycleCallbacks(this));
    }

    private Optional<MRTDScanActivity> getScanActivity(Activity activity) {
        if (activity instanceof MRTDScanActivity && activity.getIntent() != null && activity.getIntent().getIntExtra(EXTRA_SESSION_ID, -1) == sessionId) {
            return Optional.of((MRTDScanActivity)activity);
        } else {
            return Optional.empty();
        }
    }

    private Optional<MRTDScanActivity> getScanActivity() {
        if (mrtdScanActivityRef == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mrtdScanActivityRef.get());
    }

    //region ActivityLifecycleCallbacks

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        getScanActivity(activity).ifPresent(mrtdScanActivity -> {
            mrtdScanActivityRef = new WeakReference<>(mrtdScanActivity);
            mrtdScanActivity.setNfcPermissionListener(this);
        });
    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
        getScanActivity(activity).ifPresent(this::startReadingNFCTags);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        getScanActivity(activity).ifPresent(this::stopReadingNFCTags);
    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        getScanActivity(activity).ifPresent(mrtdScanActivity -> {
            mrtdScanActivityRef = null;
            if (mrtdScanActivity.isFinishing()) {
                if (readerDisposable != null && !readerDisposable.isDisposed()) {
                    readerDisposable.dispose();
                }
                readerDisposable = null;
                unregisterActivityLifecycleListener();
                getListener().ifPresent(listener -> listener.onMRTDScanCancelled(bacSpec));
                isStarted.set(false);
            }
        });
    }

    //region NFC permission listener

    @Override
    public void onNFCPermissionGranted() {
        getScanActivity().ifPresent(this::startReadingNFCTags);
    }

    @Override
    public void onNFCPermissionDenied() {
        // TODO
    }

    //endregion

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE,
                        NfcAdapter.STATE_OFF);
                switch (state) {
                    case NfcAdapter.STATE_TURNING_OFF:
                    case NfcAdapter.STATE_OFF:
                        getScanActivity().ifPresent(MRTDScanActivity::onNoNFC);
                        break;
                    case NfcAdapter.STATE_TURNING_ON:
                    case NfcAdapter.STATE_ON:
                        getScanActivity().ifPresent(MRTDScanActivity::onWaiting);
                        break;
                }
            }
        }
    };

    private void startReadingNFCTags(MRTDScanActivity mrtdScanActivity) {
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        mrtdScanActivity.registerReceiver(broadcastReceiver, filter);
        if (ContextCompat.checkSelfPermission(mrtdScanActivity, Manifest.permission.NFC) == PackageManager.PERMISSION_GRANTED) {
            if (nfcAdapter == null) {
                nfcAdapter = NfcAdapter.getDefaultAdapter(mrtdScanActivity);
            }
            if (nfcAdapter != null && nfcAdapter.isEnabled()) {
                Bundle options = new Bundle();
                options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 50);
                nfcAdapter.enableReaderMode(mrtdScanActivity, this, NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK | NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B, options);
            } else {
                // TODO
            }
        } else {
            ActivityCompat.requestPermissions(mrtdScanActivity, new String[]{Manifest.permission.NFC}, MRTDScanActivity.REQUEST_CODE_NFC_PERMISSION);
        }
    }

    private void stopReadingNFCTags(MRTDScanActivity mrtdScanActivity) {
        mrtdScanActivity.unregisterReceiver(broadcastReceiver);
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            nfcAdapter.disableReaderMode(mrtdScanActivity);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (Arrays.asList(tag.getTechList()).contains("android.nfc.tech.IsoDep")) {
            if (readerDisposable != null) {
                return;
            }
            readerDisposable = MRTDReader.createProgressObservable(IsoDep.get(tag), bacSpec)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext(mrtdReaderProgress -> {
                        getScanActivity().ifPresent(mrtdScanActivity -> mrtdScanActivity.runOnUiThread(() -> {
                            mrtdScanActivity.onScanProgress(mrtdReaderProgress);
                        }));
                    })
                    .lastOrError()
                    .subscribe(
                        result -> {
                            readerDisposable = null;
                            unregisterActivityLifecycleListener();
                            getListener().ifPresent(listener -> listener.onMRTDScanSucceeded(bacSpec, result.getResult()));
                            getScanActivity().ifPresent(Activity::finish);
                        },
                        error -> {
                            readerDisposable = null;
                            unregisterActivityLifecycleListener();
                            getListener().ifPresent(listener -> listener.onMRTDScanFailed(bacSpec, error));
                            getScanActivity().ifPresent(Activity::finish);
                        }
                );
        }
    }
}
