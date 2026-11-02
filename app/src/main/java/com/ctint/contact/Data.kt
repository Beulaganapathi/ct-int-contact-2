package com.ctint.contact

import org.json.JSONArray
import org.json.JSONObject

/** A group of posts, e.g. "State Tax Officer" (short label "STO"). Its sanctioned strength = its posts. */
data class Cadre(val name: String, val short: String)

data class Staff(
    val sl: Int,
    val group: String,
    /** Name of the cadre this post belongs to (matches Cadre.name). */
    val cadre: String,
    /** Designation line, e.g. "Deputy State Tax Officer - 1". Empty = show the cadre name. */
    val post: String,
    /** Name exactly as stored, e.g. "M. Vijay Perumal (Direct)", or "Vacant" for an empty post. */
    val rawName: String,
    val mobile: String
) {
    val isVacant: Boolean = rawName.trim().equals("Vacant", ignoreCase = true)

    /** "(Direct)" in the sheet marks direct-recruit officers; shown as a tag instead. */
    val isDirect: Boolean = rawName.contains("(Direct)", ignoreCase = true)
    val name: String = rawName.replace("(Direct)", "", ignoreCase = true).trim()
    val designation: String get() = post.ifBlank { cadre }
}

/** Sanctioned posts versus officers in position. */
data class Strength(val sanctioned: Int, val filled: Int) {
    val vacant: Int get() = (sanctioned - filled).coerceAtLeast(0)
    val percent: Float
        get() = if (sanctioned == 0) 0f else (filled.toFloat() / sanctioned).coerceAtMost(1f)
}

fun List<CadreStrength>.totalStrength(): Strength =
    Strength(sumOf { it.strength.sanctioned }, sumOf { it.strength.filled })

data class CadreStrength(val cadre: Cadre, val strength: Strength)
data class UnitStrength(val unit: String, val strength: Strength)

/** Display order of cadres on every screen. Cadres not listed here come after, in their own order. */
private val CADRE_ORDER = listOf(
    "Joint Commissioner(ST)", "Deputy Commissioner(ST)", "Assistant Commissioner",
    "State Tax Officer", "Deputy State Tax Officer",
    "Assistant", "Junior Assistant", "Steno Typist", "Typist", "RC", "OA", "Driver"
)

/** Cadres shown in the first table (officers). All other cadres go to the second table (office staff). */
val OFFICER_CADRES = setOf(
    "Joint Commissioner(ST)", "Deputy Commissioner(ST)", "Assistant Commissioner",
    "State Tax Officer", "Deputy State Tax Officer"
)

private fun cadreRank(name: String): Int =
    CADRE_ORDER.indexOf(name).let { if (it < 0) CADRE_ORDER.size else it }

data class Directory(
    val office: String,
    val division: String,
    val asOn: String,
    val cadres: List<Cadre>,
    val staff: List<Staff>
) {
    /**
     * Every row is a post. Sanctioned = posts of the cadre (filled + vacant),
     * Filled = posts that have an officer. Adding an officer to a vacant post only raises Filled.
     */
    val orderedCadres: List<Cadre> = cadres.sortedBy { cadreRank(it.name) }

    /** Officers in display order: by cadre rank, then the saved order. */
    val ordered: List<Staff> = staff.sortedBy { cadreRank(it.cadre) }

    val cadreStrengths: List<CadreStrength> = orderedCadres.map { c ->
        val rows = staff.filter { it.cadre == c.name }
        CadreStrength(c, Strength(rows.size, rows.count { !it.isVacant }))
    }

    val officerStrengths: List<CadreStrength> = cadreStrengths.filter { it.cadre.name in OFFICER_CADRES }
    val staffStrengths: List<CadreStrength> = cadreStrengths.filter { it.cadre.name !in OFFICER_CADRES }

    val total: Strength = Strength(staff.size, staff.count { !it.isVacant })

    /** Units in list order (first appearance). */
    val units: List<UnitStrength> =
        ordered.groupBy { it.group }.map { (unit, members) ->
            UnitStrength(unit, Strength(members.size, members.count { !it.isVacant }))
        }

    fun toJson(): String {
        val cadreArr = JSONArray()
        cadres.forEach { c ->
            cadreArr.put(JSONObject().put("name", c.name).put("short", c.short))
        }
        val staffArr = JSONArray()
        staff.forEach { s ->
            staffArr.put(
                JSONObject()
                    .put("sl", s.sl)
                    .put("group", s.group)
                    .put("cadre", s.cadre)
                    .put("post", s.post)
                    .put("name", s.rawName)
                    .put("mobile", s.mobile)
            )
        }
        return JSONObject()
            .put("office", office)
            .put("division", division)
            .put("asOn", asOn)
            .put("cadres", cadreArr)
            .put("staff", staffArr)
            .toString(1)
    }

    companion object {
        private const val STO = "State Tax Officer"
        private const val DSTO = "Deputy State Tax Officer"

        /**
         * Reads the current format and the first format (no "cadres" list), so lists saved by
         * earlier versions of the app keep working. Throws if the text is not a valid directory.
         */
        fun parse(text: String): Directory {
            val json = JSONObject(text)
            val arr = json.getJSONArray("staff")
            val office = json.optString("office")
            val division = json.optString("division")
            val asOn = json.optString("asOn")

            fun shortOf(name: String) = when (name) {
                STO -> "STO"
                DSTO -> "DSTO"
                else -> name
            }

            if (json.has("cadres")) {
                val cArr = json.getJSONArray("cadres")
                val cadres = List(cArr.length()) { i ->
                    val o = cArr.getJSONObject(i)
                    val name = o.getString("name")
                    Cadre(name, o.optString("short", "").ifBlank { shortOf(name) })
                }
                val staff = List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    Staff(
                        sl = o.getInt("sl"),
                        group = o.getString("group"),
                        cadre = o.getString("cadre"),
                        post = o.optString("post", ""),
                        rawName = o.getString("name"),
                        mobile = o.optString("mobile", "").trim()
                    )
                }
                return Directory(office, division, asOn, cadres, staff)
            }

            // First format: no cadre list. Rows starting with "Deputy" are DSTO, the rest STO.
            val staff = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val post = o.getString("post")
                Staff(
                    sl = o.getInt("sl"),
                    group = o.getString("group"),
                    cadre = if (post.startsWith("Deputy", ignoreCase = true)) DSTO else STO,
                    post = post,
                    rawName = o.getString("name"),
                    mobile = o.optString("mobile", "").trim()
                )
            }
            return Directory(office, division, asOn, listOf(Cadre(STO, "STO"), Cadre(DSTO, "DSTO")), staff)
        }
    }
}
