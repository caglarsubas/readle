package com.readle.reader

import android.content.ContentValues
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class BookImporter(private val context: Context, private val database: LibraryDatabase) {
    private val limit = 128L * 1024 * 1024

    fun register(uri: Uri, displayName: String? = null): String {
        val name =
            displayName
                ?: context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                ?: "Imported book"
        val id = UUID.randomUUID().toString()
        database.writableDatabase.insertOrThrow(
            "imports",
            null,
            ContentValues().apply {
                put("id", id)
                put("uri", uri.toString())
                put("name", name)
                put("status", "queued")
            },
        )
        return id
    }

    fun process(id: String) {
        val item =
            database.query("SELECT * FROM imports WHERE id = ?", arrayOf(id)).getJSONObject(0)
        val originalUri = Uri.parse(item.getString("uri"))
        val temporary = File.createTempFile("import-", ".part", context.cacheDir)
        try {
            setStatus(id, "validating")
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(originalUri).use { input ->
                requireNotNull(input) { "File permission unavailable. Import this file again." }
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= limit) { "File exceeds the spike's 128 MiB limit." }
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val existing =
                database
                    .query("SELECT id FROM books WHERE sourceVersion = ?", arrayOf(hash))
                    .optJSONObject(0)
            if (existing != null) {
                setStatus(id, "duplicate", bookId = existing.getString("id"))
                return
            }
            val signature =
                temporary.inputStream().use { input -> ByteArray(5).also { input.read(it) } }
            val format: String
            if (String(signature, Charsets.ISO_8859_1).startsWith("%PDF-")) {
                format = "pdf"
                ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY).use {
                    descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        require(renderer.pageCount > 0) { "PDF has no pages" }
                    }
                }
            } else {
                format = "epub"
                validateEpub(temporary)
            }
            val directory = File(context.filesDir, "books").apply { mkdirs() }
            val destination = File(directory, "$hash.$format")
            temporary.copyTo(destination, overwrite = true)
            val bookId = UUID.randomUUID().toString()
            database.writableDatabase.beginTransaction()
            try {
                database.writableDatabase.insertOrThrow(
                    "books",
                    null,
                    ContentValues().apply {
                        put("id", bookId)
                        put("title", item.getString("name").substringBeforeLast('.'))
                        put("path", destination.absolutePath)
                        put("format", format)
                        put("sourceVersion", hash)
                    },
                )
                setStatus(id, "ready", bookId = bookId)
                database.writableDatabase.setTransactionSuccessful()
            } finally {
                database.writableDatabase.endTransaction()
            }
        } catch (e: Exception) {
            setStatus(id, "failed", e.message ?: "Unsupported or unreadable file")
        } finally {
            temporary.delete()
        }
    }

    private fun validateEpub(file: File) {
        ZipFile(file).use { archive ->
            require(
                archive
                    .getEntry("mimetype")
                    ?.takeIf { it.size <= 64 }
                    ?.let {
                        archive.getInputStream(it).use { stream ->
                            val bytes = ByteArray(64)
                            val count = stream.read(bytes)
                            String(bytes, 0, count.coerceAtLeast(0), Charsets.UTF_8)
                        }
                    } == "application/epub+zip"
            ) {
                "Choose a valid, unencrypted PDF or EPUB."
            }
            require(archive.size() <= 4096) { "Archive has too many entries." }
            require(archive.getEntry("META-INF/encryption.xml") == null) {
                "Encrypted EPUB content is unsupported in this spike."
            }
            var expanded = 0L
            archive.entries().asSequence().forEach { entry ->
                require(!entry.name.startsWith('/') && !entry.name.split('/').contains("..")) {
                    "Unsafe archive path."
                }
                require(entry.size in 0..limit) { "Invalid archive entry size." }
                expanded += entry.size
                require(expanded <= limit) { "Expanded book exceeds 128 MiB." }
                if (
                    entry.name.endsWith(".xhtml") ||
                        entry.name.endsWith(".html") ||
                        entry.name.endsWith(".opf")
                ) {
                    require(entry.size <= 8L * 1024 * 1024) {
                        "Markup exceeds the 8 MiB per-resource limit."
                    }
                    val markup =
                        archive.getInputStream(entry).use { input ->
                            val bytes = input.readBytesBounded(8 * 1024 * 1024)
                            Charsets.UTF_8.newDecoder()
                                .decode(java.nio.ByteBuffer.wrap(bytes))
                                .toString()
                        }
                    require(
                        !markup.contains('\u0000') &&
                            !markup.contains("<!DOCTYPE", ignoreCase = true)
                    ) {
                        "DTD declarations and non-UTF-8 markup are unsupported."
                    }
                    val factory =
                        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                    val builder = factory.newDocumentBuilder()
                    builder.setEntityResolver { _, _ ->
                        org.xml.sax.InputSource(java.io.StringReader(""))
                    }
                    val document =
                        builder.parse(org.xml.sax.InputSource(java.io.StringReader(markup)))
                    val nodes = document.getElementsByTagName("*")
                    for (index in 0 until nodes.length) {
                        val node = nodes.item(index)
                        if (node.localName == "style") {
                            require(
                                !node.textContent.contains(Regex("(?i)(https?:|//|@import|\\\\)"))
                            ) {
                                "External stylesheet resources are unsupported."
                            }
                        }
                        require(
                            node.localName != "script" &&
                                node.localName != "iframe" &&
                                node.localName != "object"
                        ) {
                            "Active EPUB content is unsupported."
                        }
                        for (attributeIndex in 0 until node.attributes.length) {
                            val attribute = node.attributes.item(attributeIndex)
                            if (attribute.nodeName.endsWith("href") && node.localName != "a") {
                                require(
                                    !attribute.nodeValue.contains(Regex("(?i)(https?:|//|data:)"))
                                ) {
                                    "External embedded resources are unsupported."
                                }
                            }
                            require(
                                !attribute.nodeName.lowercase().startsWith("on") &&
                                    !attribute.nodeValue.lowercase().contains("javascript:")
                            ) {
                                "Scripted EPUB content is unsupported."
                            }
                            if (attribute.nodeName in listOf("src", "srcset", "style")) {
                                require(
                                    !attribute.nodeValue.contains(
                                        Regex("(?i)(https?:|//|@import|\\\\)")
                                    )
                                ) {
                                    "External EPUB resources are unsupported."
                                }
                            }
                        }
                    }
                }
                if (entry.name.endsWith(".css")) {
                    val css =
                        archive.getInputStream(entry).use {
                            String(it.readBytesBounded(8 * 1024 * 1024), Charsets.UTF_8)
                        }
                    require(!css.contains(Regex("(?i)(https?:|//|@import|\\\\)"))) {
                        "External stylesheet resources are unsupported."
                    }
                }
            }
            require(archive.getEntry("META-INF/container.xml") != null) {
                "Missing EPUB container."
            }
        }
    }

    private fun java.io.InputStream.readBytesBounded(maximum: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = read(buffer)
            if (size < 0) break
            require(output.size() + size <= maximum) { "Resource exceeds 8 MiB." }
            output.write(buffer, 0, size)
        }
        return output.toByteArray()
    }

    private fun setStatus(id: String, status: String, error: String = "", bookId: String = "") {
        database.writableDatabase.update(
            "imports",
            ContentValues().apply {
                put("status", status)
                put("error", error)
                put("bookId", bookId)
            },
            "id = ?",
            arrayOf(id),
        )
    }
}
