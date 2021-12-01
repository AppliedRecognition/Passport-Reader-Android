package com.appliedrec.mrtd_reader_app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.appliedrec.mrtd_reader_sample.R;
import com.appliedrec.mrtd_reader_sample.databinding.ActivityFaceComparisonBinding;

import org.apache.commons.math3.distribution.NormalDistribution;

public class FaceComparisonActivity extends AppCompatActivity {

    public static final String EXTRA_SCORE = "score";
    public static final String EXTRA_IMAGE1 = "image1";
    public static final String EXTRA_IMAGE2 = "image2";
    float threshold = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityFaceComparisonBinding viewBinding = ActivityFaceComparisonBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        float score = getIntent().getFloatExtra(EXTRA_SCORE, 0f);
        if (score >= threshold) {
            NormalDistribution normalDistribution = new NormalDistribution();
            double probability = normalDistribution.cumulativeProbability(score)*100;
            viewBinding.contextLabel.setText(getString(R.string.pass_details, score, probability, threshold));
            viewBinding.resultStatusTextView.setText(R.string.pass);
            int green = Color.rgb(54, 175, 0);
            viewBinding.resultStatusTextView.setTextColor(green);
        } else {
            viewBinding.contextLabel.setText(getString(R.string.warning_details, score, threshold));
            viewBinding.resultStatusTextView.setText(R.string.warning);
            viewBinding.resultStatusTextView.setTextColor(Color.RED);
        }
        ImageView[] imageViews = new ImageView[]{
                viewBinding.face1ImageView,
                viewBinding.face2ImageView
        };
        byte[] image1 = getIntent().getByteArrayExtra(EXTRA_IMAGE1);
        byte[] image2 = getIntent().getByteArrayExtra(EXTRA_IMAGE2);
        if (image1 != null && image2 != null) {
            byte[][] images = new byte[2][0];
            images[0] = image1;
            images[1] = image2;
            for (int i=0; i<images.length; i++) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(images[i], 0, images[i].length);
                RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                drawable.setCornerRadius((float)drawable.getIntrinsicWidth()/8f);
                imageViews[i].setImageDrawable(drawable);
            }
        }
    }
}