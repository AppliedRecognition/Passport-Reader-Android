package com.appliedrec.mrtdreader

/**
 * Session listener
 * @since 2.0.0
 */
interface MRTDScanSessionListener {
    /**
     * Called when passport scan session succeeds
     * @param bacSpec Basic Access Control (BAC) spec of the scanned passport
     * @param result Scan result
     * @since 2.0.0
     */
    fun onMRTDScanSucceeded(bacSpec: BACSpec, result: MRTDScanResult)
    fun onMRTDScanFailed(bacSpec: BACSpec, throwable: Throwable) {}
    fun onMRTDScanCancelled(bacSpec: BACSpec) {}
}