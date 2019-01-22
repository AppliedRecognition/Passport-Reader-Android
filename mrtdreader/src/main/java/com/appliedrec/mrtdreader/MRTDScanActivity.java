package com.appliedrec.mrtdreader;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.Arrays;

/**
 * Machine-Readable Travel Document (MRTD) scan activity
 *
 * <p>The activity will ask the user to place their travel document close to their device and read the document's NFC chip</p>
 * @version 1.0.0
 */
public class MRTDScanActivity extends AppCompatActivity implements MRTDReaderFragment.MRTDReaderFragmentInteractionListener {

    private static final String TAG = "MRTDReaderAct";

    /**
     * Constant representing an intent extra key for BAC specification
     * @version 1.0.0
     */
    public static final String EXTRA_BAC_SPEC = "com.appliedrec.EXTRA_BAC_SPEC";
    /**
     * Constant representing an intent extra key for the scan result
     * @version 1.0.0
     */
    public static final String EXTRA_MRTD_SCAN_RESULT = "com.appliedrec.EXTRA_MRTD_SCAN_RESULT";
    /**
     * Constant representing an intent extra key for the description of a scan error
     * @version 1.0.0
     */
    public static final String EXTRA_MRTD_SCAN_ERROR = "com.appliedrec.EXTRA_MRTD_SCAN_ERROR";
    public static final String MRTD_READER_TAG = "MRTDReader";

    private NfcAdapter nfcAdapter;

    private MRTDReaderFragment readerFragment;
    private boolean readStarted = false;
    private BACSpec bacSpec;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mrtdreader);
        if (savedInstanceState == null){
            bacSpec = getIntent().getParcelableExtra(EXTRA_BAC_SPEC);
            readerFragment = MRTDReaderFragment.newInstance(bacSpec);
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.mrtdreader_root, readerFragment, MRTD_READER_TAG);
            ft.commit();
        }
        else{
            bacSpec = savedInstanceState.getParcelable(EXTRA_BAC_SPEC);
            readerFragment = (MRTDReaderFragment)getSupportFragmentManager().findFragmentByTag(MRTD_READER_TAG);
        }

        //resolveIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(EXTRA_BAC_SPEC, bacSpec);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        //this will get called with the NFC intent when we scan an NFC
        resolveIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();

        readStarted = false;
        this.unregisterReceiver(mReceiver);
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
            nfcAdapter = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.NFC) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.NFC}, 0);
        } else {
            startNfcAdapter();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startNfcAdapter();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void startNfcAdapter() {
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        this.registerReceiver(mReceiver, filter);

        if (!readStarted) {
            nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (nfcAdapter != null && nfcAdapter.isEnabled()) {
                Log.d(TAG, "Resume is notifying NFCWaiting!");
                readerFragment.notifyNFCWaiting();
                enableForegroundDispatch();
            } else {

                readerFragment.notifyNFCNotEnabled();
            }
        }
    }

    private void enableForegroundDispatch() {

        Intent in = new Intent(this, this.getClass());
        in.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        in.putExtra(EXTRA_BAC_SPEC, bacSpec);

        PendingIntent pi = PendingIntent.getActivity( this, 0, in, PendingIntent.FLAG_UPDATE_CURRENT);

        String[][] filter = new String[][]{
                new String[]{ "android.nfc.tech.IsoDep" }
        };
        nfcAdapter.enableForegroundDispatch( this, pi, null, filter);
    }

    private void resolveIntent(Intent intent) {
        Log.v(TAG, "resolveIntent");
        String action = intent.getAction();
        Log.v(TAG, action);

        if( NfcAdapter.ACTION_TECH_DISCOVERED.equals(action) )
        {
            Tag t = intent.getExtras().getParcelable(NfcAdapter.EXTRA_TAG);

            if( Arrays.asList( t.getTechList() ).contains( "android.nfc.tech.IsoDep" ) )
            {
                handleIsoDepFound( IsoDep.get(t));
            }
        }
    }

    private void handleIsoDepFound(IsoDep isodep) {
        Log.v(TAG, "handleIsoDepFound");
        readerFragment.onIsoDepReceived(isodep);
    }

    @Override
    public void onMRTDReadStarted() {
        readStarted = true;
    }

    @Override
    public void onMRTDReadFailed(String errorDescription) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_BAC_SPEC, bacSpec);
        intent.putExtra(EXTRA_MRTD_SCAN_ERROR, errorDescription);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onMRTDReadCompleted(MRTDScanResult passportResult) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_BAC_SPEC, bacSpec);
        intent.putExtra(EXTRA_MRTD_SCAN_RESULT, passportResult);
        setResult(RESULT_OK, intent);
        finish();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)) {
                final int state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE,
                        NfcAdapter.STATE_OFF);
                Log.d(TAG, "Broadcast: NFC Radio State: " + state);
                switch (state) {
                    case NfcAdapter.STATE_TURNING_OFF:
                    case NfcAdapter.STATE_OFF:
                        readerFragment.notifyNFCNotEnabled();
                        break;

                    case NfcAdapter.STATE_TURNING_ON:
                    case NfcAdapter.STATE_ON:
                        readerFragment.notifyNFCWaiting();
                        break;
                }
            }
        }
    };
}
