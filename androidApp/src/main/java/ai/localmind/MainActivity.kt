package ai.localmind

import ai.localmind.device.SharedContentStore
import ai.localmind.device.BasicDeviceAction
import ai.localmind.device.BasicDeviceActionParser
import ai.localmind.device.BasicDeviceActions
import ai.localmind.device.DeviceActionResult
import ai.localmind.device.AndroidCalendarConnector
import ai.localmind.device.WhatsAppHandoff
import ai.localmind.device.EmailHandoff
import ai.localmind.device.model.*
import ai.localmind.core.CalendarEvent
import ai.localmind.ui.ResponseFormatter
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.DownloadManager
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var modelStore: LocalModelStore
    private lateinit var selectedModel: LocalModelSpec
    private lateinit var scroll: ScrollView
    private lateinit var messages: LinearLayout
    private lateinit var modelPanel: LinearLayout
    private lateinit var starterPrompts: View
    private lateinit var tracePanel: LinearLayout
    private lateinit var traceList: LinearLayout
    private lateinit var overflowButton: ImageButton
    private lateinit var modelBadge: TextView
    private lateinit var requestInput: EditText
    private lateinit var sendButton: ImageButton
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fileExecutor = Executors.newSingleThreadExecutor()
    private val runtimeCleanupExecutor = Executors.newCachedThreadPool()
    private val activityItems = mutableListOf<Pair<String, String>>()
    private var chatEngine: OnDeviceChatEngine? = null
    private var legacyDownloadId: Long? = null
    private var downloadSampleStartedAt = 0L
    private var downloadSampleStartBytes = 0L
    private var downloadBytesPerSecond = 0L
    private var modelReady = false
    private var generating = false
    private var modelLoadAttempt = 0
    private var pendingModelNotice: String? = null
    private var pendingPermissionAction: BasicDeviceAction.SetTorch? = null
    private var pendingCalendarAction: BasicDeviceAction.CreateCalendarEvent? = null
    private var lastBasicAction: BasicDeviceAction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modelStore = LocalModelStore(this)
        selectedModel = modelStore.selected(recommendedModel())
        pendingModelNotice = intent.getStringExtra(EXTRA_MODEL_NOTICE)
        setContentView(buildContent())
        if (intent.getBooleanExtra(EXTRA_SHARED_CONTENT_ADDED, false)) notifySharedContentAdded()
        refreshModelState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SHARED_CONTENT_ADDED, false)) notifySharedContentAdded()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            IMPORT_MODEL_REQUEST -> data?.data?.let(::importModel)
            DRIVE_DOCUMENT_REQUEST -> data?.data?.let(::importDriveDocument)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                val action = pendingPermissionAction
                pendingPermissionAction = null
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && action != null) executeBasicAction(action)
                else finishBasicAction(DeviceActionResult(false, "Camera permission was not granted, so the flashlight was not changed."), "device.flashlight")
            }
            CALENDAR_PERMISSION_REQUEST -> {
                val action = pendingCalendarAction
                pendingCalendarAction = null
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED } && action != null) executeCalendarAction(action)
                else finishBasicAction(DeviceActionResult(false, "Calendar permission was not granted, so no event was created."), "calendar.create_event")
            }
        }
    }

    override fun onDestroy() {
        legacyDownloadId = null
        mainHandler.removeCallbacksAndMessages(null)
        disposeChatEngineAsync()
        fileExecutor.shutdownNow()
        runtimeCleanupExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun recommendedModel(): LocalModelSpec {
        val memory = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        return LocalModelCatalog.recommended(memory.totalMem)
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.localmind_canvas)) }
        root.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(64)))
        scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(20)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        modelPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(modelPanel)
        content.addView(messages, blockParams(4))
        content.addView(buildSuggestions().also { starterPrompts = it }, blockParams(12))
        content.addView(buildTracePanel(), blockParams(16))
        root.addView(buildComposer())
        return root
    }

    private fun buildHeader(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), 0, dp(10), 0)
        background = solid(R.color.localmind_surface, 0f, R.color.localmind_divider)
        addView(TextView(this@MainActivity).apply {
            text = "L"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 16f
            typeface = Typeface.DEFAULT_BOLD; background = solid(R.color.localmind_ink, 8f)
        }, LinearLayout.LayoutParams(dp(34), dp(34)))
        addView(TextView(this@MainActivity).apply {
            text = "LocalMind"; setTextColor(getColor(R.color.localmind_ink)); textSize = 19f; typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
        addView(TextView(this@MainActivity).apply {
            modelBadge = this; gravity = Gravity.CENTER; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
            minWidth = dp(88); setPadding(dp(8), dp(6), dp(8), dp(6))
        })
        addView(ImageButton(this@MainActivity).apply {
            overflowButton = this; setImageResource(R.drawable.ic_more_vert); contentDescription = "More options"; background = null
            setOnClickListener { showOverflowMenu() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
    }

    private fun buildComposer(): View = LinearLayout(this).apply {
        gravity = Gravity.BOTTOM; setPadding(dp(12), dp(10), dp(12), dp(12)); background = solid(R.color.localmind_surface, 0f, R.color.localmind_divider)
        requestInput = EditText(this@MainActivity).apply {
            hint = "Set up a local model to start"; setTextColor(getColor(R.color.localmind_ink)); setHintTextColor(getColor(R.color.localmind_muted))
            textSize = 16f; minLines = 1; maxLines = 4; imeOptions = EditorInfo.IME_ACTION_SEND; setSingleLine(false); isEnabled = false
            setPadding(dp(14), dp(11), dp(14), dp(11)); background = solid(R.color.localmind_input, 8f, R.color.localmind_divider)
            setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_SEND) { submitRequest(); true } else false }
        }
        addView(requestInput, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ImageButton(this@MainActivity).apply {
            sendButton = this; setImageResource(R.drawable.ic_arrow_up); setColorFilter(Color.WHITE); contentDescription = "Send message"
            background = solid(R.color.localmind_ink, 8f); isEnabled = false; setOnClickListener { submitRequest() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) })
    }

    private fun buildSuggestions(): View = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@MainActivity).apply {
            addView(actionButton("Help me write", false) { requestInput.setText("Help me write a clear message"); requestInput.requestFocus() })
            addView(actionButton("Open WhatsApp", false) { requestInput.setText("Open WhatsApp"); submitRequest() }, LinearLayout.LayoutParams(-2, dp(44)).apply { leftMargin = dp(8) })
            addView(actionButton("Flashlight on", false) { requestInput.setText("Turn on the flashlight"); submitRequest() }, LinearLayout.LayoutParams(-2, dp(44)).apply { leftMargin = dp(8) })
            addView(actionButton("Pause music", false) { requestInput.setText("Pause the music"); submitRequest() }, LinearLayout.LayoutParams(-2, dp(44)).apply { leftMargin = dp(8) })
        })
    }

    private fun buildTracePanel(): View = LinearLayout(this).apply {
        tracePanel = this; orientation = LinearLayout.VERTICAL; visibility = View.GONE
        background = solid(R.color.localmind_rail, 8f); setPadding(dp(12), dp(12), dp(12), dp(12))
        addView(overline("ACTIVITY", R.color.localmind_trace_text))
        traceList = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        addView(traceList, blockParams(6))
    }

    private fun showOverflowMenu() {
        PopupMenu(this, overflowButton).apply {
            menu.add("New chat").isEnabled = modelReady && !generating
            menu.add(if (tracePanel.visibility == View.VISIBLE) "Hide activity" else "Show activity")
            val sharedCount = SharedContentStore(this@MainActivity).list().size
            menu.add("Shared inbox${if (sharedCount > 0) " ($sharedCount)" else ""}")
            menu.add("Use Qwen3 0.6B Lite")
            menu.add("Use Gemma 3 1B")
            menu.add("Use Gemma 4 Full")
            setOnMenuItemClickListener {
                when (it.title.toString()) {
                    "New chat" -> startNewChat()
                    "Show activity", "Hide activity" -> {
                        tracePanel.visibility = if (tracePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        if (tracePanel.visibility == View.VISIBLE) scrollToBottom()
                    }
                    "Use Qwen3 0.6B Lite" -> selectModel(LocalModelCatalog.lite)
                    "Use Gemma 3 1B" -> selectModel(LocalModelCatalog.standard)
                    "Use Gemma 4 Full" -> selectModel(LocalModelCatalog.full)
                    else -> if (it.title.toString().startsWith("Shared inbox")) showSharedInbox()
                }
                true
            }
            show()
        }
    }

    private fun selectModel(model: LocalModelSpec) {
        if (selectedModel == model) return
        cancelModelDownloadService()
        legacyDownloadId?.let { downloads().remove(it) }
        modelStore.deleteDownloadFiles(selectedModel)
        modelStore.clearDownloadState()
        modelStore.clearTrackedDownload()
        legacyDownloadId = null
        modelLoadAttempt++
        disposeChatEngineAsync(); modelReady = false
        selectedModel = model; modelStore.select(model); recordActivity("Model selected", model.displayName)
        refreshModelState()
    }

    private fun refreshModelState() {
        if (modelStore.isAvailable(selectedModel)) {
            initializeModel()
            return
        }
        legacyDownloadId = modelStore.trackedDownload(selectedModel)
        legacyDownloadId?.let {
            downloads().remove(it)
            modelStore.clearTrackedDownload()
            modelStore.downloadFile(selectedModel).delete()
            legacyDownloadId = null
        }
        val status = modelStore.downloadStatus(selectedModel)
        if (status != null) {
            resetDownloadMetrics()
            if (status.state == LocalModelStore.DOWNLOAD_ACTIVE) startModelDownloadService()
            pollDownload()
        } else {
            showModelSetup()
        }
    }

    private fun showModelSetup(status: String? = null, offerLiteFallback: Boolean = false) {
        setModelReady(false); setBadge("SET UP MODEL", false)
        modelPanel.visibility = View.VISIBLE; modelPanel.removeAllViews(); stylePanel(modelPanel)
        modelPanel.addView(overline(if (selectedModel == recommendedModel()) "RECOMMENDED FOR THIS PHONE" else "SELECTED MODEL", R.color.localmind_blue))
        modelPanel.addView(TextView(this).apply {
            text = selectedModel.displayName; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(getColor(R.color.localmind_ink))
        }, blockParams(6))
        val setupNote = if (selectedModel.downloadUrl == null) {
            " Accept the model license on the download page, download the .litertlm file, then import it here."
        } else ""
        modelPanel.addView(body("${selectedModel.downloadSizeLabel} model. Runs locally; prompts and responses stay on this device.$setupNote"), blockParams(5))
        status?.let { modelPanel.addView(body(it), blockParams(8)) }
        modelPanel.addView(LinearLayout(this).apply {
            val modelAvailable = modelStore.isAvailable(selectedModel)
            addView(
                actionButton(if (modelAvailable) "Try again" else if (selectedModel.downloadUrl != null) "Download model" else "Get model", true) {
                    if (modelAvailable) initializeModel() else if (selectedModel.downloadUrl != null) startModelDownload() else openModelDownloadPage()
                },
                LinearLayout.LayoutParams(0, dp(46), 1f)
            )
            addView(actionButton("Import file", false, ::chooseModelFile), LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
        }, blockParams(12))
        if (offerLiteFallback) {
            modelPanel.addView(actionButton("Use lighter model", false) { selectModel(LocalModelCatalog.lite) }, blockParams(8))
        }
    }

    private fun showDownloadProgress(doneBytes: Long, totalBytes: Long, state: String? = null) {
        val progressValue = if (totalBytes > 0L) ((doneBytes.coerceAtLeast(0L) * 100L) / totalBytes).toInt() else null
        setBadge("DOWNLOADING", false); modelPanel.removeAllViews(); stylePanel(modelPanel)
        modelPanel.addView(overline("DOWNLOADING LOCALLY", R.color.localmind_blue)); modelPanel.addView(body(selectedModel.displayName), blockParams(6))
        modelPanel.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = progressValue == null; max = 100; progressValue?.let { progress = it }
        }, blockParams(12))
        modelPanel.addView(body(downloadProgressText(doneBytes, totalBytes, progressValue, state)), blockParams(6))
        modelPanel.addView(actionButton("Cancel download", false, ::cancelDownload), blockParams(10))
    }

    private fun startModelDownload() {
        if (selectedModel.downloadUrl == null) return
        val finalFile = modelStore.file(selectedModel)
        if (finalFile.exists() && !modelStore.isAvailable(selectedModel)) modelStore.deleteModel(selectedModel)
        modelStore.deleteDownloadFiles(selectedModel)
        modelStore.beginDownload(selectedModel)
        resetDownloadMetrics(0L)
        startModelDownloadService()
        recordActivity("Model download started", "${selectedModel.displayName}, ${selectedModel.downloadSizeLabel}")
        showDownloadProgress(0L, selectedModel.downloadBytes, "Opening secure model streams...")
        pollDownload()
    }

    private fun startModelDownloadService() {
        val intent = Intent(this, ModelDownloadService::class.java).putExtra(ModelDownloadService.EXTRA_MODEL_ID, selectedModel.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun openModelDownloadPage() {
        val pageUrl = selectedModel.downloadPageUrl ?: return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)))
        recordActivity("Model download page opened", selectedModel.displayName)
    }

    private fun pollDownload() {
        val status = modelStore.downloadStatus(selectedModel) ?: return
        when (status.state) {
            LocalModelStore.DOWNLOAD_ACTIVE -> showDownloadProgress(status.downloadedBytes, selectedModel.downloadBytes)
            LocalModelStore.DOWNLOAD_VERIFYING -> showVerifyingModel()
            LocalModelStore.DOWNLOAD_COMPLETE -> {
                modelStore.clearDownloadState()
                if (modelStore.isAvailable(selectedModel)) {
                    recordActivity("Model download completed", selectedModel.displayName)
                    initializeModel()
                } else showModelSetup("The downloaded file could not be activated. Please retry.")
                return
            }
            LocalModelStore.DOWNLOAD_FAILED -> {
                modelStore.clearDownloadState()
                recordActivity("Model download paused", selectedModel.displayName)
                showModelSetup(status.error ?: "The download was interrupted. Tap Download model to resume it.")
                return
            }
        }
        mainHandler.postDelayed(::pollDownload, 1000)
    }

    private fun showVerifyingModel() {
        setBadge("VERIFYING", false)
        modelPanel.removeAllViews(); stylePanel(modelPanel)
        modelPanel.addView(overline("VERIFYING MODEL", R.color.localmind_blue))
        modelPanel.addView(body("Checking ${selectedModel.displayName} before it is opened. This can take a few seconds."), blockParams(6))
        modelPanel.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(36), dp(36)).apply { topMargin = dp(10) })
    }

    private fun cancelDownload() {
        cancelModelDownloadService()
        legacyDownloadId?.let { downloads().remove(it) }; legacyDownloadId = null
        modelStore.clearTrackedDownload()
        modelStore.clearDownloadState()
        modelStore.deleteDownloadFiles(selectedModel)
        recordActivity("Model download cancelled", selectedModel.displayName); showModelSetup("Download cancelled. No model was activated.")
    }

    private fun cancelModelDownloadService() {
        startService(Intent(this, ModelDownloadService::class.java).setAction(ModelDownloadService.ACTION_CANCEL))
    }

    private fun resetDownloadMetrics(startBytes: Long? = null) {
        downloadSampleStartedAt = if (startBytes == null) 0L else SystemClock.elapsedRealtime()
        downloadSampleStartBytes = startBytes ?: 0L
        downloadBytesPerSecond = 0L
    }

    private fun downloadProgressText(doneBytes: Long, totalBytes: Long, percent: Int?, state: String?): String {
        val safeDone = doneBytes.coerceAtLeast(0L)
        val safeTotal = totalBytes.takeIf { it > 0L } ?: selectedModel.downloadBytes
        if (downloadSampleStartedAt == 0L) {
            downloadSampleStartedAt = SystemClock.elapsedRealtime()
            downloadSampleStartBytes = safeDone
        } else if (safeDone < downloadSampleStartBytes) {
            resetDownloadMetrics(safeDone)
        }
        val now = SystemClock.elapsedRealtime()
        val elapsedMs = (now - downloadSampleStartedAt).coerceAtLeast(1L)
        if (elapsedMs >= DOWNLOAD_SPEED_SAMPLE_MS) {
            val measuredBytes = (safeDone - downloadSampleStartBytes).coerceAtLeast(0L)
            val instantSpeed = measuredBytes * 1000L / elapsedMs
            downloadBytesPerSecond = if (downloadBytesPerSecond == 0L) instantSpeed else (downloadBytesPerSecond * 2L + instantSpeed) / 3L
            downloadSampleStartedAt = now
            downloadSampleStartBytes = safeDone
        }
        val bytesPerSecond = downloadBytesPerSecond
        val transfer = "${formatBytes(safeDone)} of ${formatBytes(safeTotal)}${percent?.let { " ($it%)" }.orEmpty()}"
        val speed = if (bytesPerSecond > 0L) " at ${formatBytes(bytesPerSecond)}/s" else ""
        val remaining = if (bytesPerSecond > 0L && safeTotal > safeDone) " - about ${formatDuration((safeTotal - safeDone) / bytesPerSecond)} left" else ""
        return listOfNotNull(transfer + speed + remaining, state).joinToString("\n")
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes.toDouble() / (1024L * 1024L))
        bytes >= 1024L -> "%.1f KB".format(bytes.toDouble() / 1024L)
        else -> "$bytes B"
    }

    private fun formatDuration(seconds: Long): String = when {
        seconds >= 3600L -> "${seconds / 3600L}h ${(seconds % 3600L) / 60L}m"
        seconds >= 60L -> "${seconds / 60L}m ${seconds % 60L}s"
        else -> "${seconds}s"
    }

    private fun chooseModelFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream" }, IMPORT_MODEL_REQUEST)
    }

    private fun importModel(uri: Uri) {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        if (name?.endsWith(".litertlm", true) != true) { showModelSetup("Choose a .litertlm model file."); return }
        setBadge("IMPORTING", false); modelPanel.removeAllViews(); stylePanel(modelPanel); modelPanel.addView(body("Importing $name..."))
        fileExecutor.execute {
            val target = modelStore.file(selectedModel); val temporary = File(target.parentFile, "${target.name}.importing")
            val result = runCatching {
                contentResolver.openInputStream(uri).use { input -> requireNotNull(input); temporary.outputStream().use { input.copyTo(it) } }
                require(selectedModel.hasCompleteFileSize(temporary.length())) {
                    "This file does not match ${selectedModel.displayName}. Expected ${selectedModel.downloadSizeLabel}."
                }
                check(modelStore.activateImport(selectedModel, temporary)) { "The imported model failed verification and was not activated." }
            }
            runOnUiThread { result.onSuccess { recordActivity("Model imported", selectedModel.displayName); initializeModel() }.onFailure { temporary.delete(); showModelSetup(it.message ?: "Model import failed.") } }
        }
    }

    private fun initializeModel() {
        val loadAttempt = ++modelLoadAttempt
        val loadingModel = selectedModel
        setModelReady(false); setBadge("LOADING MODEL", false); modelPanel.visibility = View.VISIBLE; modelPanel.removeAllViews(); stylePanel(modelPanel)
        modelPanel.addView(overline("INITIALIZING ON DEVICE", R.color.localmind_blue))
        modelPanel.addView(body("Loading ${selectedModel.displayName}. The first start can take a moment."), blockParams(6))
        modelPanel.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(36), dp(36)).apply { topMargin = dp(10) })
        disposeChatEngineAsync()
        chatEngine = LiteRtChatEngine(selectedModel, modelStore.file(selectedModel), File(cacheDir, "litertlm/${selectedModel.id}"))
        mainHandler.postDelayed({
            if (loadAttempt == modelLoadAttempt && !modelReady) {
                recoverFromModelLoadFailure(loadAttempt, loadingModel, timedOut = true)
            }
        }, modelInitializationTimeout(selectedModel))
        chatEngine?.initialize(
            onReady = { runOnUiThread {
                if (loadAttempt != modelLoadAttempt) return@runOnUiThread
                modelPanel.visibility = View.GONE; setModelReady(true)
                setBadge(modelBadgeLabel(selectedModel), true)
                recordActivity("Model ready", selectedModel.displayName)
                if (messages.childCount == 0) addAssistantMessage("What would you like to do?")
                pendingModelNotice?.let(::addAssistantMessage)
                pendingModelNotice = null
            } },
            onError = { error -> runOnUiThread {
                if (loadAttempt != modelLoadAttempt) return@runOnUiThread
                recordActivity("Model initialization failed", error.javaClass.simpleName)
                recoverFromModelLoadFailure(loadAttempt, loadingModel, timedOut = false)
            } }
        )
    }

    private fun recoverFromModelLoadFailure(loadAttempt: Int, failedModel: LocalModelSpec, timedOut: Boolean) {
        if (loadAttempt != modelLoadAttempt) return
        modelLoadAttempt++
        disposeChatEngineAsync()
        val reason = if (timedOut) "took too long to start" else "could not start"
        recordActivity("Model unavailable", "${failedModel.displayName} $reason")
        if (failedModel != LocalModelCatalog.lite && modelStore.isAvailable(LocalModelCatalog.lite)) {
            selectedModel = LocalModelCatalog.lite
            modelStore.select(selectedModel)
            restartWithLite("${failedModel.displayName} is not compatible with this device, so LocalMind switched to Qwen3 0.6B Lite.")
        } else {
            showModelSetup(
                "This model $reason. Its backend may not be compatible with this phone.",
                offerLiteFallback = failedModel != LocalModelCatalog.lite
            )
        }
    }

    private fun disposeChatEngineAsync() {
        val engineToClose = chatEngine ?: return
        chatEngine = null
        engineToClose.cancel()
        runtimeCleanupExecutor.execute { runCatching { engineToClose.close() } }
    }

    private fun restartWithLite(notice: String) {
        setBadge("RESTARTING", false)
        modelPanel.removeAllViews()
        stylePanel(modelPanel)
        modelPanel.addView(overline("USING COMPATIBLE MODEL", R.color.localmind_blue))
        modelPanel.addView(body("The selected model is unavailable. Restarting with Qwen3 0.6B Lite..."), blockParams(6))
        startActivity(Intent(this, ModelRecoveryActivity::class.java).putExtra(EXTRA_MODEL_NOTICE, notice))
        mainHandler.postDelayed({ Process.killProcess(Process.myPid()) }, 250L)
    }

    private fun submitRequest() {
        val request = requestInput.text.toString().trim(); if (request.isEmpty() || generating) return
        requestInput.text.clear(); starterPrompts.visibility = View.GONE
        addUserMessage(request)
        BasicDeviceActionParser.parse(request, lastBasicAction)?.let {
            executeBasicAction(it)
            return
        }
        lastBasicAction = null
        if (BasicDeviceActionParser.isAmbiguousStateCommand(request)) {
            addAssistantMessage("What would you like me to turn on or off?")
            return
        }
        if (!modelReady) {
            addAssistantMessage("Set up a local model for conversation. Basic phone actions such as opening an app, controlling the flashlight, media, and volume are still available.")
            return
        }
        generating = true; setComposerEnabled(false)
        val pending = addAssistantMessage("Thinking on device..."); recordActivity("Generation started", selectedModel.displayName)
        chatEngine?.send(request,
            onResponse = { response -> runOnUiThread { pending.text = ResponseFormatter.format(response); generating = false; setComposerEnabled(true); recordActivity("Generation completed", selectedModel.displayName); scrollToBottom() } },
            onError = { error -> runOnUiThread {
                pending.text = "I could not complete that response locally. Please try again."
                generating = false; setComposerEnabled(true); recordActivity("Generation failed", error.javaClass.simpleName)
            } }
        )
    }

    private fun executeBasicAction(action: BasicDeviceAction) {
        when (action) {
            is BasicDeviceAction.OpenApp -> finishBasicAction(BasicDeviceActions.openApp(this, action.app), "device.open_app", action)
            is BasicDeviceAction.Media -> finishBasicAction(BasicDeviceActions.controlMedia(this, action.command), "device.media", action)
            is BasicDeviceAction.Volume -> finishBasicAction(BasicDeviceActions.changeVolume(this, action.command), "device.volume", action)
            is BasicDeviceAction.SendWhatsApp -> confirmWhatsAppHandoff(action)
            is BasicDeviceAction.ComposeEmail -> confirmEmailHandoff(action)
            BasicDeviceAction.PickDriveDocument -> openDriveDocumentPicker()
            is BasicDeviceAction.SearchLocalInbox -> searchLocalInbox(action.query)
            is BasicDeviceAction.CreateCalendarEvent -> confirmCalendarAction(action)
            is BasicDeviceAction.SetTorch -> {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    pendingPermissionAction = action
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
                    return
                }
                generating = true
                setComposerEnabled(false)
                val pending = addAssistantMessage("Changing the flashlight...")
                fileExecutor.execute {
                    val result = BasicDeviceActions.setTorch(applicationContext, action.enabled)
                    runOnUiThread {
                        pending.text = ResponseFormatter.format(result.message)
                        generating = false
                        setComposerEnabled(true)
                        if (result.success) lastBasicAction = action
                        recordActivity(if (result.success) "Action completed" else "Action failed", "device.flashlight")
                        scrollToBottom()
                    }
                }
            }
        }
    }

    private fun confirmWhatsAppHandoff(action: BasicDeviceAction.SendWhatsApp) {
        val recipient = action.recipientHint?.let { " for $it" }.orEmpty()
        AlertDialog.Builder(this)
            .setTitle("Open WhatsApp?")
            .setMessage("Prepare this message$recipient:\n\n${action.message}\n\nYou will choose the chat and tap Send in WhatsApp.")
            .setNegativeButton("Cancel") { _, _ -> addAssistantMessage("Cancelled. Nothing was sent.") }
            .setPositiveButton("Continue") { _, _ ->
                val result = runCatching {
                    startActivity(WhatsAppHandoff.createSendIntent(this, action.message))
                    DeviceActionResult(true, "Opened a WhatsApp message draft. Review the recipient and tap Send in WhatsApp.")
                }.getOrElse { DeviceActionResult(false, "WhatsApp could not be opened on this phone.") }
                finishBasicAction(result, "whatsapp.send_handoff", action)
            }
            .show()
    }

    private fun confirmCalendarAction(action: BasicDeviceAction.CreateCalendarEvent) {
        AlertDialog.Builder(this)
            .setTitle("Create calendar event?")
            .setMessage("${action.title}\n${action.date}\n\nLocalMind will add this all-day event to a writable calendar and verify it.")
            .setNegativeButton("Cancel") { _, _ -> addAssistantMessage("Cancelled. No calendar event was created.") }
            .setPositiveButton("Create") { _, _ ->
                val connector = AndroidCalendarConnector(applicationContext)
                if (!connector.hasPermissions()) {
                    pendingCalendarAction = action
                    requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), CALENDAR_PERMISSION_REQUEST)
                } else executeCalendarAction(action)
            }
            .show()
    }

    private fun confirmEmailHandoff(action: BasicDeviceAction.ComposeEmail) {
        AlertDialog.Builder(this)
            .setTitle("Open email draft?")
            .setMessage("To: ${action.address}\n\n${action.message}\n\nYou will review and send it in your email app.")
            .setNegativeButton("Cancel") { _, _ -> addAssistantMessage("Cancelled. No email was sent.") }
            .setPositiveButton("Continue") { _, _ ->
                val result = runCatching {
                    startActivity(EmailHandoff.createDraftIntent(this, action.address, action.message))
                    DeviceActionResult(true, "Opened an email draft to ${action.address}. Review it and tap Send in your email app.")
                }.getOrElse { DeviceActionResult(false, "No compatible email app could open the draft.") }
                finishBasicAction(result, "email.compose_handoff", action)
            }
            .show()
    }

    private fun openDriveDocumentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        runCatching { startActivityForResult(intent, DRIVE_DOCUMENT_REQUEST) }
            .onFailure { finishBasicAction(DeviceActionResult(false, "Android could not open the document picker."), "drive.pick_document") }
    }

    private fun importDriveDocument(uri: Uri) {
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val text = if (mimeType.startsWith("text/")) runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(MAX_IMPORTED_TEXT_CHARS) }
        }.getOrNull() else null
        SharedContentStore(this).add(mimeType, text, uri.toString())
        addAssistantMessage(if (text != null) "Added the selected document text to the local inbox." else "Added the selected document to the local inbox. Its contents have not been extracted yet.")
        recordActivity("Drive document selected", mimeType)
    }

    private fun searchLocalInbox(query: String) {
        val results = SharedContentStore(this).search(query).take(5)
        val response = if (results.isEmpty()) {
            "I couldn't find '$query' in the local shared inbox."
        } else {
            "Found ${results.size} local item${if (results.size == 1) "" else "s"}:\n\n" + results.joinToString("\n\n") { it.preview(180) }
        }
        addAssistantMessage(response)
        recordActivity("Local inbox searched", "${results.size} result(s)")
    }

    private fun executeCalendarAction(action: BasicDeviceAction.CreateCalendarEvent) {
        generating = true
        setComposerEnabled(false)
        val pending = addAssistantMessage("Creating and verifying the calendar event...")
        fileExecutor.execute {
            val connector = AndroidCalendarConnector(applicationContext)
            val created = connector.create(listOf(CalendarEvent("pending", action.title, action.date.toString(), "Created by LocalMind")))
            @Suppress("UNCHECKED_CAST")
            val events = created.data["events"] as? List<CalendarEvent>
            val verified = if (created.success && events != null) connector.verify(events.map { it.id }) else null
            val result = when {
                !created.success -> DeviceActionResult(false, created.errorMessage ?: "The calendar event could not be created.")
                verified?.data?.get("verified") == true -> DeviceActionResult(true, "Created and verified '${action.title}' on ${action.date}.")
                else -> DeviceActionResult(false, "The event may have been created, but LocalMind could not verify it. Check Calendar before retrying.")
            }
            runOnUiThread {
                pending.text = ResponseFormatter.format(result.message)
                generating = false
                setComposerEnabled(true)
                if (result.success) lastBasicAction = action
                recordActivity(if (result.success) "Action completed" else "Action failed", "calendar.create_event")
                scrollToBottom()
            }
        }
    }

    private fun finishBasicAction(result: DeviceActionResult, actionId: String, action: BasicDeviceAction? = null) {
        addAssistantMessage(result.message)
        if (result.success && action != null) lastBasicAction = action
        recordActivity(if (result.success) "Action completed" else "Action failed", actionId)
    }

    private fun startNewChat() {
        if (generating) return
        chatEngine?.reset()
        lastBasicAction = null
        messages.removeAllViews()
        starterPrompts.visibility = View.VISIBLE
        requestInput.text.clear()
        addAssistantMessage("What would you like to do?")
        recordActivity("New chat started", selectedModel.displayName)
    }

    private fun modelBadgeLabel(model: LocalModelSpec): String = when (model) {
        LocalModelCatalog.lite -> "0.6B \u00B7 LOCAL"
        LocalModelCatalog.standard -> "GEMMA 3 \u00B7 LOCAL"
        else -> "GEMMA 4 \u00B7 LOCAL"
    }

    private fun modelInitializationTimeout(model: LocalModelSpec): Long =
        if (model.useGpu) GPU_INITIALIZATION_TIMEOUT_MS else CPU_INITIALIZATION_TIMEOUT_MS

    private fun setModelReady(ready: Boolean) { modelReady = ready; setComposerEnabled(!generating) }
    private fun setComposerEnabled(enabled: Boolean) {
        if (!::requestInput.isInitialized) return
        requestInput.isEnabled = enabled; sendButton.isEnabled = enabled; sendButton.alpha = if (enabled) 1f else 0.45f
        requestInput.hint = if (enabled) "Message LocalMind" else if (generating) "Working on device..." else "Message LocalMind"
    }
    private fun setBadge(label: String, ready: Boolean) {
        if (!::modelBadge.isInitialized) return
        modelBadge.text = label; modelBadge.setTextColor(getColor(if (ready) R.color.localmind_success_dark else R.color.localmind_warning_dark))
        modelBadge.background = solid(if (ready) R.color.localmind_success_soft else R.color.localmind_warning_soft, 8f)
    }

    private fun notifySharedContentAdded() {
        if (::messages.isInitialized) addAssistantMessage("Added the shared item locally. Open Shared inbox from the menu to view it.")
    }

    private fun showSharedInbox() {
        val items = SharedContentStore(this).list()
        val content = if (items.isEmpty()) {
            "No shared items yet. Use Share in WhatsApp or another app, then choose LocalMind."
        } else {
            items.take(10).joinToString("\n\n") { it.preview() }
        }
        AlertDialog.Builder(this)
            .setTitle("Shared inbox${if (items.isEmpty()) "" else " (${items.size})"}")
            .setMessage(content)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun recordActivity(title: String, detail: String) {
        activityItems += title to detail; if (!::traceList.isInitialized) return; traceList.removeAllViews()
        activityItems.takeLast(12).forEach { (event, value) -> traceList.addView(TextView(this).apply {
            text = "$event\n$value"; setTextColor(getColor(R.color.localmind_trace_text)); textSize = 12f
            setPadding(dp(10), dp(8), dp(10), dp(8)); background = solid(R.color.localmind_rail_light, 6f)
        }, blockParams(6)) }
    }

    private fun addUserMessage(text: String) = addBubble(text, true)
    private fun addAssistantMessage(text: String) = addBubble(text, false)
    private fun addBubble(text: String, fromUser: Boolean): TextView {
        val row = LinearLayout(this).apply {
            gravity = if (fromUser) Gravity.END else Gravity.TOP
            orientation = LinearLayout.HORIZONTAL
            setPadding(if (fromUser) dp(48) else 0, 0, if (fromUser) 0 else dp(28), 0)
        }
        if (!fromUser) row.addView(TextView(this).apply {
            this.text = "L"; gravity = Gravity.CENTER; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); background = solid(R.color.localmind_ink, 7f)
        }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(10); topMargin = dp(2) })
        val bubble = TextView(this).apply {
            this.text = if (fromUser) text else ResponseFormatter.format(text)
            setTextColor(if (fromUser) Color.WHITE else getColor(R.color.localmind_ink)); textSize = 15f
            setLineSpacing(dp(2).toFloat(), 1.1f); setPadding(if (fromUser) dp(13) else 0, dp(9), if (fromUser) dp(13) else 0, dp(9))
            setTextIsSelectable(!fromUser)
            if (fromUser) background = solid(R.color.localmind_user_bubble, 8f)
        }
        row.addView(bubble, LinearLayout.LayoutParams(-2, -2))
        messages.addView(row, blockParams(10)); scrollToBottom(); return bubble
    }
    private fun actionButton(text: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 14f; setTextColor(if (primary) Color.WHITE else getColor(R.color.localmind_ink))
        background = solid(if (primary) R.color.localmind_ink else R.color.localmind_input, 7f, if (primary) null else R.color.localmind_divider)
        setPadding(dp(12), 0, dp(12), 0); setOnClickListener { onClick() }
    }
    private fun overline(text: String, color: Int) = TextView(this).apply { this.text = text; setTextColor(getColor(color)); textSize = 11f; typeface = Typeface.DEFAULT_BOLD }
    private fun body(text: String) = TextView(this).apply { this.text = text; setTextColor(getColor(R.color.localmind_ink)); textSize = 15f; setLineSpacing(dp(2).toFloat(), 1.05f) }
    private fun stylePanel(panel: LinearLayout) { panel.setPadding(dp(14), dp(14), dp(14), dp(14)); panel.background = solid(R.color.localmind_surface, 8f, R.color.localmind_divider) }
    private fun solid(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply { setColor(getColor(color)); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(1), getColor(it)) } }
    private fun blockParams(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun downloads() = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
    private fun scrollToBottom() { if (::scroll.isInitialized) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SHARED_CONTENT_ADDED = "shared_content_added"
        const val EXTRA_MODEL_NOTICE = "model_notice"
        private const val IMPORT_MODEL_REQUEST = 72
        private const val CAMERA_PERMISSION_REQUEST = 73
        private const val CALENDAR_PERMISSION_REQUEST = 74
        private const val DRIVE_DOCUMENT_REQUEST = 75
        private const val MAX_IMPORTED_TEXT_CHARS = 250_000
        private const val DOWNLOAD_SPEED_SAMPLE_MS = 3_000L
        private const val GPU_INITIALIZATION_TIMEOUT_MS = 30_000L
        private const val CPU_INITIALIZATION_TIMEOUT_MS = 120_000L
    }
}
