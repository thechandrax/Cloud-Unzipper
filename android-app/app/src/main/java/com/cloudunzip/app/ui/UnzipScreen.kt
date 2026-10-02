package com.cloudunzip.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
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

// 🌐 Installed Browser Model
data class InstalledBrowser(
    val name: String,
    val packageName: String,
    val iconBitmap: ImageBitmap?
)

/**
 * Scans all installed web browsers on the device using multiple detection strategies.
 */
fun getInstalledBrowsers(context: Context): List<InstalledBrowser> {
    val pm = context.packageManager
    val browsers = mutableListOf<InstalledBrowser>()
    val seenPackages = mutableSetOf<String>()

    fun addFromPackage(pkg: String, defaultName: String? = null) {
        if (pkg == context.packageName || pkg in seenPackages) return
        try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            if (appInfo.enabled) {
                seenPackages.add(pkg)
                val label = try { appInfo.loadLabel(pm).toString() } catch (_: Exception) { defaultName ?: pkg }
                val iconBmp: ImageBitmap? = try {
                    val d = appInfo.loadIcon(pm)
                    if (d is BitmapDrawable && d.bitmap != null) {
                        d.bitmap.asImageBitmap()
                    } else if (d != null) {
                        val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
                        val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        d.setBounds(0, 0, canvas.width, canvas.height)
                        d.draw(canvas)
                        bmp.asImageBitmap()
                    } else null
                } catch (_: Exception) {
                    null
                }
                browsers.add(InstalledBrowser(name = label, packageName = pkg, iconBitmap = iconBmp))
            }
        } catch (_: Exception) {}
    }

    // 1. Query by Browsable HTTPS Intent
    try {
        val browsableIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://colab.research.google.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val resolveList = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(browsableIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(browsableIntent, PackageManager.MATCH_ALL)
        }
        for (info in resolveList) {
            addFromPackage(info.activityInfo.packageName, info.loadLabel(pm).toString())
        }
    } catch (_: Exception) {}

    // 2. Query standard ACTION_VIEW
    try {
        val standardIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))
        val resolveList = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(standardIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(standardIntent, PackageManager.MATCH_ALL)
        }
        for (info in resolveList) {
            addFromPackage(info.activityInfo.packageName, info.loadLabel(pm).toString())
        }
    } catch (_: Exception) {}

    // 3. Explicit check for common Android browsers
    val popularBrowsers = listOf(
        "com.android.chrome" to "Google Chrome",
        "com.sec.android.app.sbrowser" to "Samsung Internet",
        "com.brave.browser" to "Brave Browser",
        "org.mozilla.firefox" to "Mozilla Firefox",
        "com.microsoft.emmx" to "Microsoft Edge",
        "com.opera.browser" to "Opera Browser",
        "com.opera.mini.native" to "Opera Mini",
        "com.opera.gx" to "Opera GX",
        "com.duckduckgo.mobile.android" to "DuckDuckGo",
        "com.vivaldi.browser" to "Vivaldi",
        "com.kiwibrowser.browser" to "Kiwi Browser",
        "com.mi.globalbrowser" to "Mi Browser",
        "com.android.browser" to "Android Browser",
        "com.coloros.browser" to "ColorOS Browser",
        "com.vivo.browser" to "Vivo Browser",
        "com.heytap.browser" to "HeyTap Browser",
        "com.transsion.phoenix" to "Phoenix Browser",
        "mark.via.gp" to "Via Browser",
        "org.torproject.torbrowser" to "Tor Browser"
    )

    for ((pkg, defaultName) in popularBrowsers) {
        addFromPackage(pkg, defaultName)
    }

    return browsers.sortedBy { it.name.lowercase() }
}

// 🎨 Cambria / Serif Typography for the entire app
val CambriaFont = FontFamily.Serif

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnzipScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cloud_unzip_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // 🟢 Flickering / Pulsing Green Bubble Animation
    val pulseTransition = rememberInfiniteTransition(label = "greenPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // 👤 Google Account State
    var connectedAccount by remember {
        mutableStateOf(prefs.getString("connected_account", "thekristopherpaul@gmail.com") ?: "thekristopherpaul@gmail.com")
    }
    var showAccountDialog by remember { mutableStateOf(false) }
    var tempAccountInput by remember { mutableStateOf("") }

    // 🌐 Cloud Worker URL State (Saved in phone memory)
    var serverUrl by remember {
        mutableStateOf(prefs.getString("server_url", "https://costume-kurt-pittsburgh-scientists.trycloudflare.com") ?: "https://costume-kurt-pittsburgh-scientists.trycloudflare.com")
    }
    var isCheckingHealth by remember { mutableStateOf(false) }
    var isServerOnline by remember { mutableStateOf(false) }
    var serverHealthText by remember { mutableStateOf("Tap Test Link to verify") }

    // 🌐 Installed Browsers Chooser State
    var showBrowserDialog by remember { mutableStateOf(false) }
    var installedBrowsers by remember { mutableStateOf<List<InstalledBrowser>>(emptyList()) }

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
    var currentStatus by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var hasSuccess by remember { mutableStateOf(false) }
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
            serverHealthText = "Please enter your Cloud Server URL"
            return
        }
        isCheckingHealth = true
        coroutineScope.launch(Dispatchers.IO) {
            val client = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).build()
            try {
                val req = Request.Builder().url(serverUrl.trimEnd('/') + "/").build()
                val resp = client.newCall(req).execute()
                val success = resp.isSuccessful
                withContext(Dispatchers.Main) {
                    isServerOnline = success
                    serverHealthText = if (success) "🟢 Cloud Worker Online & Connected to Google Drive!" else "🔴 Server responded with code ${resp.code}"
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

    // Auto-check connection and pre-scan installed browsers on start
    LaunchedEffect(Unit) {
        if (serverUrl.isNotBlank()) {
            checkServerHealth()
        }
        val detected = getInstalledBrowsers(context)
        installedBrowsers = detected
        if (logMessages.isEmpty()) {
            logMessages.add("📱 Cloud Unzipper v1.4.1 ready.")
            logMessages.add("👤 Google Account: $connectedAccount")
            if (detected.isNotEmpty()) {
                logMessages.add("🌐 Browsers detected: ${detected.joinToString { it.name }}")
            }
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

    // 🌐 Installed Browser Selection Dialog
    if (showBrowserDialog) {
        AlertDialog(
            onDismissRequest = { showBrowserDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Choose Browser to Open Colab",
                        fontFamily = CambriaFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select any installed browser on your phone to open Google Colab:",
                        fontFamily = CambriaFont,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (installedBrowsers.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(installedBrowsers) { browser ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showBrowserDialog = false
                                            val colabUrl = "https://colab.research.google.com/github/thechandrax/Drive-Unzipper/blob/main/Drive_Cloud_Unzipper.ipynb"
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(colabUrl)).apply {
                                                setPackage(browser.packageName)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            try {
                                                context.startActivity(browserIntent)
                                                logMessages.add("🌐 Opening Colab in ${browser.name}...")
                                            } catch (e: Exception) {
                                                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(colabUrl)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(fallback)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (browser.iconBitmap != null) {
                                                Image(
                                                    bitmap = browser.iconBitmap,
                                                    contentDescription = browser.name,
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Public,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = browser.name,
                                                    fontFamily = CambriaFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = browser.packageName,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 📋 Manual Copy Option
                    OutlinedButton(
                        onClick = {
                            val colabUrl = "https://colab.research.google.com/github/thechandrax/Drive-Unzipper/blob/main/Drive_Cloud_Unzipper.ipynb"
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(colabUrl))
                            logMessages.add("📋 Copied Colab link to clipboard! Paste it into any browser.")
                            showBrowserDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Link (Paste in any Browser)", fontFamily = CambriaFont, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBrowserDialog = false }) {
                    Text("Close", fontFamily = CambriaFont)
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
                    // 🌟 Colorful Vibrant Logo & Title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFFF5722), // Vibrant Orange
                                            Color(0xFFE91E63), // Hot Pink
                                            Color(0xFF9C27B0), // Royal Purple
                                            Color(0xFF2979FF), // Electric Blue
                                            Color(0xFF00E676)  // Neon Emerald
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Cloud Unzipper",
                                    fontFamily = CambriaFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "v1.4.2",
                                        fontFamily = CambriaFont,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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

                        // 🌐 Button: GENERATE CLOUDFLARE LINK (Scans installed browsers and shows chooser dialog)
                        Button(
                            onClick = {
                                val detected = getInstalledBrowsers(context)
                                installedBrowsers = detected
                                showBrowserDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE65100) // Vibrant Warm Amber / Orange
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GENERATE CLOUDFLARE LINK",
                                fontFamily = CambriaFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }

                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = {
                                serverUrl = it.trim()
                                persistSettings()
                            },
                            label = { Text("Cloud Server URL", fontFamily = CambriaFont, fontSize = 12.sp) },
                            placeholder = { Text("https://xxxx.trycloudflare.com", fontFamily = CambriaFont, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clip = clipboardManager.getText()?.text?.trim() ?: ""
                                    if (clip.isNotBlank()) {
                                        serverUrl = clip
                                        persistSettings()
                                        checkServerHealth()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste from Clipboard",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isServerOnline) {
                                // 🟢 Flickering / Pulsing Green Bubble (Requested by user)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        // Outer pulsing glow
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer {
                                                    scaleX = pulseScale
                                                    scaleY = pulseScale
                                                    alpha = pulseAlpha * 0.45f
                                                }
                                                .clip(CircleShape)
                                                .background(Color(0xFF4CAF50))
                                        )
                                        // Core flickering green dot
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .graphicsLayer { alpha = pulseAlpha }
                                                .clip(CircleShape)
                                                .background(Color(0xFF00E676))
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Online",
                                        fontFamily = CambriaFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF5252))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (serverUrl.isBlank()) "No URL" else "Offline",
                                        fontFamily = CambriaFont,
                                        fontSize = 12.sp,
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text?.trim() ?: ""
                                        if (clip.isNotBlank()) {
                                            serverUrl = clip
                                            persistSettings()
                                            checkServerHealth()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Paste", fontFamily = CambriaFont, fontSize = 12.sp)
                                }
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
            }

            // 📁 Source & Target Folder Selection Card (Cleaned up!)
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // --- Source Folder (Exact label as requested) ---
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Source Folder:",
                                fontFamily = CambriaFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
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
                        }

                        // --- Target Folder (Exact label as requested) ---
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Target Folder:",
                                fontFamily = CambriaFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
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
                        }

                        // --- Exact Archive Name with Hint ---
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Exact Archive Name:",
                                fontFamily = CambriaFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            OutlinedTextField(
                                value = exactFileName,
                                onValueChange = { exactFileName = it },
                                placeholder = {
                                    Text("Type archive name here (or leave empty for newest)", fontFamily = CambriaFont, fontSize = 13.sp)
                                },
                                leadingIcon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 🚀 START BUTTON
                        Button(
                            onClick = {
                                if (serverUrl.isBlank()) {
                                    hasError = true
                                    hasSuccess = false
                                    progressPercent = 0
                                    currentStatus = "Error: Cloud Server URL is empty!"
                                    logMessages.add("❌ Error: Cloud Server URL is empty!")
                                    return@Button
                                }

                                if (!isExtracting) {
                                    isExtracting = true
                                    hasError = false
                                    hasSuccess = false
                                    progressPercent = 0
                                    currentStatus = "Connecting to Cloud Worker..."
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
                                                if (pct in 0..100 && pct >= progressPercent) {
                                                    progressPercent = pct
                                                }
                                                currentStatus = msg
                                                if (pct > 0) {
                                                    hasError = false
                                                }
                                                logMessages.add(msg)
                                            },
                                            onComplete = { isSuccess, finalMsg ->
                                                isExtracting = false
                                                if (isSuccess) {
                                                    progressPercent = 100
                                                    hasSuccess = true
                                                    hasError = false
                                                    currentStatus = finalMsg
                                                } else {
                                                    hasSuccess = false
                                                    hasError = true
                                                    currentStatus = finalMsg
                                                }
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

            // 📊 Cloud Status & Progress Card (Only shows success when genuine!)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasError) Color(0xFFFFEBEE)
                        else if (hasSuccess) Color(0xFFE8F5E9)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
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
                                color = if (hasError) Color(0xFFC62828)
                                else if (hasSuccess) Color(0xFF2E7D32)
                                else MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )
                        }

                        LinearProgressIndicator(
                            progress = progressPercent / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (hasError) Color(0xFFC62828)
                            else if (hasSuccess) Color(0xFF2E7D32)
                            else MaterialTheme.colorScheme.primary
                        )

                        if (currentStatus.isNotBlank()) {
                            Text(
                                text = currentStatus,
                                fontFamily = CambriaFont,
                                fontSize = 13.sp,
                                fontWeight = if (hasSuccess || hasError) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (hasError) Color(0xFFC62828)
                                else if (hasSuccess) Color(0xFF2E7D32)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
 * Connects directly to the active Cloud Worker
 * and streams REAL extraction progress line by line.
 */
suspend fun executeRealExtraction(
    serverUrl: String,
    sourceFolder: String,
    destFolder: String,
    exactFile: String,
    password: String,
    onProgress: (Int, String) -> Unit,
    onComplete: (Boolean, String) -> Unit
) = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .build()

    val json = JSONObject().apply {
        put("source_folder", sourceFolder)
        put("destination_folder", destFolder)
        put("exact_file_name", exactFile)
        put("password", password)
    }

    val requestBody = json.toString().toRequestBody("application/json".toMediaType())
    val endpoint = "${serverUrl.trimEnd('/')}/api/extract-stream"

    var extractionSucceeded = false
    var lastMessage = "Extraction stopped."

    try {
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val err = "Server returned HTTP ${response.code}: Check backend link."
            withContext(Dispatchers.Main) {
                onProgress(0, "❌ $err")
                onComplete(false, err)
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
                    val status = obj.optString("status", "")
                    val msg = obj.optString("message", "")
                    
                    // Parse progress from JSON or from regex pattern in message (e.g., "15%", "45%")
                    val pctFromJson = if (obj.has("progress")) obj.getInt("progress") else -1
                    val pctFromMsg = if (pctFromJson < 0) {
                        val match = Regex("""(\d+)%""").find(msg)
                        match?.groupValues?.get(1)?.toIntOrNull() ?: -1
                    } else -1

                    val resolvedPct = when {
                        pctFromJson >= 0 -> pctFromJson
                        pctFromMsg >= 0 -> pctFromMsg
                        else -> -1
                    }

                    lastMessage = msg
                    if (status == "success") {
                        extractionSucceeded = true
                    } else if (status == "error") {
                        extractionSucceeded = false
                    }

                    withContext(Dispatchers.Main) {
                        onProgress(resolvedPct, msg)
                    }
                } catch (_: Exception) {}
            }
        }

        withContext(Dispatchers.Main) {
            onComplete(extractionSucceeded, lastMessage)
        }
    } catch (e: Exception) {
        val err = "Connection Error: ${e.localizedMessage ?: "Failed to connect to Cloud Server"}"
        withContext(Dispatchers.Main) {
            onProgress(0, "❌ $err")
            onProgress(0, "💡 Check if your Colab Cloud Worker cell is currently running!")
            onComplete(false, err)
        }
    }
}
