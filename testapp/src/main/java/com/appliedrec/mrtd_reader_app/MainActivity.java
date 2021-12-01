package com.appliedrec.mrtd_reader_app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.appliedrec.mrtd_reader_sample.R;
import com.appliedrec.mrtd_reader_sample.databinding.ActivityMainBinding;
import com.appliedrec.mrtdreader.BACSpec;
import com.appliedrec.mrtdreader.MRTDScanResult;
import com.appliedrec.mrtdreader.MRTDScanSession;
import com.appliedrec.mrtdreader.MRTDScanSessionListener;

public class MainActivity extends AppCompatActivity implements MRTDScanSessionListener, BACEntryFragment.Listener {

    private BACSpecModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding viewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        viewModel = new ViewModelProvider(this).get(BACSpecModel.class);
        viewModel.setSharedPreferences(PreferenceManager.getDefaultSharedPreferences(this));
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().add(viewBinding.container.getId(), BACEntryFragment.newInstance(viewModel.getBACSpec().getValue())).commit();
        }
    }

    //region MRTD scan listener

    @Override
    public void onMRTDScanSucceeded(BACSpec bacSpec, MRTDScanResult result) {
        Intent intent = new Intent(this, CaptureResultActivity.class);
        intent.putExtra(CaptureResultActivity.EXTRA_MRTD_SCAN_RESULT, result);
        startActivity(intent);
    }

    @Override
    public void onMRTDScanFailed(BACSpec bacSpec, Throwable throwable) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.failed_to_read_travel_document);
        builder.setMessage(throwable.toString());
        builder.setNeutralButton(android.R.string.ok, null);
        builder.create().show();
    }

    @Override
    public void onMRTDScanCancelled(BACSpec bacSpec) {

    }

    //endregion

    @Override
    public void onRequestCapture(BACSpec bacSpec) {
        MRTDScanSession session = new MRTDScanSession(this, bacSpec);
        session.setListener(this);
        session.start();
    }
}
