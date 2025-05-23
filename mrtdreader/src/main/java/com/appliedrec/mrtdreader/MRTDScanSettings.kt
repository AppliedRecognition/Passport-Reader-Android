package com.appliedrec.mrtdreader

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MRTDScanSettings(val bacSpec: BACSpec, val masterListUri: Uri?=null) : Parcelable
