
package com.example.qrpostscanner

data class ScanRecord(
    val qr: String,
    val timestampUtcIso: String,
    val latitude: Double?,
    val longitude: Double?
)
