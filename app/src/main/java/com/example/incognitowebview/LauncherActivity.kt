package com.example.incognitowebview

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * The default "Null Browser" home-screen icon. Doubles as an always-available way to reach any
 * site without relying on a pinned home-screen shortcut — shortcuts are an optional convenience,
 * created per-site once its favicon (or a generated fallback) is ready.
 */
class LauncherActivity : ComponentActivity() {

    private lateinit var store: SiteStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SiteStore(this)
    }

    override fun onResume() {
        super.onResume()
        renderSiteList()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun renderSiteList() {
        val pad = dp(16)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        container.addView(TextView(this).apply {
            text = "Null Browser"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        })
        container.addView(TextView(this).apply {
            text = "Each site below runs isolated from the others. By default its browser " +
                "state (cookies, login, etc.) is preserved between opens — enable " +
                "auto-clean per-site to wipe it on every open/close instead. Tap a site " +
                "to open; long-press to remove. A home-screen shortcut is optional and " +
                "created automatically once the site's icon is ready."
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(16))
        })
        container.addView(Button(this).apply {
            text = "+ Add new site"
            setOnClickListener { promptNewSite() }
        })

        store.listSites().forEach { site ->
            val cleanState = if (site.autoClean) "auto-clean" else "state preserved"
            container.addView(TextView(this).apply {
                text = "${site.title}\n${site.url}  ·  $cleanState"
                setTextColor(Color.WHITE)
                setPadding(0, dp(16), 0, dp(16))
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { openSite(site) }
                setOnLongClickListener { confirmRemoveSite(site); true }
            })
            container.addView(View(this).apply {
                setBackgroundColor(Color.DKGRAY)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                )
            })
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(container)
        })
    }

    private fun confirmRemoveSite(site: Site) {
        AlertDialog.Builder(this)
            .setTitle("Remove \"${site.title}\"?")
            .setMessage("This deletes the site entry and disables its home-screen shortcut.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                getSystemService(ShortcutManager::class.java)
                    ?.disableShortcuts(listOf(site.id), "Site removed")
                store.removeSite(site.id)
                renderSiteList()
            }
            .show()
    }

    private fun promptNewSite() {
        val titleInput = EditText(this).apply { hint = "Title" }
        val urlInput = EditText(this).apply {
            hint = "https://example.com"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val autoCleanCheckbox = CheckBox(this).apply {
            text = "Auto-clean browser state on open/close"
            isChecked = false
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
            addView(titleInput)
            addView(urlInput)
            addView(autoCleanCheckbox)
        }
        AlertDialog.Builder(this)
            .setTitle("Add a site")
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val title = titleInput.text.toString().trim()
                val url = normalizeUrl(urlInput.text.toString())
                if (title.isNotEmpty() && url != null) {
                    createSite(title, url, autoCleanCheckbox.isChecked)
                }
            }
            .show()
    }

    private fun createSite(title: String, url: String, autoClean: Boolean = false) {
        val site = store.addSite(title, url, SiteStore.MAX_SLOTS, autoClean)
        if (site != null) {
            openSite(site)
            return
        }
        promptReplaceSite(title, url, autoClean)
    }

    private fun promptReplaceSite(title: String, url: String, autoClean: Boolean) {
        val existing = store.listSites()
        val labels = existing.map { "${it.title} (${it.url})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("All ${SiteStore.MAX_SLOTS} slots are full — pick one to replace")
            .setItems(labels) { _, which ->
                val old = existing[which]
                getSystemService(ShortcutManager::class.java)
                    ?.disableShortcuts(listOf(old.id), "Site replaced")
                val newSite = store.replaceSite(old.id, title, url, autoClean)
                if (newSite != null) openSite(newSite)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openSite(site: Site) {
        val activityClass = SITE_ACTIVITY_CLASSES[site.slot]
        startActivity(
            Intent(this, activityClass).apply {
                putExtra(SiteActivityBase.EXTRA_SITE_ID, site.id)
            }
        )
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
