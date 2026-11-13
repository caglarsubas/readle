package com.readle.reader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.ViewManager
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

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

    override fun getConstants(): Map<String, Any> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return mapOf(
            "versionName" to (info.versionName ?: ""),
            "buildNumber" to
                androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info).toString(),
        )
    }

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
        async(promise) {
            database.query(
                "SELECT * FROM books ORDER BY CASE WHEN format = 'epub' THEN 0 ELSE 1 END, title"
            )
        }

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
    fun createWalkthrough(bookId: String, cursor: ReadableMap, promise: Promise) =
        async(promise) {
            val book = database.book(bookId)
            check(book.getString("sourceVersion") == IDENTITY_PAPER_VERSION) {
                "Import the sample paper to start this walkthrough."
            }
            database.createSession(bookId, JSONObject(cursor.toHashMap()))
        }

    @ReactMethod
    fun createGeneratedWalkthrough(bookId: String, profile: ReadableMap, promise: Promise) =
        async(promise) {
            val book = database.book(bookId)
            require(book.getString("format") == "pdf") { "Choose a PDF with selectable text." }
            val source = File(book.getString("path"))
            require(source.isFile && source.length() in 1..MAX_SOURCE_BYTES) { "Choose an available PDF up to 32 MB." }
            val learner = JSONObject(profile.toHashMap())
            require(listOf("background", "goal", "preference").all {
                val field = learner.optString(it)
                field.isNotBlank() && field.length <= 500
            }) { "Complete the learner profile." }
            database.createSession(bookId, JSONObject()
                .put("version", 2).put("lesson", 0).put("pass", 1)
                .put("drafts", JSONObject()).put("submissions", JSONObject()).put("profile", learner))
        }

    @ReactMethod
    fun generationStatus(id: String, start: Boolean, promise: Promise) =
        async(promise) {
            val state = walkthroughState(id)
            val cursor = state.getJSONObject("learningCursor")
            require(cursor.getInt("version") == 2) { "Open a generated walkthrough." }
            state.optJSONObject("learningEdition")?.let {
                return@async JSONObject().put("state", "ready").put("edition", it)
            }
            val book = database.book(database.session(id).getString("bookId"))
            val connection = URL("http://127.0.0.1:8765/v1/walkthroughs/$id")
                .openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
            try {
                connection.connectTimeout = 3000
                connection.readTimeout = 12000
                connection.instanceFollowRedirects = false
                if (start) {
                    val file = File(book.getString("path"))
                    require(file.isFile && file.length() in 1..MAX_SOURCE_BYTES) {
                        "Source unavailable. Import the original PDF again."
                    }
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(file.length())
                    connection.setRequestProperty("Content-Type", "application/pdf")
                    connection.setRequestProperty("X-Readle-Source", book.getString("sourceVersion"))
                    connection.setRequestProperty("X-Readle-Profile", Base64.encodeToString(
                        cursor.getJSONObject("profile").toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                    file.inputStream().use { input -> connection.outputStream.use { input.copyTo(it) } }
                }
                val code = connection.responseCode
                if (code == 404) return@async JSONObject().put("state", "pending")
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val result = stream?.bufferedReader()?.use { JSONObject(it.readText()) }
                    ?: error("The local generator returned an invalid response.")
                if (code !in 200..299) error(result.optString("message", "The local generator declined the request."))
                if (result.optString("state") == "ready") {
                    val edition = result.getJSONObject("edition")
                    require(edition.getString("sourceVersion") == state.getString("sourceVersion")) {
                        "The generated walkthrough belongs to another source edition."
                    }
                    val returnedProfile = edition.getJSONObject("profile")
                    val savedProfile = cursor.getJSONObject("profile")
                    require(listOf("background", "goal", "preference").all {
                        returnedProfile.getString(it) == savedProfile.getString(it)
                    }) { "The generated walkthrough belongs to another learner profile." }
                    database.mutateSession(id) { it.put("learningEdition", edition) }
                }
                result
            } catch (error: java.io.IOException) {
                Log.w("ReadleLibrary", "Local walkthrough connection failed", error)
                throw IllegalStateException("Connect this device to the Mac running the Readle generator.", error)
            } finally {
                connection.disconnect()
            }
        }

    @ReactMethod
    fun learningEdition(id: String, promise: Promise) =
        async(promise) { walkthroughState(id).optJSONObject("learningEdition") }

    @ReactMethod
    fun learningCursor(id: String, promise: Promise) =
        async(promise) { walkthroughState(id).getJSONObject("learningCursor") }

    @ReactMethod
    fun saveLearningCursor(id: String, cursor: ReadableMap, promise: Promise) =
        async(promise) {
            walkthroughState(id)
            database.mutateSession(id) { it.put("learningCursor", JSONObject(cursor.toHashMap())) }
            null
        }

    private fun walkthroughState(id: String): JSONObject {
        val session = database.session(id)
        val state = JSONObject(session.getString("state"))
        check(state.getString("mode") == "learn" &&
            database.book(session.getString("bookId")).getString("sourceVersion") == state.getString("sourceVersion")) {
            "This walkthrough requires its original source edition."
        }
        return state
    }

    @ReactMethod
    fun openSession(id: String, promise: Promise) = openReader(id, null, promise)

    @ReactMethod
    fun openWalkthroughSource(id: String, printedPage: Int, promise: Promise) =
        openReader(id, printedPage, promise)

    private fun openReader(id: String, referencePage: Int?, promise: Promise) {
        try {
            val session = database.session(id)
            check(File(database.book(session.getString("bookId")).getString("path")).exists()) {
                "Source unavailable. Import the original file again."
            }
            if (referencePage != null) {
                val state = walkthroughState(id)
                val edition = state.optJSONObject("learningEdition")
                val valid = if (edition != null) referencePage in 0 until edition.getInt("pageCount")
                    else state.getString("sourceVersion") == IDENTITY_PAPER_VERSION && referencePage in 1..32
                require(valid) { "Source page unavailable." }
            }
            val activity = context.currentActivity ?: error("Reader activity unavailable")
            val intent = Intent(activity, ReaderActivity::class.java).putExtra("sessionId", id)
            if (referencePage != null) intent.putExtra("referencePageIndex", referencePage)
            activity.startActivity(intent)
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
            val results = JSONArray()
            listOf("readle-sample.epub", "readle-sample.pdf").forEach { name ->
                val file = File(context.cacheDir, name)
                context.assets.open("fixtures/$name").use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                val id = importer.register(Uri.fromFile(file), name)
                importer.process(id)
                results.put(database.query("SELECT * FROM imports WHERE id = ?", arrayOf(id)).getJSONObject(0))
            }
            results
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

    private companion object {
        const val MAX_SOURCE_BYTES = 32L * 1024 * 1024
        const val IDENTITY_PAPER_VERSION = "b019c9420565afe6a3f81fcd8e9b3375f546003cecab93ecfa25d965b2f68bb2"
    }
}

class LibraryPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
        listOf(LibraryModule(context))

    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
