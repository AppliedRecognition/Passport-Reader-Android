package com.appliedrec.mrtdreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Base64OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.ByteArrayOutputStream

internal object BitmapSerializer: KSerializer<Bitmap> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Bitmap", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Bitmap {
        decoder.decodeString().let { base64String ->
            if (base64String.isEmpty()) {
                return Bitmap.createBitmap(0, 0, Bitmap.Config.ARGB_8888)
            }
            Base64.decode(base64String, Base64.NO_WRAP).let { bytes ->
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
    }

    override fun serialize(encoder: Encoder, value: Bitmap) {
        ByteArrayOutputStream().use { outputStream ->
            Base64OutputStream(outputStream, Base64.NO_WRAP).use { base64Stream ->
                value.compress(Bitmap.CompressFormat.JPEG, 90, base64Stream)
                base64Stream.flush()
                encoder.encodeString(outputStream.toString(Charsets.UTF_8.name()))
            }
        }
    }
}