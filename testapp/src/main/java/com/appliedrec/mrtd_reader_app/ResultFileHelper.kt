package com.appliedrec.mrtd_reader_app

import android.net.Uri
import androidx.core.net.toFile
import com.appliedrec.mrtdreader.MRTDScanResult
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File

object ResultFileHelper {

    val files: MutableList<File> = mutableListOf()

    suspend fun deleteFiles() = coroutineScope {
        while (files.isNotEmpty()) {
            files.removeAt(0).delete()
        }
    }

    suspend fun saveScanResult(result: MRTDScanResult.Success): Uri = coroutineScope {
        val resultFile = File.createTempFile("scan_result", ".json")
        resultFile.writeText(Json.encodeToString(result))
        files.add(resultFile)
        return@coroutineScope Uri.fromFile(resultFile)
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun readScanResult(uri: Uri): MRTDScanResult.Success = coroutineScope {
        uri.toFile().inputStream().use { inputStream ->
            val json = Json { ignoreUnknownKeys = true }
            return@coroutineScope json.decodeFromStream<MRTDScanResult.Success>(inputStream)
        }
    }
}