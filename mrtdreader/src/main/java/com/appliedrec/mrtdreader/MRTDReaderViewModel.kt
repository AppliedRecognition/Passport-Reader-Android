package com.appliedrec.mrtdreader

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.nfc.tech.IsoDep
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import jj2000.JJ2000Frontend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.sf.scuba.smartcards.CardService
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jmrtd.BACKey
import org.jmrtd.PACEKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardSecurityFile
import org.jmrtd.lds.LDSFileUtil
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.iso19794.FaceImageInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.Security
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean

internal class MRTDReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val resultFlow = MutableStateFlow<MRTDReaderUiState>(MRTDReaderUiState.Idle)
    private val isReading = AtomicBoolean(false)
    private var readJob: Job? = null

    val result: StateFlow<MRTDReaderUiState> = resultFlow.asStateFlow()

    fun readPassport(isoDep: IsoDep, bacSpec: BACSpec, masterListLocation: MasterListLocation = MasterListLocation.None) {
        if (!isReading.compareAndSet(false, true)) {
            return
        }
        resultFlow.update {
            MRTDReaderUiState.Reading(
                0.0,
                application.getString(R.string.mrtd_evt_reading_passport)
            )
        }
        readJob = viewModelScope.launch(Dispatchers.IO) {
            var passportService: PassportService? = null
            try {
                isoDep.connect()
                isoDep.timeout = 5000
                isoDep.use {
                    val bacKey =
                        BACKey(bacSpec.documentNumber, bacSpec.dateOfBirth, bacSpec.dateOfExpiry)
                    Security.addProvider(BouncyCastleProvider())
                    val cardService = CardService.getInstance(isoDep)
                    passportService = PassportService(
                        cardService,
                        PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                        PassportService.DEFAULT_MAX_BLOCKSIZE,
                        true,
                        false
                    )
                    resultFlow.update {
                        MRTDReaderUiState.Reading(
                            0.2,
                            application.getString(R.string.mrtd_evt_bac)
                        )
                    }
                    passportService!!.open()
                    val paceSucceeded = doPACE(passportService!!, bacKey)
                    passportService!!.sendSelectApplet(paceSucceeded)
                    if (!paceSucceeded) {
                        tryWithRetry {
                            passportService!!.doBAC(bacKey)
                        }
                    }
                    val sodFile: SODFile = tryWithRetry {
                        readFile(passportService!!, PassportService.EF_SOD)
                    }
                    resultFlow.update {
                        MRTDReaderUiState.Reading(
                            0.4,
                            application.getString(R.string.mrtd_evt_mrz)
                        )
                    }
                    val dG1File: DG1File = tryWithRetry {
                        readFile(passportService!!, PassportService.EF_DG1)
                    }
                    val result = MRTDScanResult.Success(
                        documentCode = dG1File.mrzInfo.documentCode,
                        issuingState = dG1File.mrzInfo.issuingState,
                        primaryIdentifier = dG1File.mrzInfo.primaryIdentifier,
                        secondaryIdentifiers = dG1File.mrzInfo.secondaryIdentifierComponents,
                        nationality = dG1File.mrzInfo.nationality,
                        documentNumber = dG1File.mrzInfo.documentNumber,
                        personalNumber = dG1File.mrzInfo.personalNumber,
                        dateOfBirth = dG1File.mrzInfo.dateOfBirth,
                        dateOfExpiry = dG1File.mrzInfo.dateOfExpiry,
                        gender = dG1File.mrzInfo.genderCode.name,
                        faceImage = null
                    )
                    result.dateOfBirth = dG1File.mrzInfo.dateOfBirth
                    result.dateOfExpiry = dG1File.mrzInfo.dateOfExpiry
                    result.documentNumber = dG1File.mrzInfo.documentNumber
                    result.documentCode = dG1File.mrzInfo.documentCode
                    result.gender = dG1File.mrzInfo.gender.name
                    result.issuingState = dG1File.mrzInfo.issuingState
                    result.nationality = dG1File.mrzInfo.nationality
                    result.personalNumber = dG1File.mrzInfo.personalNumber
                    result.primaryIdentifier = dG1File.mrzInfo.primaryIdentifier
                    result.secondaryIdentifiers = dG1File.mrzInfo.secondaryIdentifierComponents
                    resultFlow.update {
                        MRTDReaderUiState.Reading(
                            0.6,
                            application.getString(R.string.mrtd_evt_photo)
                        )
                    }
                    val dG2File: DG2File = tryWithRetry {
                        readFile(passportService!!, PassportService.EF_DG2)
                    }
                    val faceImageInfos = mutableListOf<FaceImageInfo>()
                    dG2File.faceInfos.forEach { faceInfo ->
                        faceImageInfos.addAll(faceInfo.faceImageInfos)
                    }
                    if (faceImageInfos.isNotEmpty()) {
                        var faceImage: Bitmap?
                        for (faceImageInfo in faceImageInfos) {
                            val imageSize = faceImageInfo.imageLength.toDouble()
                            faceImageInfo.imageInputStream.use { inputStream ->
                                ByteArrayOutputStream().use { outputStream ->
                                    var read: Int
                                    val buffer = ByteArray(1024)
                                    var totalRead = 0.0
                                    while (inputStream.read(buffer).also { read = it } > 0) {
                                        outputStream.write(buffer, 0, read)
                                        totalRead += read.toDouble()
                                        val completed = totalRead / imageSize * 0.2
                                        resultFlow.update {
                                            MRTDReaderUiState.Reading(
                                                0.6 + completed,
                                                application.getString(R.string.mrtd_evt_photo)
                                            )
                                        }
                                    }
                                    outputStream.flush()
                                    val imageData = outputStream.toByteArray()
                                    faceImage = JJ2000Frontend.decode(imageData)
                                    if (faceImage == null) {
                                        faceImage =
                                            BitmapFactory.decodeByteArray(
                                                imageData,
                                                0,
                                                imageData.size
                                            )
                                    }
                                }
                            }
                            if (faceImage != null) {
                                result.faceImage = faceImage
                                break
                            }
                        }
                    }
                    resultFlow.update {
                        MRTDReaderUiState.Reading(
                            0.8,
                            application.getString(R.string.mrtd_evt_signature)
                        )
                    }
                    result.signatureVerified = checkDocumentSignature(sodFile)
                    resultFlow.update {
                        MRTDReaderUiState.Reading(
                            0.9,
                            application.getString(R.string.mrtd_evt_signature)
                        )
                    }
                    try {
                        val masterList: List<X509Certificate> = when (masterListLocation) {
                            is MasterListLocation.Url -> {
                                masterListLocation.context.contentResolver
                                    .openInputStream(masterListLocation.uri).use { inputStream ->
                                        CertificateFactory.getInstance("X.509")
                                            .generateCertificates(inputStream)
                                            .filterIsInstance<X509Certificate>()
                                    }
                            }

                            is MasterListLocation.Assets -> {
                                masterListLocation.context.assets.open(masterListLocation.assetName)
                                    .use {
                                        CertificateFactory.getInstance("X.509")
                                            .generateCertificates(it)
                                    }
                                    .filterIsInstance<X509Certificate>()
                            }

                            MasterListLocation.None -> emptyList()
                        }
                        result.issuerVerified =
                            verifySigningCertificate(
                                sodFile.docSigningCertificate,
                                masterList
                            )
                    } catch (e: CancellationException) {
                        return@launch
                    } catch (e: Exception) {
                        result.issuerVerified = false
                    }
                    resultFlow.update { MRTDReaderUiState.Finished(result) }
                }
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> {
                        resultFlow.update { MRTDReaderUiState.Finished(MRTDScanResult.Cancelled) }
                        throw e
                    }
                    else -> {
                        if (e.message?.lowercase()?.contains("permission denial") == true ||
                            e.message?.lowercase()?.contains("is out of date") == true
                        ) {
                            resultFlow.update { MRTDReaderUiState.Warning(application.getString(R.string.tag_lost)) }
                        } else {
                            resultFlow.update { MRTDReaderUiState.Finished(MRTDScanResult.Failure(e)) }
                        }
                    }
                }
            } finally {
                try {
                    passportService?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                isReading.set(false)
            }
        }
    }

    fun cancelReading() {
        readJob?.cancel()
    }

    private suspend fun <T> tryWithRetry(
        maxRetries: Int = 2,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(maxRetries) {
            try {
                return block()
            } catch (e: IOException) {
                lastException = e
                delay(100)
            }
        }
        throw lastException ?: RuntimeException("Unknown error")
    }

    private fun checkDocumentSignature(sodFile: SODFile): Boolean {
        try {
            var digestEncryptionAlgorithm = sodFile.digestEncryptionAlgorithm
            if (digestEncryptionAlgorithm == null) {
                return false
            }
            if (digestEncryptionAlgorithm == "SSAwithRSA/PSS") {
                digestEncryptionAlgorithm = sodFile.signerInfoDigestAlgorithm.replace("-", "") + "withRSA/PSS"
            }
            if (digestEncryptionAlgorithm == "RSA") {
                digestEncryptionAlgorithm = sodFile.signerInfoDigestAlgorithm.replace("-", "") + "withRSA"
            }
            var signature: Signature
            try {
                signature = Signature.getInstance(digestEncryptionAlgorithm)
            } catch (e: Exception) {
                signature = Signature.getInstance(digestEncryptionAlgorithm, "BC")
            }
            signature.initVerify(sodFile.docSigningCertificate)
            signature.update(sodFile.eContent)
            return signature.verify(sodFile.encryptedDigest)
        } catch (e: Exception) {
            return false
        }
    }

    private fun <T> readFile(passportService: PassportService, id: Short): T {
        passportService.getInputStream(id, PassportService.DEFAULT_MAX_BLOCKSIZE).use { inputStream ->
            return LDSFileUtil.getLDSFile(id, inputStream) as T
        }
    }

    private fun doPACE(passportService: PassportService, bacKey: BACKey): Boolean {
        try {
            passportService.getInputStream(
                PassportService.EF_CARD_SECURITY,
                PassportService.DEFAULT_MAX_BLOCKSIZE
            ).use { inputStream ->
                val cardAccessFile = CardSecurityFile(inputStream)
                val securityInfo =
                    cardAccessFile.securityInfos.firstOrNull { it is PACEInfo } as? PACEInfo
                if (securityInfo != null) {
                    passportService.doPACE(
                        PACEKeySpec.createMRZKey(bacKey),
                        securityInfo.objectIdentifier,
                        PACEInfo.toParameterSpec(securityInfo.parameterId),
                        null
                    )
                    return true
                }
            }
        } catch (ignore: Exception) {}
        return false
    }

    private fun verifySigningCertificate(certificate: X509Certificate, masterList: Collection<X509Certificate>): Boolean {
        val issuers = masterList.filter { it.subjectX500Principal == certificate.issuerX500Principal }
        for (issuer in issuers) {
            try {
                certificate.verify(issuer.publicKey)
                return true
            } catch (ignore: Exception) {
            }
        }
        return false
    }
}

internal sealed class MRTDReaderUiState {
    data object Idle : MRTDReaderUiState()
    data class Reading(val progress: Double, val message: String) : MRTDReaderUiState()
    data class Warning(val message: String) : MRTDReaderUiState()
    data class Finished(val result: MRTDScanResult) : MRTDReaderUiState()
}

internal sealed class MasterListLocation {
    data object None : MasterListLocation()
    data class Url(val context: Context, val uri: Uri) : MasterListLocation()
    data class Assets(val context: Context, val assetName: String) : MasterListLocation()
}