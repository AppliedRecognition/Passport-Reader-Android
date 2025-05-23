package com.appliedrec.mrtdreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.nfc.tech.IsoDep
import io.reactivex.rxjava3.core.Observable
import jj2000.JJ2000Frontend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
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
import java.security.Security
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer


internal object MRTDReader {

    @JvmStatic
    @Throws(Exception::class)
    suspend fun readPassport(isoDep: IsoDep, bacSpec: BACSpec, progressConsumer: Consumer<Progress>?, masterListFuture: CompletableFuture<Collection<X509Certificate>>?): MRTDScanResult = coroutineScope {
        val bacKey = BACKey(bacSpec.documentNumber, bacSpec.dateOfBirth, bacSpec.dateOfExpiry)
        var passportService: PassportService? = null
        try {
            val tasks = arrayOf(
                Progress(0.0, "Authenticating"),
                Progress(0.0, "Reading personal information"),
                Progress(0.0, "Reading face image"),
                Progress(0.0, "Verifying document signature")
            )
            Security.addProvider(BouncyCastleProvider())
            val cardService = CardService.getInstance(isoDep)
            passportService = PassportService(
                cardService,
                PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                PassportService.DEFAULT_MAX_BLOCKSIZE,
                true,
                false
            )
            reportProgress(progressConsumer, tasks, 0)
            passportService.open()
            val paceSucceeded = doPACE(passportService, bacKey)
            passportService.sendSelectApplet(paceSucceeded)
            if (!paceSucceeded) {
                passportService.doBAC(bacKey)
            }
            val sodFile: SODFile = readFile(passportService, PassportService.EF_SOD)
            tasks[0].completed = 1.0
            reportProgress(progressConsumer, tasks, 1)
            val dG1File: DG1File = readFile(passportService, PassportService.EF_DG1)
            val result = MRTDScanResult()
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
            tasks[1].completed = 1.0
            reportProgress(progressConsumer, tasks, 2)
            val dG2File: DG2File = readFile(passportService, PassportService.EF_DG2)
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
                                tasks[2].completed = totalRead / imageSize
                                reportProgress(progressConsumer, tasks, 2)
                            }
                            outputStream.flush()
                            val imageData = outputStream.toByteArray()
                            faceImage = JJ2000Frontend.decode(imageData)
                            if (faceImage == null) {
                                faceImage = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                            }
                        }
                    }
                    if (faceImage != null) {
                        result.faceImage = faceImage
                        break
                    }
                }
            }
            tasks[2].completed = 1.0
            reportProgress(progressConsumer, tasks, 3)
            result.signatureVerified = checkDocumentSignature(sodFile)
            tasks[3].completed = 0.3
            reportProgress(progressConsumer, tasks, 3)
            if (masterListFuture != null) {
                try {
                    val masterList = masterListFuture.get()
                    result.issuerVerified = verifySigningCertificate(sodFile.docSigningCertificate, masterList)
                } catch (e: Exception) {
                    result.issuerVerified = false
                }
            }
            tasks[3].completed = 1.0
            reportProgress(progressConsumer, tasks, 3)
            result
        } finally {
            passportService?.close()
        }
    }

    fun reportProgress(progressConsumer: Consumer<Progress>?, tasks: Array<Progress>, currentTaskIndex: Int) {
        progressConsumer?.let { consumer ->
            val totalProgress = tasks.sumOf { it.completed } / tasks.size.toDouble()
            val progress = Progress(totalProgress, tasks[currentTaskIndex].message)
            CoroutineScope(Dispatchers.Main).launch {
                consumer.accept(progress)
            }
        }
    }

    @JvmStatic
    fun createProgressObservable(isoDep: IsoDep, bacSpec: BACSpec, masterListFuture: CompletableFuture<Collection<X509Certificate>>?): Observable<MRTDReaderProgress> {
        val bacKey = BACKey(bacSpec.documentNumber, bacSpec.dateOfBirth, bacSpec.dateOfExpiry)
        return Observable.create { emitter ->
            var passportService: PassportService? = null
            try {
                Security.addProvider(BouncyCastleProvider())
//                Security.insertProviderAt(BouncyCastleProvider(), 1)
//                Security.addProvider(SecurityProvider("MRTDSecurityProvider", 1.0, "null"))

                val cardService = CardService.getInstance(isoDep)
                passportService = PassportService(
                    cardService,
                    PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                    PassportService.DEFAULT_MAX_BLOCKSIZE,
                    true,
                    false
                )
                passportService.open()

                val paceSucceeded = doPACE(passportService, bacKey)
                passportService.sendSelectApplet(paceSucceeded)
                if (!paceSucceeded) {
                    passportService.doBAC(bacKey)
                }
                val sodFile: SODFile = readFile(passportService, PassportService.EF_SOD)
                val signatureVerified = checkDocumentSignature(sodFile)
                if (masterListFuture != null) {
                    val masterList = masterListFuture.get()
                    val issuerVerified = verifySigningCertificate(sodFile.docSigningCertificate, masterList)
                }
                val personalDataShare = 0.05
                var progress = MRTDReaderProgress(PassportService.EF_DG1, MRTDScanResult(), personalDataShare)
                val dG1File: DG1File = readFile(passportService, PassportService.EF_DG1)
                val mrzInfo = dG1File.mrzInfo
                progress.result.dateOfBirth = mrzInfo.dateOfBirth
                progress.result.dateOfExpiry = mrzInfo.dateOfExpiry
                progress.result.documentNumber = mrzInfo.documentNumber
                progress.result.documentCode = mrzInfo.documentCode
                progress.result.gender = mrzInfo.gender.name
                progress.result.issuingState = mrzInfo.issuingState
                progress.result.nationality = mrzInfo.nationality
                progress.result.personalNumber = mrzInfo.personalNumber
                progress.result.primaryIdentifier = mrzInfo.primaryIdentifier
                progress.result.secondaryIdentifiers = mrzInfo.secondaryIdentifierComponents
                if (!emitter.isDisposed) {
                    emitter.onNext(progress)
                }
                progress = MRTDReaderProgress(PassportService.EF_DG2, progress.result, progress.progress)
                if (!emitter.isDisposed) {
                    emitter.onNext(progress)
                }
                val dG2File: DG2File = readFile(passportService, PassportService.EF_DG2)
                val faceImageInfos = mutableListOf<FaceImageInfo>()
                dG2File.faceInfos.forEach { faceInfo ->
                    faceImageInfos.addAll(faceInfo.faceImageInfos)
                }
                if (faceImageInfos.isNotEmpty()) {
                    var faceImage: Bitmap?
                    for (faceImageInfo in faceImageInfos) {
                        faceImageInfo.imageInputStream.use { inputStream ->
                            ByteArrayOutputStream().use { outputStream ->
                                var read: Int
                                val buffer = ByteArray(1024)
                                var totalRead: Int = 0
                                while (inputStream.read(buffer).also { read = it } > 0) {
                                    outputStream.write(buffer, 0, read)
                                    totalRead += read
                                    progress = MRTDReaderProgress(PassportService.EF_DG2, progress.result, personalDataShare + totalRead.toDouble() / faceImageInfo.imageLength.toDouble() * (1.0 - personalDataShare))
                                    if (!emitter.isDisposed) {
                                        emitter.onNext(progress)
                                    }
                                }
                                outputStream.flush()
                                val imageData = outputStream.toByteArray()
                                faceImage = JJ2000Frontend.decode(imageData)
                                if (faceImage == null) {
                                    faceImage = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                                }
                            }
                        }
                        if (faceImage != null) {
                            progress.result.faceImage = faceImage
                            break
                        }
                    }
                }
                if (!emitter.isDisposed) {
                    progress = MRTDReaderProgress(PassportService.EF_DG2, progress.result, 1.0)
                    emitter.onNext(progress)
                    emitter.onComplete()
                }
            } catch (e: Exception) {
                if (!emitter.isDisposed) {
                    emitter.onError(e)
                }
            } finally {
                passportService?.close()
            }
        }
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

    private fun doChipAuthentication(passportService: PassportService): Boolean {
        return false
//        try {
//            val dg14File: DG14File = readFile(passportService, PassportService.EF_DG14)
//            dg14File.securityInfos.firstOrNull { it is ChipAuthenticationPublicKeyInfo }?.let { securityInfo ->
//                val publicKey = (securityInfo as ChipAuthenticationPublicKeyInfo).subjectPublicKey
//                val keyId = securityInfo.keyId
//                val agreementAlgo = Util.inferKeyAgreementAlgorithm(publicKey)
//                val keyPairGenerator = KeyPairGenerator.getInstance(agreementAlgo)
//                var params: AlgorithmParameterSpec? = null
//                if ("DH" == agreementAlgo) {
//                    val dhPublicKey: DHPublicKey = publicKey as DHPublicKey
//                    params = dhPublicKey.getParams()
//                } else if ("ECDH" == agreementAlgo) {
//                    val ecPublicKey: ECPublicKey = publicKey as ECPublicKey
//                    params = ecPublicKey.getParams()
//                } else {
//                    throw IllegalStateException("Unsupported algorithm \"$agreementAlgo\"")
//                }
//                keyPairGenerator.initialize(params)
//                val keyPair = keyPairGenerator.generateKeyPair()
//                val keyAgreement = KeyAgreement.getInstance(agreementAlgo)
//                keyAgreement.init(keyPair.private)
//                keyAgreement.doPhase(publicKey, true)
//                val secret = keyAgreement.generateSecret()
//                var keyData: ByteArray = ByteArray(0)
//                var idData: ByteArray = ByteArray(0)
//                var keyHash: ByteArray = ByteArray(0)
//                if ("DH" == agreementAlgo) {
//                    val dhPublicKey = keyPair.public as DHPublicKey
//                    keyData = dhPublicKey.y.toByteArray()
//                    val md = MessageDigest.getInstance("SHA1")
//                    keyHash = md.digest(keyData)
//                } else {
//                    val ecPublicKey = keyPair.public as org.spongycastle.jce.interfaces.ECPublicKey
//                    keyData = ecPublicKey.q.getEncoded(false)
//                    val t = Util.i2os(ecPublicKey.q.x.toBigInteger())
//                    keyHash = Util.alignKeyDataToSize(t, ecPublicKey.parameters.curve.fieldSize / 8)
//                }
//                keyData = Util.wrapDO(0x91.toByte(), keyData)
//                if (keyId.compareTo(BigInteger.ZERO) >= 0) {
//                    val keyIdBytes = keyId.toByteArray()
//                    idData = Util.wrapDO(0x84.toByte(), keyIdBytes)
//                }
//                passportService.sendMSEKAT(passportService.getWrapper(), keyData, idData)
//
//                val ksEnc = Util.deriveKey(secret, Util.ENC_MODE)
//                val ksMac = Util.deriveKey(secret, Util.MAC_MODE)
//
//                passportService.setWrapper(DESedeSecureMessagingWrapper(ksEnc, ksMac, 0L))
//                val fld: Field = PassportService::class.java.getDeclaredField("state")
//                fld.setAccessible(true)
//                fld.set(passportService, 4) //PassportService.CA_AUTHENTICATED_STATE)
//
//                return ChipAuthenticationResult(keyId, publicKey, keyHash, keyPair)
//            }
//        } catch (ignore: Exception) {}
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
}