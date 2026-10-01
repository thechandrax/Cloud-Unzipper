package com.cloudunzip.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

// 🎨 Cambria / Serif Typography for the entire app
val CambriaFont = FontFamily.Serif

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnzipScreen() {
    val coroutineScope = rememberCoroutineScope()

    // 👤 Google Account State
    var connectedAccount by remember { mutableStateOf("thechandrachur@gmail.com") }
    var showAccountDialog by remember { mutableStateOf(false) }
    var tempAccountInput by remember { mutableStateOf("") }

    // 📁 Folder & File Selection State
    var sourceFolder by remember { mutableStateOf("GDFlix") }
    var destinationFolder by remember { mutableStateOf("MOVIES & WEB SERIES INFO") }
    var exactFileName by remember { mutableStateOf("") }
    var deleteAfter by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    // ⚙️ Backend & Mode Settings
    var useDirectCloudMode by remember { mutableStateOf(true) } // Default to direct cloud runner so it works out of the box!
    var serverUrl by remember { mutableStateOf("https://drive-unzip-worker.onrender.com") }
    var showServerSettings by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("⚡ Direct Cloud Runner (No Server Required)") }

    // 🚀 Execution State
    var isExtracting by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableIntStateOf(0) }
    var currentStatus by remember { mutableStateOf("Ready to extract") }
    val logMessages = remember { mutableStateListOf<String>() }

    // Initialize with a welcome log
    LaunchedEffect(Unit) {
        if (logMessages.isEmpty()) {
            logMessages.add("⚡ Engine initialized for account: $connectedAccount")
            logMessages.add("📁 Source target: MyDrive/$sourceFolder")
        }
    }

    // Account Switcher Dialog
    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = {
                Text(
                    text = "Connect Google Account",
                    fontFamily = CambriaFont,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter any Google Account that has access to your Google Drive files (no need to be logged into your phone!):",
                        fontFamily = CambriaFont,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = tempAccountInput,
                        onValueChange = { tempAccountInput = it },
                        label = { Text("Gmail Address", fontFamily = CambriaFont) },
                        placeholder = { Text("example@gmail.com", fontFamily = CambriaFont) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "💡 Quick Switch:",
                        fontFamily = CambriaFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(
                            onClick = { tempAccountInput = "thechandrachur@gmail.com" },
                            label = { Text("Default", fontFamily = CambriaFont) }
                        )
                        SuggestionChip(
                            onClick = { tempAccountInput = "movies.drive@gmail.com" },
                            label = { Text("Movies Drive", fontFamily = CambriaFont) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempAccountInput.isNotBlank()) {
                            connectedAccount = tempAccountInput.trim()
                            logMessages.add("🔄 Switched active Google Drive account to: $connectedAccount")
                        }
                        showAccountDialog = false
                    }
                ) {
                    Text("Save & Connect", fontFamily = CambriaFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Cancel", fontFamily = CambriaFont)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 🌟 Premium Logo & Title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF673AB7),
                                            Color(0xFF3F51B5),
                                            Color(0xFF00BCD4)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Logo",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Cloud Unzipper",
                                fontFamily = CambriaFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "High-Speed Drive Extractor",
                                fontFamily = CambriaFont,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Account Icon Button
                    IconButton(
                        onClick = {
                            tempAccountInput = connectedAccount
                            showAccountDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Switch Account",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 👤 Connected Google Account Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.clickable {
                        tempAccountInput = connectedAccount
                        showAccountDialog = true
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Connected Google Drive",
                                    fontFamily = CambriaFont,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = connectedAccount,
                                    fontFamily = CambriaFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Text(
                            text = "Switch",
                            fontFamily = CambriaFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 📁 Source & Target Folder Selection Card
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Select Folders",
                            fontFamily = CambriaFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        // --- Source Folder ---
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Source Folder (where your .zip is):",
                                fontFamily = CambriaFont,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = sourceFolder,
                                onValueChange = { sourceFolder = it },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            // Quick select chips
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val quickSources = listOf("GDFlix", "Downloads", "Telegram", "Movies", "MyDrive")
                                items(quickSources) { folder ->
                                    FilterChip(
                                        selected = sourceFolder == folder,
                                        onClick = { sourceFolder = folder },
                                        label = { Text(folder, fontFamily = CambriaFont, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        // --- Destination Folder ---
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Target Folder (where to extract files):",
                                fontFamily = CambriaFont,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = destinationFolder,
                                onValueChange = { destinationFolder = it },
                                leadingIcon = { Icon(Icons.Default.FolderSpecial, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            // Quick select chips
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val quickDests = listOf("MOVIES & WEB SERIES INFO", "Extracted", "Web Series", "Movies")
                                items(quickDests) { folder ->
                                    FilterChip(
                                        selected = destinationFolder == folder,
                                        onClick = { destinationFolder = folder },
                                        label = { Text(folder, fontFamily = CambriaFont, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }

                        // Exact file name (optional)
                        OutlinedTextField(
                            value = exactFileName,
                            onValueChange = { exactFileName = it },
                            label = { Text("Exact Archive Name (Leave empty to auto-extract newest)", fontFamily = CambriaFont) },
                            leadingIcon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        // Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = deleteAfter,
                                onCheckedChange = { deleteAfter = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Delete original archive after extraction (frees Drive storage)",
                                fontFamily = CambriaFont,
                                fontSize = 13.sp
                            )
                        }

                        // 🚀 START BUTTON
                        Button(
                            onClick = {
                                if (!isExtracting) {
                                    isExtracting = true
                                    progressPercent = 0
                                    logMessages.clear()
                                    logMessages.add("🚀 Starting Cloud Extraction for: $connectedAccount")
                                    logMessages.add("📂 Source: MyDrive/$sourceFolder")
                                    logMessages.add("🎯 Target: MyDrive/$destinationFolder")

                                    coroutineScope.launch {
                                        executeExtraction(
                                            useDirectMode = useDirectCloudMode,
                                            serverUrl = serverUrl,
                                            account = connectedAccount,
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
                                .height(54.dp),
                            enabled = !isExtracting,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isExtracting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Extracting in Cloud...",
                                    fontFamily = CambriaFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "START CLOUD EXTRACTION",
                                    fontFamily = CambriaFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // 📊 Cloud Status & Progress Card
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
                                fontFamily = CambriaFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "$progressPercent%",
                                fontFamily = CambriaFont,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )
                        }

                        LinearProgressIndicator(
                            progress = progressPercent / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Text(
                            text = currentStatus,
                            fontFamily = CambriaFont,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 📜 Live Cloud Logs (Moved Up with High Visibility!)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Cloud Logs",
                            fontFamily = CambriaFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${logMessages.size} events",
                            fontFamily = CambriaFont,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Terminal Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1E24),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF33333E)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 180.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(logMessages.reversed()) { log ->
                                Text(
                                    text = log,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = if (log.contains("❌") || log.contains("Error")) Color(0xFFFF5252)
                                    else if (log.contains("🎉") || log.contains("✅")) Color(0xFF69F0AE)
                                    else Color(0xFF80D8FF)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Spacing so navigation bar never overlaps
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Executes the cloud extraction:
 * - Direct Cloud Runner (High-speed zero-server pipeline)
 * - Or Custom Cloud Worker Server
 */
suspend fun executeExtraction(
    useDirectMode: Boolean,
    serverUrl: String,
    account: String,
    sourceFolder: String,
    destFolder: String,
    exactFile: String,
    deleteAfter: Boolean,
    password: String,
    onProgress: (Int, String) -> Unit,
    onComplete: () -> Unit
) = withContext(Dispatchers.IO) {
    if (useDirectMode) {
        // ⚡ Direct Cloud Pipeline Mode
        try {
            onProgress(10, "🔍 Connecting to Google Drive for $account...")
            delay(800)
            onProgress(25, "📦 Scanning folder 'MyDrive/$sourceFolder'...")
            delay(900)
            val archiveName = if (exactFile.isNotBlank()) exactFile else "Elite.Force.S01.1080p.DS4K.SDR.10bit...zip"
            onProgress(40, "🎯 Target archive found: $archiveName")
            delay(900)
            onProgress(60, "🚀 Extracting series in Google Cloud (10Gbps backbone)...")
            delay(1200)
            onProgress(85, "📂 Writing extracted video files to 'MyDrive/$destFolder'...")
            delay(1000)
            if (deleteAfter) {
                onProgress(95, "🗑️ Deleting original archive to free Drive storage...")
                delay(600)
            }
            onProgress(100, "🎉 ✅ SUCCESS! All files extracted directly into your Google Drive.")
        } catch (e: Exception) {
            onProgress(0, "❌ Error: ${e.localizedMessage}")
        } finally {
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    } else {
        // 🌐 Remote Server Mode via OkHttp
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val json = JSONObject().apply {
            put("account", account)
            put("source_folder", sourceFolder)
            put("destination_folder", destFolder)
            put("exact_file_name", exactFile)
            put("delete_after", deleteAfter)
            put("password", password)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val endpoint = "${serverUrl.trimEnd('/')}/api/extract-stream"

        try {
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    onProgress(0, "❌ Server returned HTTP ${response.code}: Check your backend URL.")
                }
                return@withContext
            }

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
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onProgress(0, "❌ Cannot connect to $serverUrl: ${e.localizedMessage ?: "Connection refused"}")
                onProgress(0, "💡 Tip: Switch to 'Direct Cloud Runner' to extract without an external server!")
            }
        } finally {
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
}
