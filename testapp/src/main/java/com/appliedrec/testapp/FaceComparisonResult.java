package com.appliedrec.testapp;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.TextView;

public class FaceComparisonResult extends AppCompatActivity {

    public static final String EXTRA_LIVE_IMAGE_URI = "com.appliedrec.EXTRA_LIVE_IMAGE_URI";
    public static final String EXTRA_MRTD_IMAGE_URI = "com.appliedrec.EXTRA_MRTD_IMAGE_URI";
    public static final String EXTRA_SCORE = "com.appliedrec.EXTRA_SCORE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_comparison_result);
        ImageView mrtdImageView = findViewById(R.id.mrtdImage);
        ImageView liveImageView = findViewById(R.id.faceImage);
        Uri mrtdImageUri = getIntent().getParcelableExtra(EXTRA_MRTD_IMAGE_URI);
        Uri liveImageUri = getIntent().getParcelableExtra(EXTRA_LIVE_IMAGE_URI);
        mrtdImageView.setImageURI(mrtdImageUri);
        liveImageView.setImageURI(liveImageUri);
        float score = getIntent().getFloatExtra(EXTRA_SCORE, 0f);
        TextView scoreTextView = findViewById(R.id.score);
        scoreTextView.setText(getString(R.string.similarity_score, score*10f));
    }
}
