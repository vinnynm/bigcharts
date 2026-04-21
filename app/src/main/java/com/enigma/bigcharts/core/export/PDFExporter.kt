package com.enigma.bigcharts.core.export
// core/export/PdfExporter.kt
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

@Stable
class PdfExporterState(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _isExporting = MutableStateFlow(false)
    val isExporting = _isExporting.asStateFlow()

    private var composeView: ComposeView? = null

    suspend fun capture(
        content: @Composable () -> Unit,
        pageWidth: Dp = 595.dp,   // A4 width in points (72 dpi base)
        pageHeight: Dp = 842.dp,  // A4 height
        outputStream: OutputStream,
        onProgress: ((Float) -> Unit)? = null
    ) = withContext(Dispatchers.Main) {
        _isExporting.value = true
        try {
            // Create a temporary ComposeView and measure/layout it at desired size
            val container = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            composeView = ComposeView(context).also { cv ->
                cv.setContent { content() }
                container.addView(cv)
            }

            // Wait for layout
            val density = context.resources.displayMetrics.density
            val widthPx = (pageWidth.value * density).toInt()
            val heightPx = (pageHeight.value * density).toInt()

            composeView?.let { view ->
                // Measure and layout the view at the desired size
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, widthPx, heightPx)

                // Draw to bitmap
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                view.draw(canvas)

                // Generate PDF
                val document = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(widthPx, heightPx, 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                document.finishPage(page)

                document.writeTo(outputStream)
                document.close()
                bitmap.recycle()
            }
            onProgress?.invoke(1f)
        } finally {
            composeView?.let { (it.parent as? ViewGroup)?.removeView(it) }
            composeView = null
            _isExporting.value = false
        }
    }

    suspend fun captureToFile(
        content: @Composable () -> Unit,
        pageWidth: Dp = 595.dp,
        pageHeight: Dp = 842.dp,
        file: File,
        onProgress: ((Float) -> Unit)? = null
    ) {
        FileOutputStream(file).use { stream ->
            capture(content, pageWidth, pageHeight, stream, onProgress)
        }
    }

    fun release() {
        composeView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        composeView = null
    }
}

@Composable
fun rememberPdfExporter(): PdfExporterState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember {
        PdfExporterState(context, scope)
    }
}
