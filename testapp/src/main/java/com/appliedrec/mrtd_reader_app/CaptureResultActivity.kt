package com.appliedrec.mrtd_reader_app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.net.toFile
import androidx.core.util.Pair
import androidx.lifecycle.lifecycleScope
import com.appliedrec.mrtd_reader_app.databinding.ActivityCaptureResultBinding
import com.appliedrec.mrtdreader.MRTDScanResult
import com.appliedrec.verid.core2.Bearing
import com.appliedrec.verid.core2.Face
import com.appliedrec.verid.core2.IRecognizable
import com.appliedrec.verid.core2.Image
import com.appliedrec.verid.core2.RecognizableFace
import com.appliedrec.verid.core2.VerID
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings
import com.appliedrec.verid.core2.session.VerIDSessionResult
import com.appliedrec.verid.ui2.IVerIDSession
import com.appliedrec.verid.ui2.VerIDSession
import com.appliedrec.verid.ui2.VerIDSessionDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.StringJoiner

class CaptureResultActivity : AppCompatActivity(), VerIDSessionDelegate {

    private var scanResult: MRTDScanResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            val verID = (application as MRTDReaderApplication).verID ?: throw Exception("Ver-ID not initialized")
            val settings = LivenessDetectionSessionSettings()
            val verIDSession = VerIDSession(verID, settings)
            verIDSession.setDelegate(this)
            verIDSession.start()
        } catch (exception: Exception) {
            showError(R.string.failed_to_start_face_capture_session)
        }
    }

    override fun onSessionFinished(session: IVerIDSession<*>, result: VerIDSessionResult) {
        if (result.error.isPresent) {
            showError(R.string.face_capture_failed)
            return
        }
        try {
            startActivity(createFaceComparisonIntent(session.verID, result))
        } catch (exception: Exception) {
            showError(R.string.face_comparison_failed)
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
    private fun createFaceComparisonIntent(verID: VerID, result: VerIDSessionResult): Intent {
        val facePair = detectFaceInImage(
            scanResult!!.faceImage
        )
        val documentFaceImage = cropImageToFace(scanResult!!.faceImage, facePair.first)
        if (!result.getFirstFaceCapture(Bearing.STRAIGHT).isPresent) {
            throw Exception("Face not present in result")
        }
        val faceCapture = result.getFirstFaceCapture(Bearing.STRAIGHT).get()
        val score = verID.faceRecognition.compareSubjectFacesToFaces(
            arrayOf(facePair.second),
            arrayOf<IRecognizable>(faceCapture.face)
        )
        val documentFaceJpeg = compressImage(documentFaceImage)
        val liveFaceJpeg = compressImage(faceCapture.faceImage)
        val intent = Intent(this, FaceComparisonActivity::class.java)
        intent.putExtra(FaceComparisonActivity.EXTRA_IMAGE1, documentFaceJpeg)
        intent.putExtra(FaceComparisonActivity.EXTRA_IMAGE2, liveFaceJpeg)
        intent.putExtra(FaceComparisonActivity.EXTRA_SCORE, score)
        return intent
    }

    @Throws(IOException::class)
    private fun compressImage(image: Bitmap): ByteArray {
        ByteArrayOutputStream().use { outputStream ->
            image.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            outputStream.flush()
            return outputStream.toByteArray()
        }
    }

    @Throws(Exception::class)
    private fun detectFaceInImage(image: Bitmap?): Pair<Face, IRecognizable> {
        val verID = (application as MRTDReaderApplication).verID ?: throw Exception("Ver-ID not initialized")
        val verIDImage = Image(image)
        val faces = verID.faceDetection.detectFacesInImage(verIDImage, 1, 0)
        if (faces.size == 0) {
            throw Exception("Face not detected in image")
        }
        val recognizables: Array<out RecognizableFace> =
            verID.faceRecognition.createRecognizableFacesFromFaces(faces, verIDImage)
        return Pair(faces[0], recognizables[0])
    }

    @SuppressLint("CheckResult")
    private fun cropImageToFace(image: Bitmap?, face: Face): Bitmap {
        val bounds = Rect()
        face.bounds.round(bounds)
        bounds.intersect(0, 0, image!!.width, image.height)
        return Bitmap.createBitmap(image, bounds.left, bounds.top, bounds.width(), bounds.height())
    }
}