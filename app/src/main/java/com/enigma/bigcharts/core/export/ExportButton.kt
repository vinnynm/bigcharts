package com.enigma.bigcharts.core.export

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun ExportPdfButton(
    exporter: PdfExporterState,
    content: @Composable () -> Unit,
    fileName: String = "chart_export.pdf",
    modifier: Modifier = Modifier,
    buttonText: String = "Export as PDF",
    onSuccess: ((File) -> Unit)? = null,
    onError: ((Throwable) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isExporting by exporter.isExporting.collectAsState()

    Button(
        onClick = {
            scope.launch {
                try {
                    val file = File(context.cacheDir, fileName)
                    exporter.captureToFile(
                        content = content,
                        file = file
                    ) { progress ->
                        // optional progress handling
                    }
                    onSuccess?.invoke(file)
                } catch (e: Exception) {
                    onError?.invoke(e)
                }
            }
        },
        enabled = !isExporting,
        modifier = modifier
    ) {
        if (isExporting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(buttonText)
    }
}