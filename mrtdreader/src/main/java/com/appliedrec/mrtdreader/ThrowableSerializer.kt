package com.appliedrec.mrtdreader

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal object ThrowableSerializer : KSerializer<Throwable> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Throwable", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Throwable {
        val message = decoder.decodeString()
        return Exception(message)
    }

    override fun serialize(encoder: Encoder, value: Throwable) {
        encoder.encodeString(value.message ?: "")
    }
}