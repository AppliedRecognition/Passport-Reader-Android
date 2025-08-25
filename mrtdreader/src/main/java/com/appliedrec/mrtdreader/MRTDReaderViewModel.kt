package com.appliedrec.mrtdreader

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.nfc.TagLostException
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

    init {
        // Make sure BC is registered once. Re-adding different BCs can cause issues.
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun readPassport(
        isoDep: IsoDep,
        bacSpec: BACSpec,
        masterListLocation: MasterListLocation = MasterListLocation.None
    ) {
        if (!isReading.compareAndSet(false, true)) return

        resultFlow.update {
            MRTDReaderUiState.Reading(
                0.0,
                application.getString(R.string.mrtd_evt_reading_passport)
            )
        }

        readJob = viewModelScope.launch(Dispatchers.IO) {
            var passportService: PassportService? = null
            var cardService: CardService? = null
            try {
                isoDep.connect()
                // Give slow chips ample time, especially during PACE/DG2.
                isoDep.timeout = 20_000

                isoDep.use {
                    val bacKey = BACKey(bacSpec.documentNumber, bacSpec.dateOfBirth, bacSpec.dateOfExpiry)

                    // Determine device capability for extended-length APDUs and pick an optimistic block size
                    val maxTx = try {
                        isoDep.maxTransceiveLength
                    } catch (_: Throwable) {
                        PassportService.NORMAL_MAX_TRANCEIVE_LENGTH
                    }
                    val supportsExtended = maxTx > 261
                    val initialBlockSize = if (supportsExtended) 0xFF else 0xE0

                    cardService = CardService.getInstance(isoDep)
                    passportService = buildPassportService(cardService!!, maxTx, initialBlockSize)

                    resultFlow.update {
                        MRTDReaderUiState.Reading(0.2, application.getString(R.string.mrtd_evt_bac))
                    }

                    passportService!!.open()
                    val paceSucceeded = doPACE(passportService!!, bacKey)
                    passportService!!.sendSelectApplet(paceSucceeded)

                    if (!paceSucceeded) {
                        // Do BAC with a resilient wrapper
                        resilientAPDU(isoDep, passportService!!, paceSucceeded) {
                            passportService!!.doBAC(bacKey)
                        }
                    }

                    // Use block-size fallback for sensitive file reads (SOD, DG1, DG2)
                    val blockCandidates = intArrayOf(
                        initialBlockSize, 0xE0, 0xC0, 0x80, 0x60
                    )

                    val sodFile: SODFile = readFileWithFallback(
                        isoDep = isoDep,
                        cardService = cardService!!,
                        currentService = passportService!!,
                        ef = PassportService.EF_SOD,
                        maxTx = maxTx,
                        paceSucceeded = paceSucceeded,
                        blockSizes = blockCandidates
                    )
                    resultFlow.update {
                        MRTDReaderUiState.Reading(0.4, application.getString(R.string.mrtd_evt_mrz))
                    }

                    val dG1File: DG1File = readFileWithFallback(
                        isoDep = isoDep,
                        cardService = cardService!!,
                        currentService = passportService!!,
                        ef = PassportService.EF_DG1,
                        maxTx = maxTx,
                        paceSucceeded = paceSucceeded,
                        blockSizes = blockCandidates
                    )

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
                    // (These assignments are redundant with the constructor fields, but preserved from your original code)
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
                        MRTDReaderUiState.Reading(0.6, application.getString(R.string.mrtd_evt_photo))
                    }

                    val dG2File: DG2File = readFileWithFallback(
                        isoDep = isoDep,
                        cardService = cardService!!,
                        currentService = passportService!!,
                        ef = PassportService.EF_DG2,
                        maxTx = maxTx,
                        paceSucceeded = paceSucceeded,
                        blockSizes = blockCandidates
                    )

                    val faceImageInfos = mutableListOf<FaceImageInfo>()
                    dG2File.faceInfos.forEach { faceInfo ->
                        faceImageInfos.addAll(faceInfo.faceImageInfos)
                    }

                    if (faceImageInfos.isNotEmpty()) {
                        var faceImage: Bitmap? = null
                        outer@ for (faceImageInfo in faceImageInfos) {
                            val imageSize = faceImageInfo.imageLength.toDouble()
                            faceImageInfo.imageInputStream.use { inputStream ->
                                // Pre-size to reduce GC churn
                                val imageData = ByteArrayOutputStream(faceImageInfo.imageLength).use { outputStream ->
                                    var read: Int
                                    val buffer = ByteArray(512) // smaller chunks = more tolerant
                                    var totalRead = 0.0
                                    while (inputStream.read(buffer).also { read = it } > 0) {
                                        outputStream.write(buffer, 0, read)
                                        totalRead += read.toDouble()
                                        // don't spam UI; progress only up to +0.2
                                        val completed = totalRead / imageSize * 0.2
                                        resultFlow.update {
                                            MRTDReaderUiState.Reading(
                                                0.6 + completed.coerceIn(0.0, 0.2),
                                                application.getString(R.string.mrtd_evt_photo)
                                            )
                                        }
                                    }
                                    outputStream.toByteArray()
                                }

                                faceImage = JJ2000Frontend.decode(imageData)
                                    ?: BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                            }
                            if (faceImage != null) {
                                result.faceImage = faceImage
                                break@outer
                            }
                        }
                    }

                    resultFlow.update {
                        MRTDReaderUiState.Reading(0.8, application.getString(R.string.mrtd_evt_signature))
                    }
                    result.signatureVerified = checkDocumentSignature(sodFile)
                    resultFlow.update {
                        MRTDReaderUiState.Reading(0.9, application.getString(R.string.mrtd_evt_signature))
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
                                masterListLocation.context.assets.open(masterListLocation.assetName).use {
                                    CertificateFactory.getInstance("X.509").generateCertificates(it)
                                }.filterIsInstance<X509Certificate>()
                            }

                            MasterListLocation.None -> emptyList()
                        }
                        result.issuerVerified = verifySigningCertificate(
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

                    is TagLostException -> {
                        // Friendlier UX for common NFC blip
                        resultFlow.update { MRTDReaderUiState.Warning(application.getString(R.string.tag_lost)) }
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

    /**
     * Retry helper with exponential backoff for IO / TagLost.
     */
    private suspend fun <T> tryWithRetry(
        maxRetries: Int = 4,
        initialDelayMs: Long = 250,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var delayMs = initialDelayMs
        var lastException: Exception? = null
        repeat(maxRetries) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is IOException || e is TagLostException) {
                    lastException = e
                    delay(delayMs)
                    delayMs = (delayMs * factor).toLong().coerceAtMost(2_000)
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: RuntimeException("Unknown error")
    }

    /**
     * Wraps a critical APDU operation; if the link blips, reconnect and reselect before one more attempt.
     */
    private suspend fun <T> resilientAPDU(
        isoDep: IsoDep,
        service: PassportService,
        paceSucceeded: Boolean,
        op: suspend () -> T
    ): T {
        return try {
            op()
        } catch (e: Exception) {
            if (e is IOException || e is TagLostException) {
                // Attempt a quick recover: reconnect + reselect + retry once
                if (!isoDep.isConnected) {
                    isoDep.connect()
                    // keep the generous timeout
                    isoDep.timeout = maxOf(isoDep.timeout, 20_000)
                }
                try {
                    service.sendSelectApplet(paceSucceeded)
                } catch (_: Exception) {
                    // If sendSelectApplet fails, we'll still try the op; subsequent calls may reopen/reselect.
                }
                op()
            } else {
                throw e
            }
        }
    }

    /**
     * Reads an EF with progressively smaller block sizes and resilient reconnect/reselect.
     * Rebuilds PassportService as needed because JMRTD doesn't expose a public setMaxBlockSize.
     */
    private suspend fun <T> readFileWithFallback(
        isoDep: IsoDep,
        cardService: CardService,
        currentService: PassportService,
        ef: Short,
        maxTx: Int,
        paceSucceeded: Boolean,
        blockSizes: IntArray
    ): T {
        var lastError: Exception? = null
        var service = currentService
        for ((i, block) in blockSizes.withIndex()) {
            // On first iteration, try with current service; otherwise rebuild with smaller block
            if (i > 0) {
                try {
                    service.close()
                } catch (_: Exception) {}
                service = buildPassportService(cardService, maxTx, block).also {
                    it.open()
                    it.sendSelectApplet(paceSucceeded)
                }
            }
            try {
                return tryWithRetry {
                    resilientAPDU(isoDep, service, paceSucceeded) {
                        readFile(service, ef)
                    }
                }
            } catch (e: Exception) {
                lastError = e
                // fall through to next block size
            }
        }
        throw lastError ?: IOException("Failed to read EF ${"0x%04X".format(ef.toInt())} with all block sizes")
    }

    private fun buildPassportService(
        cardService: CardService,
        maxTransceiveLength: Int,
        maxBlockSize: Int
    ): PassportService {
        return PassportService(
            cardService,
            maxTransceiveLength,
            maxBlockSize,
            /* closeCardService = */ true,
            /* exhaustiveSelect = */ false
        )
    }

    private fun checkDocumentSignature(sodFile: SODFile): Boolean {
        return try {
            var digestEncryptionAlgorithm = sodFile.digestEncryptionAlgorithm ?: return false
            if (digestEncryptionAlgorithm == "SSAwithRSA/PSS") {
                digestEncryptionAlgorithm = sodFile.signerInfoDigestAlgorithm.replace("-", "") + "withRSA/PSS"
            }
            if (digestEncryptionAlgorithm == "RSA") {
                digestEncryptionAlgorithm = sodFile.signerInfoDigestAlgorithm.replace("-", "") + "withRSA"
            }
            val signature: Signature = try {
                Signature.getInstance(digestEncryptionAlgorithm)
            } catch (_: Exception) {
                Signature.getInstance(digestEncryptionAlgorithm, "BC")
            }
            signature.initVerify(sodFile.docSigningCertificate)
            signature.update(sodFile.eContent)
            signature.verify(sodFile.encryptedDigest)
        } catch (_: Exception) {
            false
        }
    }

    private fun <T> readFile(passportService: PassportService, id: Short): T {
        passportService.getInputStream(id, PassportService.DEFAULT_MAX_BLOCKSIZE).use { inputStream ->
            @Suppress("UNCHECKED_CAST")
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
                val securityInfo = cardAccessFile.securityInfos.firstOrNull { it is PACEInfo } as? PACEInfo
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
        } catch (_: Exception) {
        }
        return false
    }

    private fun verifySigningCertificate(
        certificate: X509Certificate,
        masterList: Collection<X509Certificate>
    ): Boolean {
        val issuers = masterList.filter { it.subjectX500Principal == certificate.issuerX500Principal }
        for (issuer in issuers) {
            try {
                certificate.verify(issuer.publicKey)
                return true
            } catch (_: Exception) {
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
