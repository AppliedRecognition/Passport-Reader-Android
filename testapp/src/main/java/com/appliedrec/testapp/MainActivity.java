package com.appliedrec.testapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.appliedrec.mrtdreader.BACInputFragment;
import com.appliedrec.mrtdreader.BACSpec;
import com.appliedrec.mrtdreader.MRTDScanActivity;
import com.appliedrec.mrtdreader.MRTDScanResult;

import java.util.Date;

public class MainActivity extends AppCompatActivity implements BACInputFragment.OnBACInputListener {

    private static final int REQUEST_CODE_MRTD_SCAN = 0;
    private BACSpec bacSpec;
    private static class StorageKeys {
        public static final String DOCUMENT_NUMBER = "documentNumber";
        public static final String DATE_OF_BIRTH = "dateOfBirth";
        public static final String DATE_OF_EXPIRY = "dateOfExpiry";
    }
    private static final String BAC_INPUT_TAG = "bacInput";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            SharedPreferences preferences = getPreferences(MODE_PRIVATE);
            if (preferences.contains(StorageKeys.DOCUMENT_NUMBER) && preferences.contains(StorageKeys.DATE_OF_BIRTH) && preferences.contains(StorageKeys.DATE_OF_EXPIRY)) {
                bacSpec = new BACSpec(preferences.getString(StorageKeys.DOCUMENT_NUMBER, null), new Date(preferences.getLong(StorageKeys.DATE_OF_BIRTH, new Date().getTime())), new Date(preferences.getLong(StorageKeys.DATE_OF_EXPIRY, new Date().getTime())));
                invalidateOptionsMenu();
            }
            getSupportFragmentManager().beginTransaction().add(R.id.container, BACInputFragment.newInstance(bacSpec), BAC_INPUT_TAG).commit();
        } else {
            if (bacSpec == null && savedInstanceState.containsKey(StorageKeys.DOCUMENT_NUMBER) && savedInstanceState.containsKey(StorageKeys.DATE_OF_BIRTH) && savedInstanceState.containsKey(StorageKeys.DATE_OF_EXPIRY)) {
                String docNumber = savedInstanceState.getString(StorageKeys.DOCUMENT_NUMBER);
                long dob = savedInstanceState.getLong(StorageKeys.DATE_OF_BIRTH);
                long doe = savedInstanceState.getLong(StorageKeys.DATE_OF_EXPIRY);
                bacSpec = new BACSpec(docNumber, new Date(dob), new Date(doe));
            }
            ((BACInputFragment) getSupportFragmentManager().findFragmentByTag(BAC_INPUT_TAG)).setBACSpec(bacSpec);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (bacSpec != null) {
            outState.putString(StorageKeys.DOCUMENT_NUMBER, bacSpec.getDocumentNumber());
            outState.putLong(StorageKeys.DATE_OF_BIRTH, bacSpec.getDateOfBirth().getTime());
            outState.putLong(StorageKeys.DATE_OF_EXPIRY, bacSpec.getDateOfExpiry().getTime());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_scan).setEnabled(bacSpec != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_scan && bacSpec != null) {
            SharedPreferences preferences = getPreferences(MODE_PRIVATE);
            preferences.edit()
                    .putString(StorageKeys.DOCUMENT_NUMBER, bacSpec.getDocumentNumber())
                    .putLong(StorageKeys.DATE_OF_BIRTH, bacSpec.getDateOfBirth().getTime())
                    .putLong(StorageKeys.DATE_OF_EXPIRY, bacSpec.getDateOfExpiry().getTime())
                    .apply();
            Intent intent = new Intent(this, MRTDScanActivity.class);
            intent.putExtra(MRTDScanActivity.EXTRA_BAC_SPEC, bacSpec);
            startActivityForResult(intent, REQUEST_CODE_MRTD_SCAN);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_MRTD_SCAN && resultCode == RESULT_OK && data != null && data.hasExtra(MRTDScanActivity.EXTRA_MRTD_SCAN_RESULT)) {
            MRTDScanResult scanResult = data.getParcelableExtra(MRTDScanActivity.EXTRA_MRTD_SCAN_RESULT);
            Intent intent = new Intent(this, ScanResultActivity.class);
            intent.putExtra(MRTDScanActivity.EXTRA_MRTD_SCAN_RESULT, scanResult);
            startActivity(intent);
        } else if (requestCode == REQUEST_CODE_MRTD_SCAN && resultCode == RESULT_OK && data != null && data.hasExtra(MRTDScanActivity.EXTRA_MRTD_SCAN_ERROR)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.failed_to_read_travel_document);
            String message = data.getStringExtra(MRTDScanActivity.EXTRA_MRTD_SCAN_ERROR);
            if (message != null) {
                builder.setMessage(message);
            }
            builder.setNeutralButton(android.R.string.ok, null);
            builder.create().show();
        }
    }

    @Override
    public void onBACChanged(BACSpec bacSpec) {
        this.bacSpec = bacSpec;
        invalidateOptionsMenu();
    }
}
