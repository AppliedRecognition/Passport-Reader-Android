package com.appliedrec.mrtdreader;

public class MRTDReaderProgress {

    private short fileId;
    private final MRTDScanResult result;
    private double progress = 0;

    public MRTDReaderProgress() {
        this.result = new MRTDScanResult();
    }

    public void setFileId(short fileId) {
        this.fileId = fileId;
    }

    public short getFileId() {
        return fileId;
    }

    public MRTDScanResult getResult() {
        return result;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }
}
