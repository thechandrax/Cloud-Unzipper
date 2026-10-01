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
