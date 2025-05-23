package com.appliedrec.mrtd_reader_app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.appliedrec.mrtd_reader_app.BACSpecModel
import com.appliedrec.mrtd_reader_app.databinding.ActivityMainBinding
import com.appliedrec.mrtdreader.BACSpec
import com.appliedrec.mrtdreader.MRTDReaderActivityResultContract
import com.appliedrec.mrtdreader.MRTDScanResult
import com.appliedrec.mrtdreader.MRTDScanSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), BACEntryFragment.Listener {
    private var viewModel: BACSpecModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewBinding = ActivityMainBinding.inflate(
            layoutInflater
        )
        setContentView(viewBinding.root)
        viewModel = ViewModelProvider(this).get(BACSpecModel::class.java)
        viewModel!!.setSharedPreferences(PreferenceManager.getDefaultSharedPreferences(this))
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().add(
                viewBinding.container.id, BACEntryFragment.newInstance(
                    viewModel!!.liveData.value
                )
            ).commit()
        }
    }

    private fun onMRTDScanSucceeded(result: MRTDScanResult.Success) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uri = ResultFileHelper.saveScanResult(result)
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@MainActivity, CaptureResultActivity::class.java)
                    intent.data = uri
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val builder = AlertDialog.Builder(this@MainActivity)
                    builder.setTitle(R.string.failed_to_save_scan_result)
                    builder.setMessage(e.toString())
                    builder.setNeutralButton(android.R.string.ok, null)
                    builder.create().show()
                }
            }
        }
    }

    private fun onMRTDScanFailed(throwable: Throwable) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.failed_to_read_travel_document)
        builder.setMessage(throwable.toString())
        builder.setNeutralButton(android.R.string.ok, null)
        builder.create().show()
    }

    private val launcher = registerForActivityResult(MRTDReaderActivityResultContract()) { result ->
        when (result) {
            is MRTDScanResult.Success -> onMRTDScanSucceeded(result)
            is MRTDScanResult.Failure -> onMRTDScanFailed(result.error)
            else -> {}
        }
    }

    override fun onRequestCapture(bacSpec: BACSpec) {
        launcher.launch(MRTDScanSettings(bacSpec))
    }
}