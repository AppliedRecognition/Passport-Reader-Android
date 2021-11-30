package com.appliedrec.mrtdreader;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.appliedrec.mrtdreader.databinding.ActivityMrtdreaderBinding;

import org.jmrtd.PassportService;

import java.util.Optional;

/**
 * Machine-Readable Travel Document (MRTD) scan activity
 *
 * <p>The activity will ask the user to place their travel document close to their device and read the document's NFC chip</p>
 * @version 1.0.0
 */
public class MRTDScanActivity extends AppCompatActivity {

    public static final int REQUEST_CODE_NFC_PERMISSION = 0;

    private NFCPermissionListener nfcPermissionListener;
    ActivityMrtdreaderBinding viewBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityMrtdreaderBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        onWaiting();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewBinding = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_NFC_PERMISSION) {
            getNfcPermissionListener().ifPresent(listener -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    listener.onNFCPermissionGranted();
                } else {
                    listener.onNFCPermissionDenied();
                }
            });
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public void onWaiting() {
        viewBinding.textView.setText(R.string.mrtd_reader_title);
        viewBinding.progressBar.setVisibility(View.GONE);
        viewBinding.progressIndicator.setVisibility(View.GONE);
    }

    public void onReading() {
        viewBinding.textView.setText(R.string.mrtd_reading_document);
        viewBinding.progressIndicator.setVisibility(View.VISIBLE);
        viewBinding.progressBar.setVisibility(View.GONE);
    }

    public void onNoNFC() {
        viewBinding.textView.setText(R.string.mrtd_no_nfc_reader);
        viewBinding.progressBar.setVisibility(View.GONE);
        viewBinding.progressIndicator.setVisibility(View.GONE);
    }

    public void onScanProgress(MRTDReaderProgress progress) {
        String message;
        switch (progress.getFileId()) {
            case PassportService.EF_COM:
                message = getString(R.string.mrtd_evt_bac);
            case PassportService.EF_SOD:
                message = getString(R.string.mrtd_evt_bac);
                break;
            case PassportService.EF_DG1:
                message = getString(R.string.mrtd_evt_mrz);
                break;
            case PassportService.EF_DG2:
                message = getString(R.string.mrtd_evt_photo);
                break;
            default:
                message = "Reading passport";
        }
        if (viewBinding != null) {
            int done = (int) Math.round(progress.getProgress() * 100.0);
            viewBinding.progressBar.setMax(100);
            viewBinding.progressBar.setProgress(done);
            viewBinding.progressBar.setVisibility(View.VISIBLE);
            viewBinding.progressIndicator.setVisibility(View.GONE);
            viewBinding.textView.setText(message);
        }
    }

    public void setNfcPermissionListener(NFCPermissionListener nfcPermissionListener) {
        this.nfcPermissionListener = nfcPermissionListener;
    }

    public Optional<NFCPermissionListener> getNfcPermissionListener() {
        return Optional.ofNullable(nfcPermissionListener);
    }
}
