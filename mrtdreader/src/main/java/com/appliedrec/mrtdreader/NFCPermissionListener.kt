package com.appliedrec.mrtdreader

interface NFCPermissionListener {
    fun onNFCPermissionGranted()
    fun onNFCPermissionDenied()
}