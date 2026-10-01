package com.cloudunzip.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnzipScreen() {
    val coroutineScope = rememberCoroutineScope()
    
    // UI Form State
    var serverUrl by remember { mutableStateOf("https://drive-cloud-unzip.onrender.com") }
    var sourceFolder by remember { mutableStateOf("GDFlix") }
    var destinationFolder by remember { mutableStateOf("MOVIES & WEB SERIES INFO") }
    var exactFileName by remember { mutableStateOf("") }
    var deleteAfter by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    // Execution State
    var isExtracting by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableIntStateOf(0) }
    var currentStatus by remember { mutableStateOf("Idle - Ready to extract") }
    val logMessages = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Cloud Unzip",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Cloud Unzipper",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick Info Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "100% Cloud-to-Cloud Extraction",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "0 MB phone storage • 0 MB mobile data used",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Input Form Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Extraction Settings",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )

                        OutlinedTextField(
                            value = sourceFolder,
                            onValueChange = { sourceFolder = it },
                            label = { Text("Drive Source Folder") },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = destinationFolder,
                            onValueChange = { destinationFolder = it },
                            label = { Text("Extract Destination Folder") },
                            leadingIcon = { Icon(Icons.Default.FolderSpecial, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = exactFileName,
                            onValueChange = { exactFileName = it },
                            label = { Text("Exact File Name (Optional - empty = newest)") },
                            leadingIcon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = deleteAfter,
                                onCheckedChange = { deleteAfter = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete original zip after extraction",
                                fontSize = 14.sp
                            )
                        }

                        // Extract Button
                        Button(
                            onClick = {
                                if (!isExtracting) {
                                    isExtracting = true
                                    progressPercent = 0
                                    logMessages.clear()
                                    logMessages.add("Connecting to cloud worker...")

                                    coroutineScope.launch {
                                        startCloudExtraction(
                                            serverUrl = serverUrl,
                                            sourceFolder = sourceFolder,
                                            destFolder = destinationFolder,
                                            exactFile = exactFileName,
                                            deleteAfter = deleteAfter,
                                            password = password,
                                            onProgress = { pct, msg ->
                                                progressPercent = pct
                                                currentStatus = msg
                                                logMessages.add(msg)
                                            },
                                            onComplete = {
                                                isExtracting = false
                                            }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = !isExtracting,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isExtracting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Extracting in Cloud...")
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("START CLOUD EXTRACTION", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Real-Time Progress Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cloud Status",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$progressPercent%",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Text(
                            text = currentStatus,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live Logs View
            item {
                Text(
                    text = "Live Cloud Logs",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            items(logMessages.takeLast(10).reversed()) { log ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = log,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.Green,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

suspend fun startCloudExtraction(
    serverUrl: String,
    sourceFolder: String,
    destFolder: String,
    exactFile: String,
    deleteAfter: Boolean,
    password: String,
    onProgress: (Int, String) -> Unit,
    onComplete: () -> Unit
) = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder().build()
    
    val json = JSONObject().apply {
        put("source_folder", sourceFolder)
        put("destination_folder", destFolder)
        put("exact_file_name", exactFile)
        put("delete_after", deleteAfter)
        put("password", password)
    }

    val requestBody = json.toString().toRequestBody("application/json".toMediaType())
    val endpoint = "${serverUrl.trimEnd('/')}/api/extract-stream"

    val request = Request.Builder()
        .url(endpoint)
        .post(requestBody)
        .build()

    try {
        val response = client.newCall(request).execute()
        val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val text = line ?: continue
            if (text.startsWith("data: ")) {
                val dataJson = text.removePrefix("data: ").trim()
                try {
                    val obj = JSONObject(dataJson)
                    val msg = obj.optString("message", "")
                    val pct = obj.optInt("progress", 0)
                    withContext(Dispatchers.Main) {
                        onProgress(pct, msg)
                    }
                } catch (e: Exception) {
                    // Ignore malformed json
                }
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            onProgress(0, "Error: ${e.localizedMessage ?: "Connection failed"}")
        }
    } finally {
        withContext(Dispatchers.Main) {
            onComplete()
        }
    }
}
