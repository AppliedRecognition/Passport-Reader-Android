package com.appliedrec.mrtdreader

internal interface NFCPermissionListener {
    fun onNFCPermissionGranted()
    fun onNFCPermissionDenied()
}