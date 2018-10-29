package com.appliedrec.testapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.appliedrec.mrtdreader.MRTDScanActivity;
import com.appliedrec.mrtdreader.MRTDScanResult;
import com.appliedrec.ver_id.VerID;
import com.appliedrec.ver_id.VerIDLivenessDetectionIntent;
import com.appliedrec.ver_id.model.VerIDFace;
import com.appliedrec.ver_id.session.VerIDLivenessDetectionSessionSettings;
import com.appliedrec.ver_id.session.VerIDSessionResult;
import com.appliedrec.ver_id.ui.VerIDActivity;
import com.appliedrec.ver_id.util.FaceUtil;
import com.appliedrec.ver_id.util.ImageUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ScanResultActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_LIVENESS_DETECTION = 0;
    Bitmap faceBitmap;
    VerIDFace mrtdFace;
    MRTDScanResult scanResult;
    File croppedMrtdImageFile;

    private static class StorageKeys {
        public static final String MRTD_FACE = "mrtdFace";
        public static final String SCAN_RESULT = "scanResult";
        public static final String CROPPED_MRTD_IMAGE_FILE = "croppedMrtdImageFile";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (savedInstanceState != null) {
            mrtdFace = savedInstanceState.getParcelable(StorageKeys.MRTD_FACE);
            scanResult = savedInstanceState.getParcelable(StorageKeys.SCAN_RESULT);
            String croppedMrtdImagePath = savedInstanceState.getString(StorageKeys.CROPPED_MRTD_IMAGE_FILE);
            if (croppedMrtdImagePath != null) {
                croppedMrtdImageFile = new File(croppedMrtdImagePath);
            }
        } else if (intent != null && intent.hasExtra(MRTDScanActivity.EXTRA_MRTD_SCAN_RESULT)) {
            scanResult = intent.getParcelableExtra(MRTDScanActivity.EXTRA_MRTD_SCAN_RESULT);
        }
        showScanResult();
        invalidateOptionsMenu();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mrtdFace != null) {
            outState.putParcelable(StorageKeys.MRTD_FACE, mrtdFace);
        }
        if (scanResult != null) {
            outState.putParcelable(StorageKeys.SCAN_RESULT, scanResult);
        }
        if (croppedMrtdImageFile != null) {
            outState.putString(StorageKeys.CROPPED_MRTD_IMAGE_FILE, croppedMrtdImageFile.getPath());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scan_result, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_compare_live_face).setEnabled(faceBitmap != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_compare_live_face) {
            startLivenessDetectionSession();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showScanResult() {
        setContentView(R.layout.activity_scan_result);
        ((TextView)findViewById(R.id.documentNumber)).setText(scanResult.getDocumentNumber());
        ((TextView)findViewById(R.id.personalNumber)).setText(scanResult.getPersonalNumber());
        ((TextView)findViewById(R.id.documentCode)).setText(scanResult.getDocumentCode());
        ((TextView)findViewById(R.id.issuingState)).setText(scanResult.getIssuingState());
        ((TextView)findViewById(R.id.dateOfExpiry)).setText(scanResult.getDateOfExpiry());
        ((TextView)findViewById(R.id.primaryIdentifier)).setText(scanResult.getPrimaryIdentifier());
        ((TextView)findViewById(R.id.secondaryIdentifiers)).setText(TextUtils.join(", ", scanResult.getSecondaryIdentifiers()));
        ((TextView)findViewById(R.id.nationality)).setText(scanResult.getNationality());
        ((TextView)findViewById(R.id.gender)).setText(scanResult.getGender());
        ((TextView)findViewById(R.id.dateOfBirth)).setText(scanResult.getDateOfBirth());
        faceBitmap = BitmapFactory.decodeFile(scanResult.getImageFilePath());
        if (faceBitmap != null) {
            ((ImageView)findViewById(R.id.imageView)).setImageBitmap(faceBitmap);
        } else {
            findViewById(R.id.imageView).setVisibility(View.GONE);
        }
    }

    private void startLivenessDetectionSession() {
        setContentView(R.layout.activity_loading);
        final TextView label = findViewById(R.id.label);
        label.setText(R.string.loading_face_detection);
        VerID.shared.load(this, new VerID.LoadCallback() {
            @Override
            public void onLoad() {
                if (isDestroyed()) {
                    return;
                }
                if (mrtdFace != null) {
                    onMRTDFaceFound(mrtdFace);
                    return;
                }
                label.setText(R.string.detecting_passport_face);
                VerID.shared.detectFaceInImage(faceBitmap, false, false, new VerID.VerIDTaskCallback<VerIDFace>() {
                    @Override
                    public void onVerIDTaskFinished(VerIDFace result) {
                        if (isDestroyed()) {
                            return;
                        }
                        onMRTDFaceFound(result);
                    }

                    @Override
                    public void onVerIDTaskFailed(Exception exception) {
                        showError(exception.getLocalizedMessage());
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                showError(error.getLocalizedMessage());
            }
        });
    }

    private void onMRTDFaceFound(VerIDFace face) {
        mrtdFace = face;
        TextView label = findViewById(R.id.label);
        label.setText(R.string.cropping_image);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                cropMrtdFaceImage();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed()) {
                            return;
                        }
                        if (croppedMrtdImageFile == null) {
                            showError(getString(R.string.failed_to_crop_image));
                            return;
                        }
                        showScanResult();
                        VerIDLivenessDetectionSessionSettings sessionSettings = new VerIDLivenessDetectionSessionSettings();
                        sessionSettings.includeFaceTemplatesInResult = true;
                        VerIDLivenessDetectionIntent intent = new VerIDLivenessDetectionIntent(ScanResultActivity.this, sessionSettings);
                        startActivityForResult(intent, REQUEST_CODE_LIVENESS_DETECTION);
                    }
                });
            }
        });
    }

    private void showError(String description) {

    }

    @WorkerThread
    private void cropMrtdFaceImage() {
        if (croppedMrtdImageFile != null) {
            return;
        }
        if (mrtdFace == null && faceBitmap == null) {
            return;
        }
        Bitmap croppedMrtdImage = ImageUtil.cropBitmapToFace(faceBitmap, mrtdFace);
        try {
            croppedMrtdImageFile = File.createTempFile("mrtd_", "png");
            OutputStream outputStream = new FileOutputStream(croppedMrtdImageFile);
            croppedMrtdImage.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_LIVENESS_DETECTION) {
            if (resultCode == RESULT_OK && data != null && data.hasExtra(VerIDActivity.EXTRA_SESSION_RESULT)) {
                VerIDSessionResult sessionResult = data.getParcelableExtra(VerIDActivity.EXTRA_SESSION_RESULT);
                if (sessionResult.isPositive()) {
                    VerIDFace[] faces = sessionResult.getFacesSuitableForRecognition(VerID.Bearing.STRAIGHT);
                    if (faces.length > 0) {
                        final VerIDFace liveFace = faces[0];
                        Uri imageUri = sessionResult.getFaceImages().get(liveFace);
                        try {
                            InputStream inputStream = getContentResolver().openInputStream(imageUri);
                            final Bitmap liveFaceImage = BitmapFactory.decodeStream(inputStream);
                            AsyncTask.execute(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Bitmap liveFaceBitmap = ImageUtil.cropBitmapToFace(liveFaceImage, liveFace);
                                        final File imageTempFile = File.createTempFile("live_face_", "png");
                                        OutputStream outputStream = new FileOutputStream(imageTempFile);
                                        liveFaceBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                                        final float score = FaceUtil.compareFaces(liveFace, mrtdFace);
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (isDestroyed()) {
                                                    return;
                                                }
                                                Intent intent = new Intent(ScanResultActivity.this, FaceComparisonResult.class);
                                                intent.putExtra(FaceComparisonResult.EXTRA_LIVE_IMAGE_URI, Uri.fromFile(imageTempFile));
                                                intent.putExtra(FaceComparisonResult.EXTRA_MRTD_IMAGE_URI, Uri.fromFile(croppedMrtdImageFile));
                                                intent.putExtra(FaceComparisonResult.EXTRA_SCORE, score);
                                                startActivity(intent);
                                            }
                                        });
                                    } catch (final Exception e) {
                                        e.printStackTrace();
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                showError(e.getLocalizedMessage());
                                            }
                                        });
                                    }
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            showError(e.getLocalizedMessage());
                        }
                    } else {
                        showError(getString(R.string.failed_to_collect_live_face));
                    }
                } else if (sessionResult.outcome != VerIDSessionResult.Outcome.CANCEL) {
                    showError(getString(R.string.failed_to_collect_live_face));
                }
            }
        }
    }
}
