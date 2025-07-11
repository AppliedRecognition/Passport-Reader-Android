package com.appliedrec.mrtdreader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appliedrec.mrtdreader.ui.theme.MRTDReaderTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Machine-Readable Travel Document (MRTD) scan activity
 *
 *
 * The activity will ask the user to place their travel document close to their device and read the document's NFC chip
 * @version 1.0.0
 * @suppress
 */
class MRTDScanActivity: ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var bacSpec: BACSpec
    private var masterListLocation: MasterListLocation = MasterListLocation.None
    private val mrtdReaderViewModel: MRTDReaderViewModel by viewModels()
    private val nfcAdapterState = MutableStateFlow(NfcAdapterState.UNKNOWN)

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
                    NfcAdapter.STATE_TURNING_OFF, NfcAdapter.STATE_OFF -> nfcAdapterState.update { NfcAdapterState.UNAVAILABLE }
                    NfcAdapter.STATE_TURNING_ON, NfcAdapter.STATE_ON -> nfcAdapterState.update { NfcAdapterState.UNKNOWN }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        setContent {
            MRTDReaderTheme {
                val nfcState by nfcAdapterState.collectAsStateWithLifecycle()
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (nfcState) {
                        NfcAdapterState.DISABLED -> {
                            MessageView(stringResource(R.string.mrtd_nfc_reader_disabled))
                        }

                        NfcAdapterState.UNAVAILABLE -> {
                            MessageView(stringResource(R.string.mrtd_no_nfc_reader))
                        }

                        else -> {
                            val resultState by mrtdReaderViewModel.result.collectAsStateWithLifecycle()
                            LaunchedEffect(resultState) {
                                if (resultState is MRTDReaderUiState.Finished) {
                                    if ((resultState as MRTDReaderUiState.Finished).result is MRTDScanResult.Cancelled) {
                                        setResult(RESULT_CANCELED)
                                    } else {
                                        val resultId =
                                            MRTDScanResultStore.addResult((resultState as MRTDReaderUiState.Finished).result)
                                        setResult(RESULT_OK, Intent().apply {
                                            putExtra(EXTRA_RESULT_ID, resultId)
                                        })
                                    }
                                    finish()
                                }
                            }
                            MRTDReaderView(resultState)
                        }
                    }
                }
            }
        }
        masterListLocation = intent.getStringExtra(EXTRA_MASTERLIST_URI)?.let { urlString ->
            try {
                MasterListLocation.Url(this, Uri.parse(urlString))
            } catch (e: Exception) {
                null
            }
        } ?: MasterListLocation.Assets(this, "masterlist.pem")
        if (nfcAdapter == null) {
            nfcAdapterState.update { NfcAdapterState.UNAVAILABLE }
        } else {
            nfcAdapterState.update { NfcAdapterState.UNKNOWN }
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
                nfcAdapterState.update { NfcAdapterState.DISABLED }
            } else {
                nfcAdapterState.update { NfcAdapterState.AVAILABLE }
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
        mrtdReaderViewModel.cancelReading()
        nfcAdapter?.let { adapter ->
            unregisterReceiver(broadcastReceiver)
            if (adapter.isEnabled) {
                adapter.disableReaderMode(this)
            }
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        if (listOf(*tag.techList).contains("android.nfc.tech.IsoDep")) {
            val isoDep = IsoDep.get(tag) ?: return
            mrtdReaderViewModel.readPassport(isoDep, bacSpec, masterListLocation)
        }
    }
}