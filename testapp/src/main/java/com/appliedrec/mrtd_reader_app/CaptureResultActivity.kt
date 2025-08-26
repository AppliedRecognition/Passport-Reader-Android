package com.appliedrec.mrtd_reader_app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.lifecycle.lifecycleScope
import com.appliedrec.mrtd_reader_app.databinding.ActivityCaptureResultBinding
import com.appliedrec.mrtdreader.MRTDScanResult
import com.appliedrec.verid3.common.Bearing
import com.appliedrec.verid3.common.Face
import com.appliedrec.verid3.common.Image
import com.appliedrec.verid3.common.serialization.fromBitmap
import com.appliedrec.verid3.common.serialization.toBitmap
import com.appliedrec.verid3.facecapture.CapturedFace
import com.appliedrec.verid3.facecapture.FaceCapture
import com.appliedrec.verid3.facecapture.FaceCaptureSessionResult
import com.appliedrec.verid3.facecapture.FaceTrackingPlugin
import com.appliedrec.verid3.facecapture.LivenessDetectionPlugin
import com.appliedrec.verid3.facedetection.retinaface.FaceDetectionRetinaFace
import com.appliedrec.verid3.facerecognition.arcface.cloud.FaceRecognitionArcFace
import com.appliedrec.verid3.spoofdevicedetection.cloud.SpoofDeviceDetection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.StringJoiner

class CaptureResultActivity : AppCompatActivity() {

    private var scanResult: MRTDScanResult.Success? = null
    private lateinit var faceDetection: FaceDetectionRetinaFace
    private lateinit var faceRecognition: FaceRecognitionArcFace

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        faceRecognition = FaceRecognitionArcFace(this)
        val viewBinding = ActivityCaptureResultBinding.inflate(
            layoutInflater
        )
        setContentView(viewBinding.root)
        val uri = intent?.data ?: run {
            finish()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                faceDetection = FaceDetectionRetinaFace.create(this@CaptureResultActivity)
                scanResult = ResultFileHelper.readScanResult(uri)
                withContext(Dispatchers.Main) {

                    val stringJoiner = StringJoiner(" ")
                    for (name in scanResult!!.secondaryIdentifiers) {
                        stringJoiner.add(name)
                    }
                    stringJoiner.add(scanResult!!.primaryIdentifier)
                    val name = stringJoiner.toString()
                    viewBinding.nameTextView.text = name
                    val drawable = RoundedBitmapDrawableFactory.create(resources, scanResult!!.faceImage)
                    drawable.cornerRadius = drawable.intrinsicWidth.toFloat() / 8f
                    viewBinding.faceImageView.setImageDrawable(drawable)
                    viewBinding.faceImageView.setOnClickListener { v: View? -> showDocumentDetails() }
                    viewBinding.selfieButton.setOnClickListener { v: View? -> compareToSelfie() }
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            CoroutineScope(Dispatchers.Default).launch {
                faceDetection.close()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.scan_result, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_show_document_details) {
            showDocumentDetails()
            return true
        }
        return false
    }

    private fun showDocumentDetails() {
        val intent = Intent(this, DocumentDetailsActivity::class.java)
        intent.data = this.intent.data
        startActivity(intent)
    }

    private fun compareToSelfie() {
        try {
            val activity = this
            lifecycleScope.launch(Dispatchers.Default) {
                val faceCaptureResult = FaceCapture.captureFaces(activity) {
                    createFaceDetection = { FaceDetectionRetinaFace.create(activity) }
                    createFaceTrackingPlugins = { listOf(
                        LivenessDetectionPlugin(arrayOf(
                            SpoofDeviceDetection(activity)
                        )) as FaceTrackingPlugin<Any>
                    )}
                }
                when (faceCaptureResult) {
                    is FaceCaptureSessionResult.Success -> {
                        val faceCapture = faceCaptureResult.capturedFaces.first { it.bearing == Bearing.STRAIGHT }
                        val intent = createFaceComparisonIntent(faceCapture)
                        withContext(Dispatchers.Main) {
                            startActivity(intent)
                        }
                    }
                    is FaceCaptureSessionResult.Failure -> {
                        withContext(Dispatchers.Main) {
                            showError(R.string.face_comparison_failed)
                        }
                    }
                    else -> {}
                }
            }
        } catch (exception: Exception) {
            showError(R.string.failed_to_start_face_capture_session)
        }
    }

    private fun showError(@StringRes description: Int) {
        AlertDialog.Builder(this)
            .setMessage(description)
            .setPositiveButton(android.R.string.ok, null)
            .create()
            .show()
    }

    @Throws(Exception::class)
    private suspend fun createFaceComparisonIntent(capturedFace: CapturedFace): Intent {
        val documentFaceImage = scanResult?.faceImage?.let { Image.fromBitmap(it) } ?: throw Exception("Missing document face image")
        val documentFace = faceDetection.detectFacesInImage(documentFaceImage, 1).firstOrNull()
            ?: throw Exception("Face not detected in image")
        val documentFaceCroppedImage = cropImageToFace(documentFaceImage.toBitmap(), documentFace)
        val capturedFaceTemplate = faceRecognition.createFaceRecognitionTemplates(
            listOf(capturedFace.face), capturedFace.image
        ).first()
        val documentFaceTemplate = faceRecognition.createFaceRecognitionTemplates(
            listOf(documentFace), documentFaceImage
        ).first()
        val score = faceRecognition.compareFaceRecognitionTemplates(listOf(capturedFaceTemplate), documentFaceTemplate).first()
        val documentFaceJpeg = compressImage(documentFaceCroppedImage)
        val croppedCaptureFaceImage = cropImageToFace(capturedFace.image.toBitmap(), capturedFace.face)
        val liveFaceJpeg = compressImage(croppedCaptureFaceImage)
        val intent = Intent(this, FaceComparisonActivity::class.java)
        intent.putExtra(FaceComparisonActivity.EXTRA_IMAGE1, documentFaceJpeg)
        intent.putExtra(FaceComparisonActivity.EXTRA_IMAGE2, liveFaceJpeg)
        intent.putExtra(FaceComparisonActivity.EXTRA_SCORE, score)
        intent.putExtra(FaceComparisonActivity.EXTRA_THRESHOLD, faceRecognition.defaultThreshold)
        return intent
    }

    @Throws(IOException::class)
    private fun compressImage(image: Bitmap): ByteArray {
        return ByteArrayOutputStream().use { outputStream ->
            image.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            outputStream.flush()
            outputStream.toByteArray()
        }
    }

    @SuppressLint("CheckResult")
    private fun cropImageToFace(image: Bitmap, face: Face): Bitmap {
        val bounds = Rect()
        face.faceAspectRatio = 4/5f
        face.bounds.round(bounds)
        bounds.intersect(0, 0, image.width, image.height)
        return Bitmap.createBitmap(image, bounds.left, bounds.top, bounds.width(), bounds.height())
    }
}