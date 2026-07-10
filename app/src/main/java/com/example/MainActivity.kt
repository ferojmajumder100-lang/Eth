package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.Security
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0A0A0A)
                ) { innerPadding ->
                    IGViewerApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

data class RemoteConfig(
    val status: String = "on",
    val host: String = "change6.owlproxy.com",
    val port: Int = 7778,
    val user: String = "117Sz8vwEt70_custom_zone_BD",
    val pass: String = "3341056"
)

enum class ButtonStyleType {
    Primary,
    Secondary,
    Alert
}

// Function to handle SOCKS5 Proxy registration cleanly
fun updateProxySettings(enabled: Boolean, config: RemoteConfig) {
    try {
        if (enabled) {
            // 1. JVM-wide SOCKS proxy properties
            System.setProperty("socksProxyHost", config.host)
            System.setProperty("socksProxyPort", config.port.toString())
            
            // 2. Set global JVM Authenticator for username/password
            java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                override fun getPasswordAuthentication(): java.net.PasswordAuthentication {
                    return java.net.PasswordAuthentication(
                        config.user,
                        config.pass.toCharArray()
                    )
                }
            })
            
            // 3. AndroidX WebKit ProxyController override
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                val proxyConfig = ProxyConfig.Builder()
                    .addProxyRule("socks5://${config.host}:${config.port}")
                    .addProxyRule("socks4://${config.host}:${config.port}")
                    .addProxyRule("http://${config.host}:${config.port}")
                    .addProxyRule("https://${config.host}:${config.port}")
                    .addBypassRule("localhost")
                    .build()
                
                ProxyController.getInstance().setProxyOverride(
                    proxyConfig,
                    { run -> run.run() },
                    { /* Proxy activated */ }
                )
            }
        } else {
            // Clear settings
            System.clearProperty("socksProxyHost")
            System.clearProperty("socksProxyPort")
            System.clearProperty("http.proxyHost")
            System.clearProperty("http.proxyPort")
            System.clearProperty("https.proxyHost")
            System.clearProperty("https.proxyPort")
            java.net.Authenticator.setDefault(null)
            
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride(
                    { run -> run.run() },
                    { /* Proxy cleared */ }
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun IGViewerApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var isProxyEnabled by remember { mutableStateOf(false) }
    var remoteConfig by remember { mutableStateOf(RemoteConfig()) }
    val client = remember { OkHttpClient() }
    
    // Remote config fetching logic (5s polling) - ONLY TRACKING STATUS NOW
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val encodedUrl = "aHR0cHM6Ly9wYXN0ZWJpbi5jb20vcmF3L2luSng4elBt"
        val decodedUrl = String(Base64.decode(encodedUrl, Base64.DEFAULT))
        
        while (true) {
            try {
                val request = Request.Builder().url(decodedUrl).build()
                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val newStatus = json.optString("status", "on")
                        
                        // We only update the status, proxy settings remain hardcoded
                        if (newStatus != remoteConfig.status) {
                            remoteConfig = remoteConfig.copy(status = newStatus)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(5000)
        }
    }
    
    // Remember custom WebView instance to retain state and prevent recreation on layout redraw
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                javaScriptCanOpenWindowsAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // Set modern User Agent so Instagram is loaded in a full-featured mobile view
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            }
            
            // Set up clean cookies
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
            
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false // Intercept navigation within this WebView itself
                }
                
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                }

                override fun onReceivedHttpAuthRequest(
                    view: WebView?,
                    handler: android.webkit.HttpAuthHandler?,
                    host: String?,
                    realm: String?
                ) {
                    // Force credentials if proxy is enabled, as challenges might come from the proxy server
                    if (isProxyEnabled && !remoteConfig.user.isNullOrEmpty()) {
                        handler?.proceed(remoteConfig.user, remoteConfig.pass)
                    } else {
                        super.onReceivedHttpAuthRequest(view, handler, host, realm)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    if (isProxyEnabled && errorCode == -5) { // ERROR_PROXY_AUTHENTICATION_FAILED
                        Toast.makeText(context, "Proxy Auth Failed. Check credentials.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            // Pre-fill auth database to help WebView handle challenges automatically
            setHttpAuthUsernamePassword(remoteConfig.host, "", remoteConfig.user, remoteConfig.pass)
            
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress / 100f
                }
            }
            
            loadUrl("https://www.instagram.com/")
        }
    }
    
    // Back navigation support inside the WebView context
    BackHandler(enabled = webView.canGoBack()) {
        webView.goBack()
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. Sleek Header Section with Gradient Progress Bar
            HeaderSection(
                progress = progress,
                onHiddenButtonClick = { webView.loadUrl("https://whoer.net/") }
            )
            
            // 2. Main WebView Container (Beautifully containerized with rounded corners & border)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0A0A0A))
                    .padding(horizontal = 12.dp)
                    .border(
                        width = 1.dp,
                        color = Color(0x14FFFFFF),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF121212))
            ) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // 3. Floating glassmorphic bottom navigation panel containing action controls
            BottomControlPanel(
                isProxyEnabled = isProxyEnabled,
                onProxyToggle = { enabled ->
                    isProxyEnabled = enabled
                    updateProxySettings(enabled, remoteConfig)
                    // webView.reload() // Removed auto-reload per user request
                },
                onClearData = {
                    webView.clearCache(true)
                    WebStorage.getInstance().deleteAllData()
                    
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.removeAllCookies {
                        cookieManager.flush()
                        webView.post {
                            webView.loadUrl("https://www.instagram.com/")
                        }
                    }
                },
                onCopyCookies = {
                    val cookieManager = CookieManager.getInstance()
                    val cookies = cookieManager.getCookie("https://www.instagram.com/")
                    
                    if (!cookies.isNullOrBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Instagram Cookies", cookies)
                        clipboard.setPrimaryClip(clip)
                    }
                },
                onRefresh = {
                    webView.reload()
                }
            )
        }

        // Offline Overlay
        if (remoteConfig.status == "off") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0A))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color(0x1AEE5350), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = null,
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "System Offline",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "The service is currently unavailable. Please check back later.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderSection(
    progress: Float,
    onHiddenButtonClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // Gradient background container for logo
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF1976D2), Color(0xFF8E24AA))
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SleekInstagramLogo()
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "IG Viewer",
                    color = Color(0xFFF1F5F9),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Hidden Whoer.net Button (Almost invisible stealth button)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x02FFFFFF), CircleShape)
                        .clickable { onHiddenButtonClick() }
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        
        // Glowy Gradient Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color(0x0DFFFFFF), shape = CircleShape)
        ) {
            if (progress > 0f && progress < 1f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF2196F3),
                                    Color(0xFF9C27B0),
                                    Color(0xFF2196F3)
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
fun CustomToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val trackWidth = 44.dp
    val trackHeight = 24.dp
    val thumbSize = 18.dp
    val padding = 3.dp
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Smooth transition animations
    val thumbPosition by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "thumbPosition"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(width = trackWidth, height = trackHeight)
            .background(
                color = if (checked) Color(0xFF1E88E5) else Color(0x33FFFFFF),
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                color = if (checked) Color(0x33FFFFFF) else Color(0x1AFFFFFF),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = padding + (trackWidth - thumbSize - padding * 2) * thumbPosition)
                .size(thumbSize)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
fun SleekInstagramLogo() {
    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Camera outer border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, Color.White, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Lens circle
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .border(1.5.dp, Color.White, CircleShape)
            )
            // Small flash dot
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(2.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@Composable
fun BottomControlPanel(
    isProxyEnabled: Boolean,
    onProxyToggle: (Boolean) -> Unit,
    onClearData: () -> Unit,
    onCopyCookies: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF161616))
            .border(BorderStroke(1.dp, Color(0x0DFFFFFF)), shape = RoundedCornerShape(0.dp))
            .padding(top = 16.dp, bottom = 24.dp, start = 12.dp, end = 12.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Button 1: Refresh
            ArtisticControlButton(
                text = "Refresh",
                icon = Icons.Default.Refresh,
                styleType = ButtonStyleType.Secondary,
                onClick = onRefresh,
                modifier = Modifier.weight(0.9f)
            )

            // Button 2: Copy Cookies
            ArtisticControlButton(
                text = "Cookies",
                icon = Icons.Default.ContentCopy,
                styleType = ButtonStyleType.Primary,
                onClick = onCopyCookies,
                modifier = Modifier.weight(1.1f)
            )

            // Button 3: Integrated Clear + Proxy Disguised Utility
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Color(0x0DFFFFFF))
                    .border(BorderStroke(1.dp, Color(0x1AFFFFFF)), RoundedCornerShape(25.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Disguised proxy toggle (Looks like a status dot)
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                color = if (isProxyEnabled) Color(0xFF4CAF50) else Color(0x33FFFFFF),
                                shape = CircleShape
                            )
                            .clickable { onProxyToggle(!isProxyEnabled) }
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // Clear action
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onClearData() }
                            .padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear",
                            tint = Color(0xFFEF5350).copy(alpha = 0.9f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "CLEAR",
                            color = Color(0xFFEF5350).copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtisticControlButton(
    text: String,
    icon: ImageVector,
    styleType: ButtonStyleType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "btnScale"
    )
    
    val containerModifier = modifier
        .scale(scale)
        .height(64.dp)
        .clip(RoundedCornerShape(25.dp))
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            onClick = onClick
        )
        
    when (styleType) {
        ButtonStyleType.Primary -> {
            Box(
                modifier = containerModifier
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1E88E5), Color(0xFF7B1FA2))
                        )
                    )
                    .border(BorderStroke(1.dp, Color(0x33FFFFFF)), RoundedCornerShape(25.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = text,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = text.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        maxLines = 1
                    )
                }
            }
        }
        ButtonStyleType.Secondary -> {
            Box(
                modifier = containerModifier
                    .background(Color(0x0DFFFFFF))
                    .border(BorderStroke(1.dp, Color(0x1AFFFFFF)), RoundedCornerShape(25.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF2196F3), Color(0xFF9C27B0))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = text,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = text.uppercase(),
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp,
                        letterSpacing = 0.5.sp,
                        maxLines = 1
                    )
                }
            }
        }
        ButtonStyleType.Alert -> {
            Box(
                modifier = containerModifier
                    .background(Color(0x0DFFFFFF))
                    .border(BorderStroke(1.dp, Color(0x1AFFFFFF)), RoundedCornerShape(25.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0x1F2A2A2A), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = text,
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = text.uppercase(),
                        color = Color(0xFFEF5350).copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp,
                        letterSpacing = 0.5.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
