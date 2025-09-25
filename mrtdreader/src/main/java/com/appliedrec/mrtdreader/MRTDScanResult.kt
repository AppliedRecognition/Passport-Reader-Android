package com.appliedrec.mrtdreader

import android.graphics.Bitmap
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
sealed class MRTDScanResult : Parcelable {
    /**
     * Result of a successful MRTD scan
     * @property documentCode Document code
     * @property issuingState Document issuing state
     * @property primaryIdentifier Primary identifier
     * @property secondaryIdentifiers
     * @property nationality Nationality of the document holder
     * @property documentNumber Document number
     * @property personalNumber Personal number
     * @property dateOfBirth Document holder's date of birth
     * @property dateOfExpiry Document date of expiry
     * @property gender Document holder's gender
     * @property faceImage Face image
     * @version 1.0.0
     */
    @Serializable
    @ConsistentCopyVisibility
    data class Success internal constructor(
        var documentCode: String?,
        var issuingState: String?,
        var primaryIdentifier: String?,
        var secondaryIdentifiers: Array<String>,
        var nationality: String?,
        var documentNumber: String?,
        var personalNumber: String?,
        var dateOfBirth: String?,
        var dateOfExpiry: String?,
        var gender: String?,
        @Serializable(with = BitmapSerializer::class)
        var faceImage: Bitmap?,
        var signatureVerified: Boolean=false,
        var issuerVerified: Boolean=false
    ): MRTDScanResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            if (signatureVerified != other.signatureVerified) return false
            if (issuerVerified != other.issuerVerified) return false
            if (documentCode != other.documentCode) return false
            if (issuingState != other.issuingState) return false
            if (primaryIdentifier != other.primaryIdentifier) return false
            if (!secondaryIdentifiers.contentEquals(other.secondaryIdentifiers)) return false
            if (nationality != other.nationality) return false
            if (documentNumber != other.documentNumber) return false
            if (personalNumber != other.personalNumber) return false
            if (dateOfBirth != other.dateOfBirth) return false
            if (dateOfExpiry != other.dateOfExpiry) return false
            if (gender != other.gender) return false
            if (faceImage != other.faceImage) return false

            return true
        }

        override fun hashCode(): Int {
            var result = signatureVerified.hashCode()
            result = 31 * result + issuerVerified.hashCode()
            result = 31 * result + (documentCode?.hashCode() ?: 0)
            result = 31 * result + (issuingState?.hashCode() ?: 0)
            result = 31 * result + (primaryIdentifier?.hashCode() ?: 0)
            result = 31 * result + secondaryIdentifiers.contentHashCode()
            result = 31 * result + (nationality?.hashCode() ?: 0)
            result = 31 * result + (documentNumber?.hashCode() ?: 0)
            result = 31 * result + (personalNumber?.hashCode() ?: 0)
            result = 31 * result + (dateOfBirth?.hashCode() ?: 0)
            result = 31 * result + (dateOfExpiry?.hashCode() ?: 0)
            result = 31 * result + (gender?.hashCode() ?: 0)
            result = 31 * result + (faceImage?.hashCode() ?: 0)
            return result
        }
    }

    @Serializable
    @ConsistentCopyVisibility
    data class Failure internal constructor(
        @Serializable(with = ThrowableSerializer::class)
        val error: Throwable
    ): MRTDScanResult()
    data object Cancelled: MRTDScanResult()
}