package com.appliedrec.mrtdreader

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.ReaderCallback
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.appliedrec.mrtdreader.MRTDReader.createProgressObservable
import com.appliedrec.mrtdreader.MRTDScanActivity
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Arrays
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * @since 2.0.0
 */
class MRTDScanSession(context: Context, bacSpec: BACSpec, val masterListUri: Uri?=null) : ActivityLifecycleCallbacks,
    NFCPermissionListener, ReaderCallback {
    private val contextRef: WeakReference<Context>

    /**
     * @return Basic Access Control (BAC) spec
     * @since 2.0.0
     */
    val bACSpec: BACSpec
    private val sessionId: Int
    private var listener: MRTDScanSessionListener? = null
    private var mrtdScanActivityRef: WeakReference<MRTDScanActivity>? = null
    private val isStarted = AtomicBoolean(false)
    private var nfcAdapter: NfcAdapter? = null
    private var scanJob: Job? = null

    /**
     * @return Session listener
     * @since 2.0.0
     */
    fun getListener(): Optional<MRTDScanSessionListener> {
        return Optional.ofNullable(listener)
    }

    /**
     * @param listener Session listener
     * @since 2.0.0
     */
    fun setListener(listener: MRTDScanSessionListener?) {
        this.listener = listener
    }

    /**
     * Start session
     * @since 2.0.0
     */
    fun start() {
        if (isStarted.compareAndSet(false, true)) {
            Handler(Looper.getMainLooper()).post {
                context.ifPresent { context: Context ->
                    registerActivityLifecycleListener()
                    val intent = Intent(context, MRTDScanActivity::class.java)
                    intent.putExtra(EXTRA_SESSION_ID, sessionId)
                    if (context !is Activity) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    /**
     * Cancel session
     * @since 2.0.0
     */
    fun cancel() {
        Handler(Looper.getMainLooper()).post {
            scanJob?.cancel()
            scanJob = null
            masterListFuture?.cancel(true)
            scanActivity?.setResult(Activity.RESULT_CANCELED)
            scanActivity?.finish()
        }
    }

    /**
     * @return Context within which the session was created
     * @since 2.0.0
     */
    val context: Optional<Context>
        get() = Optional.ofNullable(contextRef.get())

    private fun registerActivityLifecycleListener() {
        context.ifPresent { context: Context ->
            (context.applicationContext as Application).registerActivityLifecycleCallbacks(
                this
            )
        }
    }

    private fun unregisterActivityLifecycleListener() {
        context.ifPresent { context: Context ->
            (context.applicationContext as Application).unregisterActivityLifecycleCallbacks(
                this
            )
        }
    }

    private fun getScanActivity(activity: Activity): Optional<MRTDScanActivity> {
        return if (activity is MRTDScanActivity && activity.getIntent() != null && activity.intent
                .getIntExtra(EXTRA_SESSION_ID, -1) == sessionId
        ) {
            Optional.of(activity as MRTDScanActivity)
        } else {
            Optional.empty()
        }
    }

    private val scanActivity: MRTDScanActivity?
        get() = if (mrtdScanActivityRef == null) null else mrtdScanActivityRef!!.get()

    //region ActivityLifecycleCallbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        getScanActivity(activity).ifPresent { mrtdScanActivity: MRTDScanActivity ->
            mrtdScanActivityRef = WeakReference(mrtdScanActivity)
            mrtdScanActivity.nfcPermissionListener = this
        }
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {
        getScanActivity(activity).ifPresent { mrtdScanActivity: MRTDScanActivity ->
            startReadingNFCTags(
                mrtdScanActivity
            )
        }
    }

    override fun onActivityPaused(activity: Activity) {
        getScanActivity(activity).ifPresent { mrtdScanActivity: MRTDScanActivity ->
            stopReadingNFCTags(
                mrtdScanActivity
            )
        }
    }

    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        getScanActivity(activity).ifPresent { mrtdScanActivity: MRTDScanActivity ->
            mrtdScanActivityRef = null
            if (mrtdScanActivity.isFinishing) {
                scanJob?.cancel()
                scanJob = null
                unregisterActivityLifecycleListener()
                getListener().ifPresent { listener: MRTDScanSessionListener ->
                    listener.onMRTDScanCancelled(
                        bACSpec
                    )
                }
                isStarted.set(false)
            }
        }
    }

    //region NFC permission listener
    override fun onNFCPermissionGranted() {
        scanActivity?.let { mrtdScanActivity: MRTDScanActivity ->
            startReadingNFCTags(
                mrtdScanActivity
            )
        }
    }

    override fun onNFCPermissionDenied() {
        getListener().ifPresent { listener: MRTDScanSessionListener ->
            listener.onMRTDScanFailed(
                bACSpec, Exception("NFC reader permission denied")
            )
        }
    }

    //endregion
    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED == action) {
                val state = intent.getIntExtra(
                    NfcAdapter.EXTRA_ADAPTER_STATE,
                    NfcAdapter.STATE_OFF
                )
                when (state) {
                    NfcAdapter.STATE_TURNING_OFF, NfcAdapter.STATE_OFF -> scanActivity?.onNoNFC()
                    NfcAdapter.STATE_TURNING_ON, NfcAdapter.STATE_ON -> scanActivity?.onWaiting()
                }
            }
        }
    }

    private val masterListFuture: CompletableFuture<Collection<X509Certificate>>?

    /**
     * Session constructor
     * @param context
     * @param bacSpec
     * @since 2.0.0
     */
    init {
        contextRef = WeakReference(context)
        bACSpec = bacSpec
        sessionId = SESSION_ID.getAndIncrement()
        if (masterListUri != null) {
            masterListFuture = loadMasterList(context, masterListUri)
        } else {
            masterListFuture = null
        }
    }

    private fun startReadingNFCTags(mrtdScanActivity: MRTDScanActivity) {
        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        mrtdScanActivity.registerReceiver(broadcastReceiver, filter)
        if (ContextCompat.checkSelfPermission(
                mrtdScanActivity,
                Manifest.permission.NFC
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (nfcAdapter == null) {
                nfcAdapter = NfcAdapter.getDefaultAdapter(mrtdScanActivity)
            }
            if (nfcAdapter != null && nfcAdapter!!.isEnabled) {
                val options = Bundle()
                options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 50)
                nfcAdapter!!.enableReaderMode(
                    mrtdScanActivity,
                    this,
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
                    options
                )
            } else {
                getListener().ifPresent { listener: MRTDScanSessionListener ->
                    listener.onMRTDScanFailed(
                        bACSpec, Exception("NFC adapter unavailable or disabled")
                    )
                }
                listener = null
                scanActivity?.finish()
            }
        } else {
            ActivityCompat.requestPermissions(
                mrtdScanActivity,
                arrayOf(Manifest.permission.NFC),
                MRTDScanActivity.REQUEST_CODE_NFC_PERMISSION
            )
        }
    }

    private fun stopReadingNFCTags(mrtdScanActivity: MRTDScanActivity) {
        mrtdScanActivity.unregisterReceiver(broadcastReceiver)
        if (nfcAdapter != null && nfcAdapter!!.isEnabled) {
            nfcAdapter!!.disableReaderMode(mrtdScanActivity)
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        if (Arrays.asList(*tag.techList).contains("android.nfc.tech.IsoDep")) {
            val isoDep = IsoDep.get(tag)
            isoDep.timeout = 1000
            scanJob = scanActivity?.lifecycleScope?.launch(Dispatchers.IO) {
                try {
                    val result = MRTDReader.readPassport(isoDep, bACSpec, { progress ->
                        scanActivity?.onScanProgress(progress)
                    }, masterListFuture)
                    withContext(Dispatchers.Main) {
                        unregisterActivityLifecycleListener()
                        scanActivity?.finish()
                        listener?.onMRTDScanSucceeded(bACSpec, result)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        unregisterActivityLifecycleListener()
                        scanActivity?.finish()
                        listener?.onMRTDScanFailed(bACSpec, e)
                    }
                } finally {
                    listener = null
                }
            }
        }
    }

    private fun loadMasterList(context: Context, uri: Uri): CompletableFuture<Collection<X509Certificate>> {
        return CompletableFuture.supplyAsync {
            context.contentResolver.openInputStream(uri).use { inputStream ->
                val certs = CertificateFactory.getInstance("X.509").generateCertificates(inputStream)
                return@supplyAsync certs.filter { it is X509Certificate }.map { it as X509Certificate }
            }
        }
    }

    companion object {
        private const val EXTRA_SESSION_ID = "com.appliedrec.mrtdreader.EXTRA_SESSION_ID"
        private val SESSION_ID = AtomicInteger(0)
    }
}