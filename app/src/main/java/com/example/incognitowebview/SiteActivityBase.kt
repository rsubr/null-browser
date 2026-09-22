package com.example.incognitowebview

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.InputType
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

abstract class SiteActivityBase : ComponentActivity() {

    protected abstract val slotIndex: Int

    private lateinit var webView: WebView
    private lateinit var store: SiteStore
    private lateinit var site: Site

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix("site_$slotIndex")
        }
        super.onCreate(savedInstanceState)

        store = SiteStore(this)
        val siteId = intent.getStringExtra(EXTRA_SITE_ID)
        val loadedSite = siteId?.let { store.getSite(it) }
        if (loadedSite == null) {
            finish()
            return
        }
        site = loadedSite

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener { showSettingsDialog() }

        requestPermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))

        setupWebView()
        applyImmersiveMode()

        clearWebViewData()
        webView.loadUrl(site.url)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val granted = request.resources.filter { resource ->
                    when (resource) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                            hasPermission(Manifest.permission.CAMERA)
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                            hasPermission(Manifest.permission.RECORD_AUDIO)
                        else -> false
                    }
                }.toTypedArray()
                runOnUiThread {
                    if (granted.isNotEmpty()) request.grant(granted) else request.deny()
                }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                if (icon != null) updateSiteShortcut(icon)
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun updateSiteShortcut(favicon: Bitmap) {
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
        val launchIntent = Intent(this, this.javaClass).apply {
            action = Intent.ACTION_MAIN
            putExtra(EXTRA_SITE_ID, site.id)
        }
        val shortcut = ShortcutInfo.Builder(this, site.id)
            .setShortLabel(site.title)
            .setIcon(Icon.createWithAdaptiveBitmap(favicon))
            .setIntent(launchIntent)
            .build()

        if (shortcutManager.pinnedShortcuts.any { it.id == site.id }) {
            shortcutManager.updateShortcuts(listOf(shortcut))
        } else if (shortcutManager.isRequestPinShortcutSupported) {
            shortcutManager.requestPinShortcut(shortcut, null)
        }
    }

    private fun showSettingsDialog() {
        val options = arrayOf("Edit site", "Clear data & restart", "Close")
        AlertDialog.Builder(this)
            .setTitle(site.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> promptEditSite()
                    1 -> {
                        clearWebViewData()
                        webView.loadUrl(site.url)
                    }
                    2 -> finish()
                }
            }
            .show()
    }

    private fun promptEditSite() {
        val titleInput = EditText(this).apply { hint = "Title" ; setText(site.title) }
        val urlInput = EditText(this).apply {
            hint = "https://example.com"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(site.url)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(titleInput)
            addView(urlInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit site")
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = titleInput.text.toString().trim().ifEmpty { site.title }
                val newUrl = normalizeUrl(urlInput.text.toString()) ?: site.url
                store.updateSite(site.id, newTitle, newUrl)?.let { updated ->
                    site = updated
                    clearWebViewData()
                    webView.loadUrl(site.url)
                }
            }
            .show()
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun clearWebViewData() {
        webView.clearHistory()
        webView.clearCache(true)
        webView.clearFormData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        deleteDatabase("webview.db")
        deleteDatabase("webviewCache.db")
    }

    override fun onDestroy() {
        clearWebViewData()
        webView.destroy()
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    companion object {
        const val EXTRA_SITE_ID = "site_id"
    }
}
