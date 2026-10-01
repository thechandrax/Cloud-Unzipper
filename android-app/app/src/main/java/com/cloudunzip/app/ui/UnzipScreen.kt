package com.cloudunzip.app.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
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
import java.util.concurrent.TimeUnit

// 🎨 Cambria / Serif Typography for the entire app
val CambriaFont = FontFamily.Serif

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnzipScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cloud_unzip_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()

    // 👤 Google Account State
    var connectedAccount by remember {
        mutableStateOf(prefs.getString("connected_account", "thekristopherpaul@gmail.com") ?: "thekristopherpaul@gmail.com")
    }
    var showAccountDialog by remember { mutableStateOf(false) }
    var tempAccountInput by remember { mutableStateOf("") }

    // 🌐 Cloud Worker URL State (Saved in phone memory)
    var serverUrl by remember {
        mutableStateOf(prefs.getString("server_url", "") ?: "")
    }
    var isCheckingHealth by remember { mutableStateOf(false) }
    var isServerOnline by remember { mutableStateOf(false) }
    var serverHealthText by remember { mutableStateOf("Not tested yet") }

    // 📁 Folder & File Selection State
    var sourceFolder by remember {
        mutableStateOf(prefs.getString("source_folder", "GDFlix") ?: "GDFlix")
    }
    var destinationFolder by remember {
        mutableStateOf(prefs.getString("dest_folder", "MOVIES & WEB SERIES INFO") ?: "MOVIES & WEB SERIES INFO")
    }
    var exactFileName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // 🚀 Execution State
    var isExtracting by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableIntStateOf(0) }
    var currentStatus by remember { mutableStateOf("Idle - Ready to extract") }
    val logMessages = remember { mutableStateListOf<String>() }

    // Save preferences helper
    fun persistSettings() {
        prefs.edit()
            .putString("connected_account", connectedAccount)
            .putString("server_url", serverUrl)
            .putString("source_folder", sourceFolder)
            .putString("dest_folder", destinationFolder)
            .apply()
    }

    // Function to test cloud server health
    fun checkServerHealth() {
        if (serverUrl.isBlank()) {
            isServerOnline = false
            serverHealthText = "Please enter your Colab Cloudflare URL"
            return
        }
        isCheckingHealth = true
        coroutineScope.launch(Dispatchers.IO) {
            val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()
            try {
                val req = Request.Builder().url(serverUrl.trimEnd('/') + "/").build()
                val resp = client.newCall(req).execute()
                val success = resp.isSuccessful
                withContext(Dispatchers.Main) {
                    isServerOnline = success
                    serverHealthText = if (success) "🟢 Cloud Worker Online & Connected to Google Drive!" else "🔴 Server responded with error ${resp.code}"
                    isCheckingHealth = false
                    persistSettings()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isServerOnline = false
                    serverHealthText = "🔴 Cannot connect: ${e.localizedMessage ?: "Offline"}"
                    isCheckingHealth = false
                }
            }
        }
    }

    // Auto-check connection on start if URL exists
    LaunchedEffect(Unit) {
        if (serverUrl.isNotBlank()) {
            checkServerHealth()
        }
        if (logMessages.isEmpty()) {
            logMessages.add("📱 Cloud Unzipper initialized.")
            logMessages.add("👤 Google Account: $connectedAccount")
            logMessages.add("📂 Target: MyDrive/$sourceFolder -> MyDrive/$destinationFolder")
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
                        text = "Active account for Google Drive storage:",
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
                            onClick = { tempAccountInput = "thekristopherpaul@gmail.com" },
                            label = { Text("thekristopherpaul", fontFamily = CambriaFont) }
                        )
                        SuggestionChip(
                            onClick = { tempAccountInput = "thechandrachur@gmail.com" },
                            label = { Text("thechandrax", fontFamily = CambriaFont) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempAccountInput.isNotBlank()) {
                            connectedAccount = tempAccountInput.trim()
                            persistSettings()
                            logMessages.add("🔄 Switched active account to: $connectedAccount")
                        }
                        showAccountDialog = false
                    }
                ) {
                    Text("Save", fontFamily = CambriaFont)
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

                    // Account Button
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
                                    text = "Google Drive Account",
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

            // 🌐 Cloud Worker Connection Setup Card
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (isServerOnline) Color(0xFFF1F8E9) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ Cloud Engine Connection",
                                fontFamily = CambriaFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isServerOnline) Color(0xFF4CAF50) else Color(0xFFFF5252))
                            )
                        }

                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = {
                                serverUrl = it.trim()
                                persistSettings()
                            },
                            label = { Text("Colab Cloudflare URL (https://...trycloudflare.com)", fontFamily = CambriaFont, fontSize = 12.sp) },
                            placeholder = { Text("https://example.trycloudflare.com", fontFamily = CambriaFont, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = serverHealthText,
                                fontFamily = CambriaFont,
                                fontSize = 11.sp,
                                color = if (isServerOnline) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { checkServerHealth() },
                                enabled = !isCheckingHealth,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isCheckingHealth) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White)
                                } else {
                                    Text("Test Link", fontFamily = CambriaFont, fontSize = 12.sp)
                                }
                            }
                        }
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
                                onValueChange = {
                                    sourceFolder = it
                                    persistSettings()
                                },
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
                                        onClick = {
                                            sourceFolder = folder
                                            persistSettings()
                                        },
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
                                onValueChange = {
                                    destinationFolder = it
                                    persistSettings()
                                },
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
                                        onClick = {
                                            destinationFolder = folder
                                            persistSettings()
                                        },
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

                        // 🚀 START BUTTON
                        Button(
                            onClick = {
                                if (serverUrl.isBlank()) {
                                    logMessages.add("❌ Error: Cloud Worker URL is empty!")
                                    logMessages.add("👉 Please run the Colab Worker and paste your Cloudflare URL above.")
                                    return@Button
                                }

                                if (!isExtracting) {
                                    isExtracting = true
                                    progressPercent = 0
                                    logMessages.clear()
                                    logMessages.add("🚀 Dispatching Cloud Extraction...")
                                    logMessages.add("📂 Source: MyDrive/$sourceFolder")
                                    logMessages.add("🎯 Target: MyDrive/$destinationFolder")

                                    coroutineScope.launch {
                                        executeRealExtraction(
                                            serverUrl = serverUrl,
                                            sourceFolder = sourceFolder,
                                            destFolder = destinationFolder,
                                            exactFile = exactFileName,
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

            // 📜 Live Cloud Logs (Elevated above bottom navigation bar)
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
                            .heightIn(min = 130.dp, max = 220.dp)
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
                                    color = if (log.contains("❌") || log.contains("Error") || log.contains("Failed")) Color(0xFFFF5252)
                                    else if (log.contains("🎉") || log.contains("✅") || log.contains("SUCCESS")) Color(0xFF69F0AE)
                                    else Color(0xFF80D8FF)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Spacing so navigation bar never overlaps
            item {
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }
}

/**
 * Connects directly to the active Cloud Worker (Colab Cloudflare Tunnel / Server)
 * and streams REAL extraction progress from 7-Zip executing in Google's cloud.
 */
suspend fun executeRealExtraction(
    serverUrl: String,
    sourceFolder: String,
    destFolder: String,
    exactFile: String,
    password: String,
    onProgress: (Int, String) -> Unit,
    onComplete: () -> Unit
) = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    val json = JSONObject().apply {
        put("source_folder", sourceFolder)
        put("destination_folder", destFolder)
        put("exact_file_name", exactFile)
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
            onProgress(0, "❌ Connection Error: ${e.localizedMessage ?: "Failed to connect"}")
            onProgress(0, "💡 Check if your Colab Cloud Worker cell is currently running!")
        }
    } finally {
        withContext(Dispatchers.Main) {
            onComplete()
        }
    }
}
