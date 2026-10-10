package com.umbtech.videoconverter

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private var selectedUri: Uri? = null
    private var selectedName: String = ""
    private var transformer: Transformer? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var fileText: TextView
    private lateinit var radio480: RadioButton
    private lateinit var radio720: RadioButton
    private lateinit var chooseButton: Button
    private lateinit var convertButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private val picker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedUri = uri
                selectedName = queryName(uri)
                fileText.text = "Selected: $selectedName"
                statusText.text = "Ready to convert."
                progressBar.progress = 0
                convertButton.isEnabled = true
            }
        }

    private val progressRunnable = object : Runnable {
        override fun run() {
            val t = transformer ?: return
            val holder = ProgressHolder()
            val state = t.getProgress(holder)
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                progressBar.progress = holder.progress
                statusText.text = "Converting... ${holder.progress}%"
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = "UMB Video Converter"
            textSize = 26f
        }
        val subtitle = TextView(this).apply {
            text = "Converts to MP4 (H.264 + AAC). Works offline. No ads."
            textSize = 14f
        }

        chooseButton = Button(this).apply {
            text = "Choose video"
            setOnClickListener { picker.launch("video/*") }
        }

        fileText = TextView(this).apply {
            text = "No video selected."
            textSize = 16f
        }

        val qualityLabel = TextView(this).apply {
            text = "Output size"
            textSize = 16f
        }

        radio480 = RadioButton(this).apply {
            id = View.generateViewId()
            text = "480p (smaller file, best compatibility)"
        }
        radio720 = RadioButton(this).apply {
            id = View.generateViewId()
            text = "720p (better quality)"
        }
        val group = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            addView(radio480)
            addView(radio720)
        }
        radio480.isChecked = true

        convertButton = Button(this).apply {
            text = "Convert"
            isEnabled = false
            setOnClickListener { startConversion() }
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }

        statusText = TextView(this).apply {
            text = "Choose a video to begin."
            textSize = 16f
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(chooseButton)
        root.addView(fileText)
        root.addView(qualityLabel)
        root.addView(group)
        root.addView(convertButton)
        root.addView(progressBar)
        root.addView(statusText)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
            insets
        }

        setContentView(root)
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) {
                val name = c.getString(i)
                if (!name.isNullOrBlank()) return name
            }
        }
        return "video"
    }

    private fun setBusy(busy: Boolean) {
        chooseButton.isEnabled = !busy
        convertButton.isEnabled = !busy && selectedUri != null
        radio480.isEnabled = !busy
        radio720.isEnabled = !busy
        if (busy) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun startConversion() {
        val uri = selectedUri ?: return
        val height = if (radio720.isChecked) 720 else 480

        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val base = selectedName
            .substringBeforeLast('.', selectedName)
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .ifBlank { "video" }
        val outFile = File(dir, "${base}_${height}p_${System.currentTimeMillis()}.mp4")

        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(height))))
            .build()

        val t = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    handler.removeCallbacks(progressRunnable)
                    transformer = null
                    progressBar.progress = 100
                    val mb = outFile.length() / 1048576.0
                    statusText.text = "Done!\n\nFile: ${outFile.name}\n" +
                        "Size: ${String.format("%.1f", mb)} MB\n" +
                        "Saved in:\n${outFile.parent}"
                    setBusy(false)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    handler.removeCallbacks(progressRunnable)
                    transformer = null
                    statusText.text = "Conversion failed.\n\n" +
                        "Code: ${exportException.errorCodeName}\n" +
                        "${exportException.message}"
                    setBusy(false)
                }
            })
            .build()

        transformer = t
        progressBar.progress = 0
        statusText.text = "Starting..."
        setBusy(true)
        t.start(edited, outFile.absolutePath)
        handler.post(progressRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressRunnable)
        transformer?.cancel()
        super.onDestroy()
    }
}