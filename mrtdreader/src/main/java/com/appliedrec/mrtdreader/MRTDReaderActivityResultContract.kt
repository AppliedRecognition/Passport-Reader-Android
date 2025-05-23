package com.appliedrec.mrtdreader

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class MRTDReaderActivityResultContract : ActivityResultContract<MRTDScanSettings, MRTDScanResult>() {

    override fun createIntent(context: Context, input: MRTDScanSettings): Intent {
        return Intent(context, MRTDScanActivity::class.java).apply {
            putExtra(MRTDScanActivity.EXTRA_BAC_SPEC, input.bacSpec)
            input.masterListUri?.let { uri ->
                putExtra(MRTDScanActivity.EXTRA_MASTERLIST_URI, uri.toString())
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): MRTDScanResult {
        if (resultCode == Activity.RESULT_OK) {
            val resultId = intent?.getIntExtra(MRTDScanActivity.EXTRA_RESULT_ID, -1) ?: -1
            return MRTDScanResultStore.removeResult(resultId) ?: MRTDScanResult.Failure(Exception("Result not found"))
        } else {
            return MRTDScanResult.Cancelled
        }
    }
}