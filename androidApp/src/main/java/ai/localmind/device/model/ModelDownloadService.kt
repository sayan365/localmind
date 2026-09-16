package ai.localmind.device.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ModelDownloadService : Service() {
    private val cancelled = AtomicBoolean(false)
    private var workers = Executors.newFixedThreadPool(MODEL_DOWNLOAD_WORKERS)
    private lateinit var store: LocalModelStore
    private var model: LocalModelSpec? = null
    private var lastNotificationAt = 0L

    override fun onCreate() {
        super.onCreate()
        store = LocalModelStore(applicationContext)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelDownload()
            return START_NOT_STICKY
        }
        val selected = LocalModelCatalog.byId(intent?.getStringExtra(EXTRA_MODEL_ID)) ?: return START_NOT_STICKY
        if (model != null) return START_REDELIVER_INTENT
        model = selected
        startForeground(NOTIFICATION_ID, notification("Preparing ${selected.displayName}", 0, 0))
        startDownload(selected)
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelled.set(true)
        workers.shutdownNow()
        super.onDestroy()
    }

    private fun startDownload(selected: LocalModelSpec) {
        val resolverUrl = selected.downloadUrl ?: return fail(selected, "This model does not have a direct download.")
        store.beginDownload(selected)
        workers.execute {
            val sourceUrl = runCatching { resolveStorageUrl(resolverUrl) }.getOrElse {
                fail(selected, "The model server could not start the download. Please retry.")
                return@execute
            }
            val segments = modelDownloadSegments(selected.downloadBytes)
            val remaining = AtomicInteger(segments.size)
            segments.forEach { segment ->
                workers.execute {
                    val result = runCatching { downloadSegment(selected, segment, sourceUrl) }
                    if (result.isFailure && !cancelled.get()) {
                        fail(selected, "The model server interrupted the download. LocalMind will continue when you retry.")
                    } else if (remaining.decrementAndGet() == 0 && !cancelled.get()) {
                        finishDownload(selected, segments)
                    }
                }
            }
        }
    }

    // Resolve Hugging Face once. Re-resolving for every range exhausts anonymous resolver quotas.
    private fun resolveStorageUrl(resolverUrl: String): String {
        var current = URL(resolverUrl)
        repeat(MAX_REDIRECTS) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Range", "bytes=0-0")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    connection.disconnect()
                    return current.toString()
                }
                HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER, 307, 308 -> {
                    val location = connection.getHeaderField("Location") ?: error("Redirect without location")
                    current = URL(current, location)
                    connection.disconnect()
                }
                else -> {
                    val code = connection.responseCode
                    connection.disconnect()
                    error("Model resolver returned HTTP $code")
                }
            }
        }
        error("Too many model download redirects")
    }

    private fun downloadSegment(selected: LocalModelSpec, segment: ModelDownloadSegment, sourceUrl: String) {
        val target = store.downloadPartFile(selected, segment.index)
        if (target.length() > segment.byteCount) target.delete()
        var failures = 0
        while (!cancelled.get() && target.length() < segment.byteCount) {
            val start = segment.startByte + target.length()
            val bytesBeforeAttempt = target.length()
            try {
                val connection = openRangeConnection(sourceUrl, start, segment.endByteInclusive)
                try {
                    val contentRange = connection.getHeaderField("Content-Range").orEmpty()
                    check(contentRange.startsWith("bytes $start-")) { "Unexpected model range response" }
                    connection.inputStream.use { raw ->
                        BufferedInputStream(raw, BUFFER_SIZE).use { input ->
                            FileOutputStream(target, true).buffered(BUFFER_SIZE).use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var written = target.length()
                                while (!cancelled.get() && written < segment.byteCount) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    val accepted = minOf(read.toLong(), segment.byteCount - written).toInt()
                                    output.write(buffer, 0, accepted)
                                    written += accepted
                                    publishProgress(selected)
                                }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                failures = 0
            } catch (_: Exception) {
                failures = if (target.length() > bytesBeforeAttempt) 0 else failures + 1
                if (failures >= MAX_RETRIES) throw IllegalStateException("Range download failed")
                if (failures > 0) Thread.sleep(RETRY_DELAY_MS * failures)
            }
        }
        check(cancelled.get() || target.length() == segment.byteCount)
    }

    private fun openRangeConnection(sourceUrl: String, start: Long, end: Long): HttpURLConnection {
        val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Range", "bytes=$start-$end")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        check(connection.responseCode == HttpURLConnection.HTTP_PARTIAL) { "Storage server returned HTTP ${connection.responseCode}" }
        return connection
    }

    @Synchronized
    private fun publishProgress(selected: LocalModelSpec, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationAt < NOTIFICATION_INTERVAL_MS) return
        lastNotificationAt = now
        val done = store.downloadedBytes(selected)
        store.updateDownloadProgress(selected, done)
        val percent = ((done * 100L) / selected.downloadBytes).toInt()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
            NOTIFICATION_ID, notification("Downloading ${selected.displayName}", percent, 100)
        )
    }

    private fun finishDownload(selected: LocalModelSpec, segments: List<ModelDownloadSegment>) {
        store.markDownloadVerifying(selected)
        publishProgress(selected, true)
        if (store.assembleDownload(selected, segments) && store.activateDownload(selected)) {
            store.markDownloadComplete(selected)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
                NOTIFICATION_ID, notification("${selected.displayName} is ready", 100, 100)
            )
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        } else fail(selected, "The downloaded model failed integrity verification.")
    }

    @Synchronized
    private fun fail(selected: LocalModelSpec, message: String) {
        if (cancelled.getAndSet(true)) return
        store.markDownloadFailed(selected, message)
        workers.shutdownNow()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun cancelDownload() {
        cancelled.set(true)
        workers.shutdownNow()
        model?.let { store.deleteDownloadFiles(it) }
        store.clearDownloadState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(text: String, progress: Int, max: Int) = android.app.Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("LocalMind")
        .setContentText(text)
        .setOnlyAlertOnce(true)
        .setOngoing(max > 0 && progress < max)
        .apply { if (max > 0) setProgress(max, progress, false) }
        .build()

    companion object {
        const val ACTION_CANCEL = "ai.localmind.action.CANCEL_MODEL_DOWNLOAD"
        const val EXTRA_MODEL_ID = "model_id"
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 41
        private const val USER_AGENT = "LocalMind Android"
        private const val BUFFER_SIZE = 128 * 1024
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private const val MAX_RETRIES = 5
        private const val RETRY_DELAY_MS = 1_500L
        private const val NOTIFICATION_INTERVAL_MS = 1_000L
    }
}
