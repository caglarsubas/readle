package com.readle.reader

import android.content.Context
import android.text.TextUtils
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.Calendar
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Make a reflowable text edition with a stable spine item for each physical PDF page. */
internal object PdfEpubConverter {
    private const val MAX_PAGES = 1_000
    private const val MAX_TEXT = 8 * 1024 * 1024
    private const val MAX_PAGE_TEXT = 512 * 1024
    private val paragraphs = Regex("\\n[ \\t]*\\n+")
    private val lineBreak = Regex("[ \\t]*\\n[ \\t]*")
    private val substantiveText = Regex("\\p{L}")
    private val invalidXml = Regex("[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD\\x{10000}-\\x{10FFFF}]")
    private val pageHref = Regex("(?:^|/)pdf-page-(\\d+)\\.xhtml$")
    private val zipTime = Calendar.getInstance().apply { clear(); set(2000, 0, 1, 0, 0) }.timeInMillis

    fun pageIndex(href: String): Int? =
        pageHref.find(href.substringBefore('#'))?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it in 1..MAX_PAGES }?.minus(1)

    fun write(context: Context, source: File, destination: File, title: String, sourceVersion: String) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val scratch = MemoryUsageSetting.setupMixed(16L * 1024 * 1024, 256L * 1024 * 1024)
            .setTempDir(context.cacheDir)
        try {
            PDDocument.load(source, scratch).use { pdf ->
                require(pdf.currentAccessPermission.canExtractContent()) {
                    "This PDF does not allow text extraction."
                }
                val pageCount = pdf.numberOfPages
                require(pageCount in 1..MAX_PAGES) { "Convert a PDF with up to $MAX_PAGES pages." }
                val language = pdf.documentCatalog.language?.takeIf {
                    Regex("[a-zA-Z]{2,8}(?:-[a-zA-Z0-9]{1,8})*").matches(it)
                } ?: "und"
                val extractor = PDFTextStripper().apply {
                    sortByPosition = true
                    lineSeparator = "\n"
                    paragraphStart = "\n\n"
                    paragraphEnd = ""
                    pageStart = ""
                    pageEnd = ""
                }
                ZipOutputStream(destination.outputStream().buffered()).use { epub ->
                    epub.writeMimetype()
                    epub.putText("META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
                        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                        <rootfiles><rootfile full-path="EPUB/book.opf" media-type="application/oebps-package+xml"/></rootfiles>
                        </container>
                    """.trimIndent())
                    epub.putText("EPUB/book.opf", packageDocument(title, language, sourceVersion, pageCount))
                    val navigation = (1..pageCount).joinToString("\n") {
                        """<li><a href="pdf-page-$it.xhtml">PDF page $it</a></li>"""
                    }
                    epub.putText("EPUB/nav.xhtml", xhtml("Contents", language, """
                        <nav epub:type="toc"><h1>Contents</h1><ol>$navigation</ol></nav>
                        <nav epub:type="page-list" hidden="hidden"><h2>Original PDF pages</h2><ol>$navigation</ol></nav>
                    """.trimIndent()))
                    var totalText = 0L
                    var readablePages = 0
                    for (number in 1..pageCount) {
                        extractor.startPage = number
                        extractor.endPage = number
                        val text = extractor.getText(pdf).trim()
                        totalText += text.length
                        require(text.length <= MAX_PAGE_TEXT && totalText <= MAX_TEXT) {
                            "This PDF contains too much text to convert on this device."
                        }
                        if (substantiveText.containsMatchIn(text)) readablePages++
                        val content = if (text.isNotBlank()) {
                            text.split(paragraphs).filter { it.isNotBlank() }.joinToString("\n") {
                                "<p>${xml(it.trim().replace(lineBreak, " "))}</p>"
                            }
                        } else {
                            "<p>No selectable text on this page. Use Original page to view it.</p>"
                        }
                        val label = "PDF page $number"
                        epub.putText("EPUB/pdf-page-$number.xhtml", xhtml(label, language, """
                            <div epub:type="pagebreak" role="doc-pagebreak" id="page-$number" title="$number"/>
                            <h1>$label</h1>
                            $content
                        """.trimIndent()))
                    }
                    require(readablePages > 0) {
                        "This PDF has no selectable text. Use a copy with OCR to convert it."
                    }
                }
            }
        } catch (_: InvalidPasswordException) {
            throw IllegalArgumentException("Unlock this PDF before converting it.")
        }
    }

    private fun packageDocument(title: String, language: String, sourceVersion: String, pages: Int): String {
        val manifest = (1..pages).joinToString("\n") {
            """<item id="p$it" href="pdf-page-$it.xhtml" media-type="application/xhtml+xml"/>"""
        }
        val spine = (1..pages).joinToString("\n") { """<itemref idref="p$it"/>""" }
        return """<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="id">urn:readle:pdf-epub:v1:$sourceVersion</dc:identifier>
            <dc:title>${xml(title)}</dc:title><dc:language>$language</dc:language>
            <meta property="dcterms:modified">2026-11-13T00:00:00Z</meta>
            </metadata>
            <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            $manifest</manifest><spine>$spine</spine></package>
        """.trimIndent()
    }

    private fun xhtml(title: String, language: String, body: String) =
        """<?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="$language" lang="$language">
        <head><title>${xml(title)}</title></head><body>$body</body></html>""".trimIndent()

    private fun xml(text: String): String = TextUtils.htmlEncode(text.replace(invalidXml, ""))

    private fun ZipOutputStream.writeMimetype() {
        val bytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        putNextEntry(ZipEntry("mimetype").apply {
            time = zipTime
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = size
            crc = CRC32().apply { update(bytes) }.value
        })
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.putText(name: String, content: String) {
        putNextEntry(ZipEntry(name).apply { time = zipTime })
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
