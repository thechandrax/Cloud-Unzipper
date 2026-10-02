package com.cloudunzip.app.network

data class ExtractRequest(
    val source_folder: String,
    val destination_folder: String,
    val exact_file_name: String = "",
    val delete_after: Boolean = false,
    val password: String = ""
)

data class ProgressUpdate(
    val status: String,
    val progress: Int = 0,
    val message: String = ""
)

data class TransferRequest(
    val url: String,
    val destination_folder: String = "GDFlix",
    val custom_filename: String = "",
    val auto_extract: Boolean = true,
    val extract_destination: String = "MOVIES & WEB SERIES INFO"
)
