package com.appliedrec.mrtdreader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.appliedrec.mrtdreader.databinding.ActivityMrtdreaderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Machine-Readable Travel Document (MRTD) scan activity
 *
 *
 * The activity will ask the user to place their travel document close to their device and read the document's NFC chip
 * @version 1.0.0
 * @suppress
 */
class MRTDScanActivity: AppCompatActivity(), NfcAdapter.ReaderCallback {

    private lateinit var viewBinding: ActivityMrtdreaderBinding
    private var nfcAdapter: NfcAdapter? = null
    private val isReadingTag = AtomicBoolean(false)
    private lateinit var bacSpec: BACSpec
    private lateinit var masterListFuture: CompletableFuture<Collection<X509Certificate>>

    companion object {
        const val EXTRA_RESULT_ID = "com.appliedrec.mrtdreader.EXTRA_RESULT_ID"
        const val EXTRA_BAC_SPEC = "com.appliedrec.mrtdreader.EXTRA_SESSION_ID"
        const val EXTRA_MASTERLIST_URI = "com.appliedrec.mrtdreader.EXTRA_MASTERLIST_URI"
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED == action) {
                val state = intent.getIntExtra(
                    NfcAdapter.EXTRA_ADAPTER_STATE,
                    NfcAdapter.STATE_OFF
                )
                when (state) {
                    NfcAdapter.STATE_TURNING_OFF, NfcAdapter.STATE_OFF -> onNFCDisabled()
                    NfcAdapter.STATE_TURNING_ON, NfcAdapter.STATE_ON -> onWaiting()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMrtdreaderBinding.inflate(
            layoutInflater
        )
        setContentView(viewBinding.root)
        lockCurrentOrientation()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val bac = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_BAC_SPEC, BACSpec::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_BAC_SPEC)
        }
        bacSpec = bac ?: run {
            finish()
            return@onCreate
        }
        masterListFuture = intent.getStringExtra(EXTRA_MASTERLIST_URI)?.let { urlString ->
            loadMasterList(this, Uri.parse(urlString))
        } ?: loadMasterListFromAssets(this, "masterlist.pem")
        if (nfcAdapter == null) {
            onNoNFC()
        } else {
            onWaiting()
        }
    }

    private fun lockCurrentOrientation() {
        val rotation = windowManager.defaultDisplay.rotation
        val orientation = resources.configuration.orientation
        requestedOrientation = when {
            // Natural portrait
            orientation == Configuration.ORIENTATION_PORTRAIT && (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            // Natural landscape
            orientation == Configuration.ORIENTATION_LANDSCAPE && (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            // Reverse portrait
            orientation == Configuration.ORIENTATION_PORTRAIT && (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            // Reverse landscape
            orientation == Configuration.ORIENTATION_LANDSCAPE && (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.let { adapter ->
            val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            registerReceiver(broadcastReceiver, filter)
            if (!adapter.isEnabled) {
                onNFCDisabled()
            } else {
                val options = Bundle().apply {
                    putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 50)
                }
                adapter.enableReaderMode(
                    this,
                    this,
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
                    options
                )
            }
        } ?: return
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.let { adapter ->
            unregisterReceiver(broadcastReceiver)
            if (adapter.isEnabled) {
                adapter.disableReaderMode(this)
            }
        }
    }

    private fun onWaiting() {
        viewBinding.textView.setText(R.string.mrtd_reader_title)
        viewBinding.progressBar.visibility = View.GONE
        viewBinding.progressIndicator.visibility = View.GONE
    }

    private fun onReading() {
        viewBinding.textView.setText(R.string.mrtd_reading_document)
        viewBinding.progressIndicator.visibility = View.VISIBLE
        viewBinding.progressBar.visibility = View.GONE
    }

    private fun onNoNFC() {
        viewBinding.textView.setText(R.string.mrtd_no_nfc_reader)
        viewBinding.progressBar.visibility = View.GONE
        viewBinding.progressIndicator.visibility = View.GONE
    }

    private fun onNFCDisabled() {
        viewBinding.textView.setText(R.string.mrtd_nfc_reader_disabled)
        viewBinding.progressBar.visibility = View.GONE
        viewBinding.progressIndicator.visibility = View.GONE
    }

    private fun onScanProgress(progress: Progress) {
        val done = Math.round(progress.completed * 100.0).toInt()
        viewBinding.progressBar.max = 100
        viewBinding.progressBar.progress = done
        viewBinding.progressBar.visibility = View.VISIBLE
        viewBinding.progressIndicator.visibility = View.GONE
        viewBinding.textView.text = progress.message
    }

    private fun loadMasterList(context: Context, uri: Uri): CompletableFuture<Collection<X509Certificate>> {
        return CompletableFuture.supplyAsync {
            context.contentResolver.openInputStream(uri).use { inputStream ->
                val certs = CertificateFactory.getInstance("X.509").generateCertificates(inputStream)
                return@supplyAsync certs.filterIsInstance<X509Certificate>()
            }
        }
    }

    private fun loadMasterListFromAssets(context: Context, assetName: String): CompletableFuture<Collection<X509Certificate>> {
        return CompletableFuture.supplyAsync {
            val certs = context.assets.open(assetName).use { CertificateFactory.getInstance("X.509").generateCertificates(it) }
            return@supplyAsync certs.filterIsInstance<X509Certificate>()
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        if (listOf(*tag.techList).contains("android.nfc.tech.IsoDep")) {
            val isoDep = IsoDep.get(tag)
            isoDep.timeout = 1000
            if (isReadingTag.compareAndSet(false, true)) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val result = MRTDReader.readPassport(isoDep, bacSpec, { progress ->
                            onScanProgress(progress)
                        }, masterListFuture)
                        val resultId = MRTDScanResultStore.addResult(result)
                        withContext(Dispatchers.Main) {
                            setResult(RESULT_OK, Intent().apply {
                                putExtra(EXTRA_RESULT_ID, resultId)
                            })
                            finish()
                        }
                    } catch (e: Exception) {
                        val resultId = MRTDScanResultStore.addResult(MRTDScanResult.Failure(e))
                        withContext(Dispatchers.Main) {
                            setResult(RESULT_OK, Intent().apply {
                                putExtra(EXTRA_RESULT_ID, resultId)
                            })
                            finish()
                        }
                    } finally {
                        isReadingTag.set(false)
                    }
                }
            }
        }
    }
}