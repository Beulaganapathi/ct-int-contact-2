package com.ctint.contact

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Holds the editable list. The original list ships inside the app (assets/staff.json).
 * The first edit saves a copy on the phone (filesDir/staff.json); from then on the app reads that copy.
 *
 * Rules:
 *  - Sanctioned strength = number of posts. It changes only when you add or remove a post/cadre.
 *  - Adding an officer to a vacant post only raises Filled.
 */
class DirectoryStore(private val context: Context) {
    private val file = File(context.filesDir, "staff.json")

    var directory: Directory by mutableStateOf(loadInitial())
        private set

    private fun assetText(): String =
        context.assets.open("staff.json").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun loadInitial(): Directory {
        if (file.exists()) {
            try {
                return Directory.parse(file.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                // Saved copy is damaged: fall back to the original list.
            }
        }
        return Directory.parse(assetText())
    }

    private fun buildName(name: String, direct: Boolean): String =
        name.trim() + if (direct) " (Direct)" else ""

    private fun commit(newCadres: List<Cadre> = directory.cadres, newStaff: List<Staff> = directory.staff) {
        val updated = directory.copy(cadres = newCadres, staff = newStaff)
        file.writeText(updated.toJson(), Charsets.UTF_8)
        directory = updated
    }

    private fun nextSl(): Int = (directory.staff.maxOfOrNull { it.sl } ?: 0) + 1

    /** Puts new rows right after the last row of the same unit. */
    private fun insertWithUnit(list: List<Staff>, group: String, items: List<Staff>): List<Staff> {
        val idx = list.indexOfLast { it.group == group }
        return if (idx >= 0) list.toMutableList().apply { addAll(idx + 1, items) } else list + items
    }

    private fun vacantRows(group: String, cadre: String, post: String, count: Int): List<Staff> {
        val first = nextSl()
        return List(count) { i -> Staff(first + i, group, cadre, post, "Vacant", "") }
    }

    // ---- Officers -------------------------------------------------------------------------

    /** Puts an officer into a vacant post. Sanctioned stays the same; Filled goes up by one. */
    fun fillPost(target: Staff, name: String, mobile: String, direct: Boolean) {
        commit(newStaff = directory.staff.map {
            if (it.sl == target.sl) it.copy(rawName = buildName(name, direct), mobile = mobile) else it
        })
    }

    /** Creates a new post and fills it at once. Sanctioned goes up by one. */
    fun addOfficerWithNewPost(
        group: String, cadre: String, post: String, name: String, mobile: String, direct: Boolean
    ) {
        val row = Staff(nextSl(), group.trim(), cadre, post.trim(), buildName(name, direct), mobile)
        commit(newStaff = insertWithUnit(directory.staff, group.trim(), listOf(row)))
    }

    /** Edits an officer's details, or moves the post to another unit/cadre. */
    fun update(
        old: Staff, group: String, cadre: String, post: String, name: String, mobile: String, direct: Boolean
    ) {
        val s = old.copy(
            group = group.trim(), cadre = cadre, post = post.trim(),
            rawName = buildName(name, direct), mobile = mobile
        )
        commit(newStaff = directory.staff.map { if (it.sl == old.sl) s else it })
    }

    /** Officer left but the post stays: Filled goes down, Vacant goes up, Sanctioned unchanged. */
    fun markVacant(s: Staff) {
        commit(newStaff = directory.staff.map {
            if (it.sl == s.sl) it.copy(rawName = "Vacant", mobile = "") else it
        })
    }

    /** Deletes the post itself. Sanctioned goes down by one. */
    fun removePost(s: Staff) {
        commit(newStaff = directory.staff.filter { it.sl != s.sl })
    }

    // ---- Positions and cadres -------------------------------------------------------------

    /** Adds [count] vacant posts. Sanctioned for that cadre goes up by [count]. */
    fun addPosts(group: String, cadre: String, post: String, count: Int) {
        val rows = vacantRows(group.trim(), cadre, post.trim(), count)
        commit(newStaff = insertWithUnit(directory.staff, group.trim(), rows))
    }

    fun addCadre(name: String, short: String, sanctioned: Int, group: String) {
        val cadre = Cadre(name.trim(), short.trim())
        val staff = if (sanctioned > 0) {
            insertWithUnit(directory.staff, group.trim(), vacantRows(group.trim(), cadre.name, "", sanctioned))
        } else directory.staff
        commit(newCadres = directory.cadres + cadre, newStaff = staff)
    }

    /**
     * Renames a cadre and/or changes its sanctioned number.
     * More posts: new vacant posts are added to [group]. Fewer posts: only vacant posts can be removed.
     * Returns an error message, or null when saved.
     */
    fun updateCadre(old: Cadre, newName: String, newShort: String, sanctioned: Int, group: String): String? {
        val name = newName.trim()
        val rows = directory.staff.filter { it.cadre == old.name }
        var staff = directory.staff.map { if (it.cadre == old.name) it.copy(cadre = name) else it }

        if (sanctioned > rows.size) {
            val extra = vacantRows(group.trim(), name, "", sanctioned - rows.size)
            staff = insertWithUnit(staff, group.trim(), extra)
        } else if (sanctioned < rows.size) {
            val need = rows.size - sanctioned
            val vacant = staff.filter { it.cadre == name && it.isVacant }
            if (vacant.size < need) {
                return "Only ${vacant.size} vacant post(s) can be removed. Remove or move officers first."
            }
            val drop = vacant.takeLast(need).map { it.sl }.toSet()
            staff = staff.filter { it.sl !in drop }
        }
        val cadres = directory.cadres.map { if (it.name == old.name) Cadre(name, newShort.trim()) else it }
        commit(newCadres = cadres, newStaff = staff)
        return null
    }

    /** Only a cadre with no posts can be deleted. */
    fun removeCadre(c: Cadre): Boolean {
        if (directory.staff.any { it.cadre == c.name }) return false
        commit(newCadres = directory.cadres.filter { it.name != c.name })
        return true
    }

    // ---- Backup / reset -------------------------------------------------------------------

    /** Throws away all edits and goes back to the list the app shipped with. */
    fun reset() {
        file.delete()
        directory = Directory.parse(assetText())
    }

    fun backupText(): String = directory.toJson()

    /** Returns false (and changes nothing) if the text is not a valid backup. */
    fun restore(text: String): Boolean {
        return try {
            val parsed = Directory.parse(text.trim())
            if (parsed.staff.isEmpty() && parsed.cadres.isEmpty()) return false
            // Re-number so every row has a unique id even if the backup was edited by hand.
            val fixed = parsed.copy(staff = parsed.staff.mapIndexed { i, s -> s.copy(sl = i + 1) })
            file.writeText(fixed.toJson(), Charsets.UTF_8)
            directory = fixed
            true
        } catch (e: Exception) {
            false
        }
    }
}
