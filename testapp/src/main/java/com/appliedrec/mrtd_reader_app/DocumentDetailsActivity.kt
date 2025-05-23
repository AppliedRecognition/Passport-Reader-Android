package com.appliedrec.mrtd_reader_app

import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.appliedrec.mrtd_reader_app.databinding.ActivityDocumentDetailsBinding
import com.appliedrec.mrtdreader.MRTDScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DocumentDetailsActivity : AppCompatActivity() {

    var scanResult: MRTDScanResult.Success? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewBinding = ActivityDocumentDetailsBinding.inflate(
            layoutInflater
        )
        setContentView(viewBinding.root)
        val uri: Uri = intent?.data ?: run {
            finish()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            scanResult = ResultFileHelper.readScanResult(uri)
            withContext(Dispatchers.Main) {
                viewBinding.documentNumber.text = scanResult!!.documentNumber
                viewBinding.personalNumber.text = scanResult!!.personalNumber
                viewBinding.documentCode.text = scanResult!!.documentCode
                viewBinding.issuingState.text = scanResult!!.issuingState
                viewBinding.dateOfExpiry.text = scanResult!!.dateOfExpiry
                viewBinding.primaryIdentifier.text = scanResult!!.primaryIdentifier
                viewBinding.secondaryIdentifiers.text =
                    TextUtils.join(", ", scanResult!!.secondaryIdentifiers)
                viewBinding.nationality.text = scanResult!!.nationality
                viewBinding.gender.text = scanResult!!.gender
                viewBinding.dateOfBirth.text = scanResult!!.dateOfBirth
                if (scanResult!!.faceImage != null) {
                    viewBinding.imageView.setImageBitmap(scanResult!!.faceImage)
                } else {
                    viewBinding.imageView.visibility = View.GONE
                }
                viewBinding.signatureVerified.text = if (scanResult!!.signatureVerified) {
                    getString(R.string.yes)
                } else {
                    getString(R.string.no)
                }
                viewBinding.certificateVerified.text = if (scanResult!!.issuerVerified) {
                    getString(R.string.yes)
                } else {
                    getString(R.string.no)
                }
            }
        }
    }
}