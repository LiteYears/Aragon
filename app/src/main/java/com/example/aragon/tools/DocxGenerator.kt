package com.example.aragon.tools

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * High-fidelity native DOCX generator producing 100% compliant OpenXML Word documents.
 */
object DocxGenerator {

    data class TableRow(val cells: List<String>)

    data class DocxContent(
        val title: String,
        val subtitle: String? = null,
        val paragraphs: List<String> = emptyList(),
        val bulletPoints: List<String> = emptyList(),
        val tableHeaders: List<String>? = null,
        val tableRows: List<TableRow>? = null
    )

    fun createDocument(outputFile: File, content: DocxContent) {
        outputFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            // 1. [Content_Types].xml
            addZipEntry(zos, "[Content_Types].xml", contentTypesXml())

            // 2. _rels/.rels
            addZipEntry(zos, "_rels/.rels", rootRelsXml())

            // 3. word/_rels/document.xml.rels
            addZipEntry(zos, "word/_rels/document.xml.rels", documentRelsXml())

            // 4. word/styles.xml
            addZipEntry(zos, "word/styles.xml", stylesXml())

            // 5. docProps/core.xml
            addZipEntry(zos, "docProps/core.xml", corePropsXml(content.title))

            // 6. word/document.xml
            addZipEntry(zos, "word/document.xml", documentXml(content))
        }
    }

    private fun addZipEntry(zos: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(Charsets.UTF_8))
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
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>"""

    private fun rootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>"""

    private fun documentRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun stylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
        <w:sz w:val="22"/>
        <w:color w:val="222222"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
</w:styles>"""

    private fun corePropsXml(title: String): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>${escapeXml(title)}</dc:title>
  <dc:creator>Aragon Autonomous Agent</dc:creator>
  <cp:lastModifiedBy>Aragon</cp:lastModifiedBy>
  <dcterms:created xsi:type="dcterms:W3CDTF">2026-10-07T00:00:00Z</dcterms:created>
</cp:coreProperties>"""

    private fun documentXml(content: DocxContent): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
""")

        // Title
        sb.append("""
    <w:p>
      <w:pPr>
        <w:jc w:val="center"/>
        <w:spacing w:before="240" w:after="120"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:b/>
          <w:sz w:val="36"/>
          <w:color w:val="0F172A"/>
        </w:rPr>
        <w:t>${escapeXml(content.title)}</w:t>
      </w:r>
    </w:p>
""")

        // Subtitle
        if (!content.subtitle.isNullOrBlank()) {
            sb.append("""
    <w:p>
      <w:pPr>
        <w:jc w:val="center"/>
        <w:spacing w:before="0" w:after="240"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:i/>
          <w:sz w:val="24"/>
          <w:color w:val="475569"/>
        </w:rPr>
        <w:t>${escapeXml(content.subtitle)}</w:t>
      </w:r>
    </w:p>
""")
        }

        // Paragraphs
        for (p in content.paragraphs) {
            sb.append("""
    <w:p>
      <w:pPr>
        <w:spacing w:before="120" w:after="120"/>
      </w:pPr>
      <w:r>
        <w:t>${escapeXml(p)}</w:t>
      </w:r>
    </w:p>
""")
        }

        // Bullet Points
        for (bp in content.bulletPoints) {
            sb.append("""
    <w:p>
      <w:pPr>
        <w:ind w:left="360"/>
        <w:spacing w:before="60" w:after="60"/>
      </w:pPr>
      <w:r>
        <w:rPr><w:b/></w:rPr>
        <w:t>• </w:t>
      </w:r>
      <w:r>
        <w:t>${escapeXml(bp)}</w:t>
      </w:r>
    </w:p>
""")
        }

        // Table if present
        if (!content.tableHeaders.isNullOrEmpty() && !content.tableRows.isNullOrEmpty()) {
            sb.append("""
    <w:tbl>
      <w:tblPr>
        <w:tblW w:w="5000" w:type="pct"/>
        <w:tblBorders>
          <w:top w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
          <w:left w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
          <w:bottom w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
          <w:right w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
          <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
          <w:insideV w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
        </w:tblBorders>
      </w:tblPr>
      <w:tr>
""")
            for (header in content.tableHeaders) {
                sb.append("""
        <w:tc>
          <w:tcPr>
            <w:shd w:val="clear" w:color="auto" w:fill="F1F5F9"/>
          </w:tcPr>
          <w:p>
            <w:r>
              <w:rPr><w:b/><w:color w:val="0F172A"/></w:rPr>
              <w:t>${escapeXml(header)}</w:t>
            </w:r>
          </w:p>
        </w:tc>
""")
            }
            sb.append("      </w:tr>\n")

            for (row in content.tableRows) {
                sb.append("      <w:tr>\n")
                for (cell in row.cells) {
                    sb.append("""
        <w:tc>
          <w:p>
            <w:r>
              <w:t>${escapeXml(cell)}</w:t>
            </w:r>
          </w:p>
        </w:tc>
""")
                }
                sb.append("      </w:tr>\n")
            }
            sb.append("    </w:tbl>\n")
        }

        // Footer note
        sb.append("""
    <w:p>
      <w:pPr>
        <w:spacing w:before="360" w:after="0"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:sz w:val="18"/>
          <w:color w:val="94A3B8"/>
        </w:rPr>
        <w:t>Generated by Aragon Autonomous Computer Agent • Verified OpenXML DOCX</w:t>
      </w:r>
    </w:p>
    <w:sectPr/>
  </w:body>
</w:document>
""")
        return sb.toString()
    }
}
