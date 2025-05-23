package com.appliedrec.mrtdreader

import java.util.concurrent.ConcurrentHashMap

object MRTDScanResultStore {

    private val results = ConcurrentHashMap<Int,MRTDScanResult>()

    @Synchronized
    fun addResult(result: MRTDScanResult): Int {
        val key = (results.keys.maxOrNull() ?: -1) + 1
        results[key] = result
        return key
    }

    @Synchronized
    fun removeResult(key: Int): MRTDScanResult? {
        return results.remove(key)
    }
}