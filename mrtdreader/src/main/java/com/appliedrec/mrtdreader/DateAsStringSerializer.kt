package com.appliedrec.mrtdreader

import android.annotation.SuppressLint
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.SimpleDateFormat
import java.util.Date

internal object DateAsStringSerializer: KSerializer<Date> {

    @SuppressLint("SimpleDateFormat")
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd")

    override val descriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Date) {
        encoder.encodeString(dateFormat.format(value))
    }
    override fun deserialize(decoder: Decoder): Date {
        return decoder.decodeString().let {
            dateFormat.parse(it)
        } ?: Date()
    }
}