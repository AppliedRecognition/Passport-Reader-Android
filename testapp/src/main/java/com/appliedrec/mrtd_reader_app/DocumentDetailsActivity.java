package com.appliedrec.mrtd_reader_app;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.appliedrec.mrtd_reader_sample.databinding.ActivityDocumentDetailsBinding;
import com.appliedrec.mrtdreader.MRTDScanResult;

public class DocumentDetailsActivity extends AppCompatActivity {

    MRTDScanResult scanResult;

    public static final String EXTRA_MRTD_SCAN_RESULT = "com.appliedrec.EXTRA_MRTD_SCAN_RESULT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() != null) {
            scanResult = getIntent().getParcelableExtra(EXTRA_MRTD_SCAN_RESULT);
        }
        if (scanResult == null) {
            finish();
            return;
        }
        ActivityDocumentDetailsBinding viewBinding = ActivityDocumentDetailsBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        viewBinding.documentNumber.setText(scanResult.getDocumentNumber());
        viewBinding.personalNumber.setText(scanResult.getPersonalNumber());
        viewBinding.documentCode.setText(scanResult.getDocumentCode());
        viewBinding.issuingState.setText(scanResult.getIssuingState());
        viewBinding.dateOfExpiry.setText(scanResult.getDateOfExpiry());
        viewBinding.primaryIdentifier.setText(scanResult.getPrimaryIdentifier());
        viewBinding.secondaryIdentifiers.setText(TextUtils.join(", ", scanResult.getSecondaryIdentifiers()));
        viewBinding.nationality.setText(scanResult.getNationality());
        viewBinding.gender.setText(scanResult.getGender());
        viewBinding.dateOfBirth.setText(scanResult.getDateOfBirth());
        if (scanResult.getFaceImage() != null) {
            viewBinding.imageView.setImageBitmap(scanResult.getFaceImage());
        } else {
            viewBinding.imageView.setVisibility(View.GONE);
        }
    }
}
