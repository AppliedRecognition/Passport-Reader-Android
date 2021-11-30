package com.appliedrec.mrtdreader;

/**
 * Session listener
 * @since 2.0.0
 */
public interface MRTDScanSessionListener {

    /**
     * Called when passport scan session succeeds
     * @param bacSpec Basic Access Control (BAC) spec of the scanned passport
     * @param result Scan result
     * @since 2.0.0
     */
    void onMRTDScanSucceeded(BACSpec bacSpec, MRTDScanResult result);

    default void onMRTDScanFailed(BACSpec bacSpec, Throwable throwable) {}

    default void onMRTDScanCancelled(BACSpec bacSpec) {}
}
