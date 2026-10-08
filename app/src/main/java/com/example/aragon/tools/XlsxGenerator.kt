package com.example.aragon.tools

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * High-fidelity native XLSX generator producing 100% compliant OpenXML Excel workbooks.
 */
object XlsxGenerator {

    data class XlsxContent(
        val sheetName: String = "Sheet1",
        val headers: List<String>,
        val rows: List<List<String>>
    )

    fun createWorkbook(outputFile: File, content: XlsxContent) {
        outputFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            // 1. [Content_Types].xml
            addZipEntry(zos, "[Content_Types].xml", contentTypesXml())

            // 2. _rels/.rels
            addZipEntry(zos, "_rels/.rels", rootRelsXml())

            // 3. xl/_rels/workbook.xml.rels
            addZipEntry(zos, "xl/_rels/workbook.xml.rels", workbookRelsXml())

            // 4. xl/workbook.xml
            addZipEntry(zos, "xl/workbook.xml", workbookXml(content.sheetName))

            // 5. xl/worksheets/sheet1.xml
            addZipEntry(zos, "xl/worksheets/sheet1.xml", worksheetXml(content))

            // 6. xl/styles.xml
            addZipEntry(zos, "xl/styles.xml", stylesXml())

            // 7. docProps/core.xml
            addZipEntry(zos, "docProps/core.xml", corePropsXml(content.sheetName))
        }
    }

    private fun addZipEntry(zos: ZipOutputStream, path: String, xmlContent: String) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        zos.write(xmlContent.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun contentTypesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>"""

    private fun rootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>"""

    private fun workbookRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun workbookXml(sheetName: String): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="${escapeXml(sheetName)}" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

    private fun stylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><name val="Calibri"/></font>
  </fonts>
  <fills count="2">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
</styleSheet>"""

    private fun corePropsXml(title: String): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>${escapeXml(title)}</dc:title>
  <dc:creator>Aragon Autonomous Agent</dc:creator>
  <cp:lastModifiedBy>Aragon</cp:lastModifiedBy>
  <dcterms:created xsi:type="dcterms:W3CDTF">2026-10-07T00:00:00Z</dcterms:created>
</cp:coreProperties>"""

    private fun worksheetXml(content: XlsxContent): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetData>""")

        var rowIndex = 1

        // Header Row
        if (content.headers.isNotEmpty()) {
            append("""<row r="$rowIndex">""")
            content.headers.forEachIndexed { colIdx, header ->
                val colRef = getColumnLetter(colIdx + 1) + rowIndex
                append("""<c r="$colRef" t="inlineStr" s="1"><is><t>${escapeXml(header)}</t></is></c>""")
            }
            append("""</row>""")
            rowIndex++
        }

        // Data Rows
        for (row in content.rows) {
            append("""<row r="$rowIndex">""")
            row.forEachIndexed { colIdx, cellValue ->
                val colRef = getColumnLetter(colIdx + 1) + rowIndex
                val num = cellValue.toDoubleOrNull()
                if (num != null && !cellValue.startsWith("0") || cellValue == "0") {
                    append("""<c r="$colRef" t="n"><v>$cellValue</v></c>""")
                } else {
                    append("""<c r="$colRef" t="inlineStr"><is><t>${escapeXml(cellValue)}</t></is></c>""")
                }
            }
            append("""</row>""")
            rowIndex++
        }

        append("""</sheetData>""")
        append("""</worksheet>""")
    }

    private fun getColumnLetter(colNumber: Int): String {
        var num = colNumber
        val sb = StringBuilder()
        while (num > 0) {
            val rem = (num - 1) % 26
            sb.append(('A'.code + rem).toChar())
            num = (num - 1) / 26
        }
        return sb.reverse().toString()
    }
}
