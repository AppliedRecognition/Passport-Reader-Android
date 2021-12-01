package com.appliedrec.mrtd_reader_app;

import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.core.util.Pair;
import androidx.exifinterface.media.ExifInterface;
import androidx.room.util.StringUtil;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.appliedrec.mrtd_reader_sample.R;
import com.appliedrec.mrtd_reader_sample.databinding.ActivityCaptureResultBinding;
import com.appliedrec.mrtdreader.MRTDScanResult;
import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.Face;
import com.appliedrec.verid.core2.IRecognizable;
import com.appliedrec.verid.core2.RecognizableFace;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDCoreException;
import com.appliedrec.verid.core2.VerIDImageBitmap;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.ui2.IVerIDSession;
import com.appliedrec.verid.ui2.VerIDSession;
import com.appliedrec.verid.ui2.VerIDSessionDelegate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.util.Arrays;
import java.util.StringJoiner;

public class CaptureResultActivity extends AppCompatActivity implements VerIDSessionDelegate {

    public static final String EXTRA_MRTD_SCAN_RESULT = "com.appliedrec.EXTRA_MRTD_SCAN_RESULT";
    private MRTDScanResult scanResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCaptureResultBinding viewBinding = ActivityCaptureResultBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        if (getIntent() == null) {
            finish();
            return;
        }
        scanResult = getIntent().getParcelableExtra(EXTRA_MRTD_SCAN_RESULT);
        if (scanResult == null) {
            finish();
            return;
        }
        StringJoiner stringJoiner = new StringJoiner(" ");
        for (String name : scanResult.getSecondaryIdentifiers()) {
            stringJoiner.add(name);
        }
        stringJoiner.add(scanResult.getPrimaryIdentifier());
        String name = stringJoiner.toString();
        viewBinding.nameTextView.setText(name);
        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(getResources(), scanResult.getFaceImage());
        drawable.setCornerRadius((float)drawable.getIntrinsicWidth()/8f);
        viewBinding.faceImageView.setImageDrawable(drawable);
        viewBinding.faceImageView.setOnClickListener(v -> showDocumentDetails());
        viewBinding.selfieButton.setOnClickListener(v -> compareToSelfie());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scan_result, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_show_document_details) {
            showDocumentDetails();
            return true;
        }
        return false;
    }

    private void showDocumentDetails() {
        Intent intent = new Intent(this, DocumentDetailsActivity.class);
        intent.putExtra(DocumentDetailsActivity.EXTRA_MRTD_SCAN_RESULT, scanResult);
        startActivity(intent);
    }

    private void compareToSelfie() {
        try {
            VerID verID = ((MRTDReaderApplication)getApplication()).getVerID();
            LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
            VerIDSession verIDSession = new VerIDSession(verID, settings);
            verIDSession.setDelegate(this);
            verIDSession.start();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void onSessionFinished(IVerIDSession<?> session, VerIDSessionResult result) {
        if (result.getError().isPresent()) {
            showError(R.string.face_capture_failed);
            return;
        }
        try {
            startActivity(createFaceComparisonIntent(session.getVerID(), result));
        } catch (Exception exception) {
            showError(R.string.face_comparison_failed);
        }
    }

    private void showError(@StringRes int description) {
        new AlertDialog.Builder(this)
                .setMessage(description)
                .setPositiveButton(android.R.string.ok, null)
                .create()
                .show();
    }

    private Intent createFaceComparisonIntent(VerID verID, VerIDSessionResult result) throws Exception {
        Pair<Face,IRecognizable> facePair = detectFaceInImage(scanResult.getFaceImage());
        Bitmap documentFaceImage = cropImageToFace(scanResult.getFaceImage(), facePair.first);
        if (!result.getFirstFaceCapture(Bearing.STRAIGHT).isPresent()) {
            throw new Exception("Face not present in result");
        }
        FaceCapture faceCapture = result.getFirstFaceCapture(Bearing.STRAIGHT).get();
        float score = verID.getFaceRecognition().compareSubjectFacesToFaces(new IRecognizable[]{facePair.second}, new IRecognizable[]{faceCapture.getFace()});
        byte[] documentFaceJpeg = compressImage(documentFaceImage);
        byte[] liveFaceJpeg = compressImage(faceCapture.getFaceImage());
        Intent intent = new Intent(this, FaceComparisonActivity.class);
        intent.putExtra(FaceComparisonActivity.EXTRA_IMAGE1, documentFaceJpeg);
        intent.putExtra(FaceComparisonActivity.EXTRA_IMAGE2, liveFaceJpeg);
        intent.putExtra(FaceComparisonActivity.EXTRA_SCORE, score);
        return intent;
    }

    private byte[] compressImage(Bitmap image) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            image.compress(Bitmap.CompressFormat.JPEG, 75, outputStream);
            outputStream.flush();
            return outputStream.toByteArray();
        }
    }

    private Pair<Face,IRecognizable> detectFaceInImage(Bitmap image) throws Exception {
        VerID verID = ((MRTDReaderApplication)getApplication()).getVerID();
        VerIDImageBitmap verIDImage = new VerIDImageBitmap(image, ExifInterface.ORIENTATION_NORMAL);
        Face[] faces = verID.getFaceDetection().detectFacesInImage(verIDImage.createFaceDetectionImage(), 1, 0);
        if (faces.length == 0) {
            throw new Exception("Face not detected in image");
        }
        IRecognizable[] recognizables = verID.getFaceRecognition().createRecognizableFacesFromFaces(faces, verIDImage);
        return new Pair<>(faces[0], recognizables[0]);
    }

    @SuppressLint("CheckResult")
    private Bitmap cropImageToFace(Bitmap image, Face face) {
        Rect bounds = new Rect();
        face.getBounds().round(bounds);
        bounds.intersect(0, 0, image.getWidth(), image.getHeight());
        return Bitmap.createBitmap(image, bounds.left, bounds.top, bounds.width(), bounds.height());
    }
}