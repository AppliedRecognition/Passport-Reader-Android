package com.appliedrec.mrtdreader

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.appliedrec.mrtdreader.databinding.ActivityMrtdreaderBinding
import org.jmrtd.PassportService

/**
 * Machine-Readable Travel Document (MRTD) scan activity
 *
 *
 * The activity will ask the user to place their travel document close to their device and read the document's NFC chip
 * @version 1.0.0
 * @suppress
 */
class MRTDScanActivity: AppCompatActivity() {

    internal var nfcPermissionListener: NFCPermissionListener? = null
    private lateinit var viewBinding: ActivityMrtdreaderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMrtdreaderBinding.inflate(
            layoutInflater
        )
        setContentView(viewBinding.root)
        onWaiting()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_NFC_PERMISSION) {
            nfcPermissionListener?.let { listener: NFCPermissionListener ->
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    listener.onNFCPermissionGranted()
                } else {
                    listener.onNFCPermissionDenied()
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    internal fun onWaiting() {
        viewBinding.textView.setText(R.string.mrtd_reader_title)
        viewBinding.progressBar.visibility = View.GONE
        viewBinding.progressIndicator.visibility = View.GONE
    }

    internal fun onReading() {
        viewBinding.textView.setText(R.string.mrtd_reading_document)
        viewBinding.progressIndicator.visibility = View.VISIBLE
        viewBinding.progressBar.visibility = View.GONE
    }

    internal fun onNoNFC() {
        viewBinding.textView.setText(R.string.mrtd_no_nfc_reader)
        viewBinding.progressBar.visibility = View.GONE
        viewBinding.progressIndicator.visibility = View.GONE
    }

    internal fun onScanProgress(progress: MRTDReaderProgress) {
        val message = when (progress.fileId) {
            PassportService.EF_COM, PassportService.EF_SOD -> getString(R.string.mrtd_evt_bac)
            PassportService.EF_DG1 -> getString(R.string.mrtd_evt_mrz)
            PassportService.EF_DG2 -> getString(R.string.mrtd_evt_photo)
            else -> getString(R.string.mrtd_evt_reading_passport)
        }
        val done = Math.round(progress.progress * 100.0).toInt()
        viewBinding.progressBar.max = 100
        viewBinding.progressBar.progress = done
        viewBinding.progressBar.visibility = View.VISIBLE
        viewBinding.progressIndicator.visibility = View.GONE
        viewBinding.textView.text = message
    }

    companion object {
        const val REQUEST_CODE_NFC_PERMISSION = 0
    }
}