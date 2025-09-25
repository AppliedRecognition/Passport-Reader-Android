package com.appliedrec.mrtd_reader_app

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.appliedrec.mrtd_reader_app.databinding.ActivityFaceComparisonBinding
import org.apache.commons.math3.distribution.NormalDistribution

class FaceComparisonActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCORE = "score"
        const val EXTRA_IMAGE1 = "image1"
        const val EXTRA_IMAGE2 = "image2"
        const val EXTRA_THRESHOLD = "threshold"
        const val EXTRA_GLASSES_DETECTED = "glasses_detected"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewBinding = ActivityFaceComparisonBinding.inflate(
            layoutInflater
        )
        setContentView(viewBinding.root)
        val score = intent.getFloatExtra(EXTRA_SCORE, 0f)
        val threshold = intent.getFloatExtra(EXTRA_THRESHOLD, 0.6f)
        val glassesDetected = intent.getBooleanExtra(EXTRA_GLASSES_DETECTED, false)
        val warningThreshold = threshold - 0.1f
        if (score >= threshold) {
            viewBinding.contextLabel.text = getString(R.string.pass_details, score, threshold)
            viewBinding.resultStatusTextView.setText(R.string.pass)
            val green = Color.rgb(54, 175, 0)
            viewBinding.resultStatusTextView.setTextColor(green)
        } else if (score >= warningThreshold) {
            @StringRes val resId: Int = if (glassesDetected) R.string.warning_details_glasses else R.string.warning_details
            viewBinding.contextLabel.text = getString(resId, score, threshold)
            viewBinding.resultStatusTextView.setText(R.string.warning)
            viewBinding.resultStatusTextView.setTextColor(Color.rgb(244, 191, 79))
        } else {
            @StringRes val resId: Int = if (glassesDetected) R.string.fail_details_glasses else R.string.fail_details
            viewBinding.contextLabel.text = getString(resId, score, threshold)
            viewBinding.resultStatusTextView.setText(R.string.fail)
            viewBinding.resultStatusTextView.setTextColor(Color.RED)
        }
        val imageViews = arrayOf(
            viewBinding.face1ImageView,
            viewBinding.face2ImageView
        )
        val image1 = intent.getByteArrayExtra(EXTRA_IMAGE1)
        val image2 = intent.getByteArrayExtra(EXTRA_IMAGE2)
        if (image1 != null && image2 != null) {
            val images = Array(2) { ByteArray(0) }
            images[0] = image1
            images[1] = image2
            for (i in images.indices) {
                val bitmap = BitmapFactory.decodeByteArray(images[i], 0, images[i].size)
                val drawable = RoundedBitmapDrawableFactory.create(resources, bitmap)
                drawable.cornerRadius = drawable.intrinsicWidth.toFloat() / 8f
                imageViews[i].setImageDrawable(drawable)
            }
        }
    }
}