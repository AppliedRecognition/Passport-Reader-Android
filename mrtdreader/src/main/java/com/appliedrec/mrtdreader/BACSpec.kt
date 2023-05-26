package com.appliedrec.mrtdreader

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.util.Date

/**
 * Basic Access Control (BAC) specification
 * @version 1.0.0
 */
@Serializable
@Parcelize
@OptIn(ExperimentalSerializationApi::class)
data class BACSpec(
    @JsonNames("doc_number", "document_number")
    @Serializable
    val documentNumber: String,
    @JsonNames("dob")
    @Serializable(with = DateAsStringSerializer::class)
    val dateOfBirth: Date,
    @JsonNames("doe")
    @Serializable(with = DateAsStringSerializer::class)
    val dateOfExpiry: Date): Parcelable