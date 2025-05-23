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
    ): MRTDScanResult()
    @Serializable
    data class Failure internal constructor(
        @Serializable(with = ThrowableSerializer::class)
        val error: Throwable
    ): MRTDScanResult()
    data object Cancelled: MRTDScanResult()
}