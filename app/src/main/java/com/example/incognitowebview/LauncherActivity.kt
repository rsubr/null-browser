package com.example.incognitowebview

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.ComponentActivity

class LauncherActivity : ComponentActivity() {

    private lateinit var store: SiteStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SiteStore(this)
        promptNewSite()
    }

    private fun promptNewSite() {
        val titleInput = EditText(this).apply { hint = "Shortcut title" }
        val urlInput = EditText(this).apply {
            hint = "https://example.com"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(titleInput)
            addView(urlInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Add site to home screen")
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setPositiveButton("Create") { _, _ ->
                val title = titleInput.text.toString().trim()
                val url = normalizeUrl(urlInput.text.toString())
                if (title.isEmpty() || url == null) {
                    promptNewSite()
                } else {
                    createSite(title, url)
                }
            }
            .show()
    }

    private fun createSite(title: String, url: String) {
        val site = store.addSite(title, url, SiteStore.MAX_SLOTS)
        if (site != null) {
            launchSite(site)
            return
        }
        promptReplaceSite(title, url)
    }

    private fun promptReplaceSite(title: String, url: String) {
        val existing = store.listSites()
        val labels = existing.map { "${it.title} (${it.url})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("All ${SiteStore.MAX_SLOTS} slots are full — pick one to replace")
            .setItems(labels) { _, which ->
                val old = existing[which]
                getSystemService(ShortcutManager::class.java)
                    ?.disableShortcuts(listOf(old.id), "Site replaced")
                val newSite = store.replaceSite(old.id, title, url)
                if (newSite != null) launchSite(newSite) else finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

    private fun launchSite(site: Site) {
        val activityClass = SITE_ACTIVITY_CLASSES[site.slot]
        startActivity(
            Intent(this, activityClass).apply {
                putExtra(SiteActivityBase.EXTRA_SITE_ID, site.id)
            }
        )
        finish()
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
}
