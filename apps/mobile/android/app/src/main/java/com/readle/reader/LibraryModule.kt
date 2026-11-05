package com.readle.reader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.ViewManager
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONArray

class LibraryModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context), ActivityEventListener {
    private val database = LibraryDatabase.get(context)
    private val importer = BookImporter(context, database)
    private val executor = Executors.newSingleThreadExecutor()
    private var pending: Promise? = null

    init {
        database.writableDatabase.execSQL(
            "UPDATE imports SET status = 'failed', error = 'Import interrupted. Retry this file.' WHERE status IN ('queued', 'validating')"
        )
        context.addActivityEventListener(this)
    }

    override fun getName() = "ReadleLibrary"

    private fun async(promise: Promise, block: () -> Any?) {
        executor.execute {
            try {
                val value = block()
                promise.resolve(
                    when (value) {
                        is JSONArray -> Arguments.makeNativeArray(jsonList(value))
                        is org.json.JSONObject -> Arguments.makeNativeMap(jsonMap(value))
                        else -> value
                    }
                )
            } catch (e: Exception) {
                promise.reject("READLE_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun books(promise: Promise) =
        async(promise) { database.query("SELECT * FROM books ORDER BY title") }

    @ReactMethod
    fun sessions(bookId: String, promise: Promise) = async(promise) { database.sessions(bookId) }

    @ReactMethod fun recentSessions(promise: Promise) = async(promise) { database.sessions(null) }

    @ReactMethod
    fun importItems(promise: Promise) =
        async(promise) { database.query("SELECT * FROM imports ORDER BY rowid DESC") }

    @ReactMethod
    fun createSession(bookId: String, promise: Promise) =
        async(promise) { database.createSession(bookId) }

    @ReactMethod
    fun openSession(id: String, promise: Promise) {
        try {
            val session = database.session(id)
            check(File(database.book(session.getString("bookId")).getString("path")).exists()) {
                "Source unavailable. Import the original file again."
            }
            val activity = context.currentActivity ?: error("Reader activity unavailable")
            activity.startActivity(
                Intent(activity, ReaderActivity::class.java).putExtra("sessionId", id)
            )
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SOURCE_UNAVAILABLE", e.message)
        }
    }

    @ReactMethod
    fun retryImport(id: String, promise: Promise) =
        async(promise) {
            importer.process(id)
            database.query("SELECT * FROM imports ORDER BY rowid DESC")
        }

    @ReactMethod
    fun importFixtures(promise: Promise) =
        async(promise) {
            listOf("readle-sample.pdf", "readle-sample.epub").forEach { name ->
                val file = File(context.cacheDir, name)
                context.assets.open("fixtures/$name").use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                importer.process(importer.register(Uri.fromFile(file), name))
            }
            database.query("SELECT * FROM imports ORDER BY rowid DESC")
        }

    @ReactMethod
    fun importBooks(promise: Promise) {
        if (pending != null) {
            promise.reject("IMPORT_BUSY", "A file picker is already open.")
            return
        }
        val activity =
            context.currentActivity
                ?: run {
                    promise.reject("NO_ACTIVITY", "Open the app to import books.")
                    return
                }
        pending = promise
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("application/pdf", "application/epub+zip"),
                )
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
        activity.startActivityForResult(intent, 517)
    }

    override fun onActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        if (requestCode != 517) return
        val promise = pending ?: return
        pending = null
        if (resultCode != Activity.RESULT_OK || data == null) {
            promise.resolve(Arguments.createArray())
            return
        }
        async(promise) {
            val uris =
                data.clipData?.let { clip ->
                    (0 until clip.itemCount).map { clip.getItemAt(it).uri }
                } ?: listOfNotNull(data.data)
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: SecurityException) {
                    /* Copy while the temporary grant is active. */
                }
                importer.process(importer.register(uri))
            }
            database.query("SELECT * FROM imports ORDER BY rowid DESC")
        }
    }

    override fun onNewIntent(intent: Intent) = Unit

    override fun invalidate() {
        context.removeActivityEventListener(this)
        executor.shutdown()
        super.invalidate()
    }

    private fun jsonList(array: JSONArray): List<Any?> =
        (0 until array.length()).map { value(array.get(it)) }

    private fun jsonMap(obj: org.json.JSONObject): Map<String, Any?> =
        obj.keys().asSequence().associateWith { value(obj.get(it)) }

    private fun value(item: Any?): Any? =
        when (item) {
            org.json.JSONObject.NULL -> null
            is org.json.JSONObject -> jsonMap(item)
            is JSONArray -> jsonList(item)
            else -> item
        }
}

class LibraryPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
        listOf(LibraryModule(context))

    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
