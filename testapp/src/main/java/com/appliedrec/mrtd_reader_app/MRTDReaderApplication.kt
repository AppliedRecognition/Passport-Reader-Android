package com.appliedrec.mrtd_reader_app

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.appliedrec.verid.core2.VerID
import com.appliedrec.verid.core2.VerIDFactory
import com.appliedrec.verid.core2.VerIDFactoryDelegate
import com.microblink.blinkid.MicroblinkSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class MRTDReaderApplication : Application() {

    var isMicroblinkEnabled = false
        private set
    var verID: VerID? = null
        private set
    var verIDError: Exception? = null
        private set

    override fun onCreate() {
        super.onCreate()
        with(ProcessLifecycleOwner.get()) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    ResultFileHelper.deleteFiles()
                } catch (ignore: Exception) {}
                try {
                    verID = VerIDFactory(this@MRTDReaderApplication).createVerIDSync()
                } catch (e: Exception) {
                    verIDError = e
                }
                try {
                    val key = downloadMicroblinkLicenceKey()
                    withContext(Dispatchers.Main) {
                        MicroblinkSDK.setLicenseKey(key, this@MRTDReaderApplication)
                        MicroblinkSDK.setShowTrialLicenseWarning(false)
                        isMicroblinkEnabled = true
                        LocalBroadcastManager.getInstance(this@MRTDReaderApplication).sendBroadcast(
                            Intent(
                                INTENT_ACTION_MICROBLINK_ENABLED
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun downloadMicroblinkLicenceKey(): String = coroutineScope {
        val request: Request =
            Request.Builder().url("https://ver-id.s3.amazonaws.com/blinkid-keys/android/$packageName.txt")
                .build()
        val response: Response = OkHttpClient.Builder().build().newCall(request).execute()
        if (response.code < 400 && response.body != null) {
            val key = response.body!!.string()
            if (!key.isEmpty()) {
                return@coroutineScope key
            } else {
                throw Exception("Empty licence key")
            }
        } else {
            throw Exception("Failed to download licence key")
        }
    }

    companion object {
        const val INTENT_ACTION_MICROBLINK_ENABLED = "com.appliedrec.ACTION_MICROBLINK_ENABLED"
    }
}