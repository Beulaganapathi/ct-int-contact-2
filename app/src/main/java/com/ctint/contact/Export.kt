package com.ctint.contact

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

const val PDF_MIME = "application/pdf"
const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

object Export {

    private fun today(): String = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date())

    private fun outFile(context: Context, name: String): File {
        val d = File(context.cacheDir, "exports")
        d.mkdirs()
        val f = File(d, name)
        if (f.exists()) f.delete()
        return f
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", file)

    /** Opens the share sheet for a file we made. */
    fun share(context: Context, file: File, mime: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    }

    // ------------------------------------------------------------------ PDF

    fun strengthPdf(context: Context, dir: Directory): File {
        val file = outFile(context, "Cadre-Strength-${today()}.pdf")
        val doc = PdfDocument()
        val w = 595
        val h = 842
        val left = 40f
        val right = w - 40f

        val normal = Paint().apply { textSize = 11f; isAntiAlias = true }
        val bold = Paint().apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val title = Paint().apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val line = Paint().apply { strokeWidth = 0.7f }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var c = page?.canvas
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            c = page!!.canvas
            y = 50f
        }

        fun ensure(space: Float) {
            if (page == null || y + space > h - 40f) newPage()
        }

        fun text(t: String, p: Paint, x: Float = left) {
            c!!.drawText(t, x, y, p)
        }

        fun row(c1: String, c2: String, c3: String, c4: String, p: Paint) {
            ensure(20f)
            text(c1, p)
            val xs = floatArrayOf(330f, 410f, 480f, right)
            val vals = arrayOf(c1, c2, c3, c4)
            for (i in 1..3) {
                val v = vals[i]
                c!!.drawText(v, xs[i] - p.measureText(v), y, p)
            }
            y += 6f
            c!!.drawLine(left, y, right, y, line)
            y += 14f
        }

        fun table(heading: String, totalLabel: String, rows: List<CadreStrength>) {
            ensure(70f)
            text(heading, title)
            y += 18f
            row("Post", "Sanctioned", "Filled", "Vacant", bold)
            rows.forEach { cs ->
                row(cs.cadre.short, cs.strength.sanctioned.toString(), cs.strength.filled.toString(), cs.strength.vacant.toString(), normal)
            }
            val t = rows.totalStrength()
            row(totalLabel, t.sanctioned.toString(), t.filled.toString(), t.vacant.toString(), bold)
            y += 8f
        }

        newPage()
        text(dir.office, title); y += 18f
        text(dir.division, normal); y += 16f
        text("Cadre strength - in position as on ${today()}", bold); y += 24f

        val tot = dir.total
        text("Overall:  Sanctioned ${tot.sanctioned}   In position ${tot.filled}   Vacant ${tot.vacant}", bold)
        y += 26f

        table("Officers", "Total officers", dir.officerStrengths)
        table("Office staff", "Total staff", dir.staffStrengths)

        ensure(60f)
        text("Unit-wise strength (filled / sanctioned)", title)
        y += 18f
        dir.units.forEach { u ->
            ensure(20f)
            text(u.unit, normal)
            val v = "${u.strength.filled} / ${u.strength.sanctioned}"
            c!!.drawText(v, right - normal.measureText(v), y, normal)
            y += 6f
            c!!.drawLine(left, y, right, y, line)
            y += 14f
        }

        page?.let { doc.finishPage(it) }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    // ----------------------------------------------------------------- Excel

    private fun esc(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 32 && ch != '\n' && ch != '\t' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun colName(i: Int): String = ('A' + i).toString()

    private fun sheetXml(rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<cols><col min=\"1\" max=\"1\" width=\"6\" customWidth=\"1\"/>")
        sb.append("<col min=\"2\" max=\"2\" width=\"26\" customWidth=\"1\"/>")
        sb.append("<col min=\"3\" max=\"4\" width=\"22\" customWidth=\"1\"/>")
        sb.append("<col min=\"5\" max=\"5\" width=\"30\" customWidth=\"1\"/>")
        sb.append("<col min=\"6\" max=\"6\" width=\"16\" customWidth=\"1\"/></cols>")
        sb.append("<sheetData>")
        rows.forEachIndexed { r, cells ->
            sb.append("<row r=\"${r + 1}\">")
            cells.forEachIndexed { i, v ->
                sb.append("<c r=\"${colName(i)}${r + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                sb.append(esc(v))
                sb.append("</t></is></c>")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    fun contactsXlsx(context: Context, dir: Directory): File {
        val file = outFile(context, "Contact-List-${today()}.xlsx")
        val rows = ArrayList<List<String>>()
        rows.add(listOf("Sl", "Name", "Cadre", "Post", "Unit", "Mobile"))
        dir.ordered.forEachIndexed { i, s ->
            val name = if (s.isVacant) "Vacant" else s.name
            rows.add(listOf((i + 1).toString(), name, s.cadre, s.post, s.group, s.mobile))
        }
        val xmlHead = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        ZipOutputStream(file.outputStream().buffered()).use { z ->
            fun put(name: String, body: String) {
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            put(
                "[Content_Types].xml",
                xmlHead + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                    "</Types>"
            )
            put(
                "_rels/.rels",
                xmlHead + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>"
            )
            put(
                "xl/workbook.xml",
                xmlHead + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<sheets><sheet name=\"Contacts\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
            )
            put(
                "xl/_rels/workbook.xml.rels",
                xmlHead + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                    "</Relationships>"
            )
            put("xl/worksheets/sheet1.xml", sheetXml(rows))
        }
        return file
    }
}
