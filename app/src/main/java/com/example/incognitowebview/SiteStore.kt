package com.example.incognitowebview

import android.content.Context

data class Site(val id: String, val title: String, val url: String, val slot: Int)

class SiteStore(context: Context) {

    private val prefs = context.getSharedPreferences("site_registry", Context.MODE_PRIVATE)

    fun listSites(): List<Site> =
        (prefs.getStringSet(KEY_SITE_IDS, emptySet()) ?: emptySet())
            .mapNotNull { getSite(it) }
            .sortedBy { it.slot }

    fun getSite(id: String): Site? {
        val title = prefs.getString("$id.title", null) ?: return null
        val url = prefs.getString("$id.url", null) ?: return null
        val slot = prefs.getInt("$id.slot", -1)
        if (slot < 0) return null
        return Site(id, title, url, slot)
    }

    private fun findFreeSlot(maxSlots: Int): Int? {
        val usedSlots = listSites().map { it.slot }.toSet()
        for (slot in 0 until maxSlots) {
            if (slot !in usedSlots) return slot
        }
        return null
    }

    fun addSite(title: String, url: String, maxSlots: Int): Site? {
        val slot = findFreeSlot(maxSlots) ?: return null
        return writeSite(java.util.UUID.randomUUID().toString(), title, url, slot)
    }

    /** Replaces whatever occupies [oldId]'s slot with a brand-new site (new id, same slot). */
    fun replaceSite(oldId: String, title: String, url: String): Site? {
        val old = getSite(oldId) ?: return null
        removeSite(oldId)
        return writeSite(java.util.UUID.randomUUID().toString(), title, url, old.slot)
    }

    /** Updates an existing site's title/url in place, keeping its id and slot (and pinned shortcut) intact. */
    fun updateSite(id: String, title: String, url: String): Site? {
        val existing = getSite(id) ?: return null
        return writeSite(id, title, url, existing.slot)
    }

    fun removeSite(id: String) {
        val ids = (prefs.getStringSet(KEY_SITE_IDS, emptySet()) ?: emptySet()).toMutableSet()
        ids.remove(id)
        // commit() (synchronous) is required here, not apply(): the caller is about to spin up a
        // brand-new OS process for a different site slot, and that process reads this same
        // SharedPreferences file from disk on first access. apply()'s async write can otherwise
        // lose the race, so the new process finds nothing and immediately finishes.
        prefs.edit()
            .putStringSet(KEY_SITE_IDS, ids)
            .remove("$id.title")
            .remove("$id.url")
            .remove("$id.slot")
            .commit()
    }

    private fun writeSite(id: String, title: String, url: String, slot: Int): Site {
        val ids = (prefs.getStringSet(KEY_SITE_IDS, emptySet()) ?: emptySet()).toMutableSet()
        ids.add(id)
        // commit(), not apply() — see removeSite() for why this must be synchronous.
        prefs.edit()
            .putStringSet(KEY_SITE_IDS, ids)
            .putString("$id.title", title)
            .putString("$id.url", url)
            .putInt("$id.slot", slot)
            .commit()
        return Site(id, title, url, slot)
    }

    companion object {
        private const val KEY_SITE_IDS = "site_ids"
        const val MAX_SLOTS = 10
    }
}
