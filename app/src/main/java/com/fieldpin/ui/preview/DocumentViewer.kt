package com.fieldpin.ui.preview

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fieldpin.core.Fmt
import com.fieldpin.domain.model.DocumentKind
import com.fieldpin.domain.model.SiteDocument
import com.fieldpin.ui.components.PrimaryButton
import com.fieldpin.ui.components.SecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

fun DocumentKind.icon(): ImageVector = when (this) {
    DocumentKind.IMAGE -> Icons.Filled.Image
    DocumentKind.PDF -> Icons.Filled.PictureAsPdf
    DocumentKind.TEXT -> Icons.Filled.Description
    DocumentKind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

fun DocumentKind.label(): String = when (this) {
    DocumentKind.IMAGE -> "Image"
    DocumentKind.PDF -> "PDF"
    DocumentKind.TEXT -> "Text"
    DocumentKind.OTHER -> "File"
}

/**
 * Full-screen preview of an attached file: images zoom, PDFs scroll page by page, text files are shown
 * as text. Anything else (Word, Excel, CAD...) shows its details and can be opened in another app.
 */
@Composable
fun DocumentViewer(document: SiteDocument, onClose: () -> Unit, onDelete: (() -> Unit)? = null) {
    val context = LocalContext.current
    val file = remember(document.path) { File(document.path) }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close preview") }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(document.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${document.kind.label()} · ${formatBytes(document.sizeBytes)} · Added ${Fmt.date(document.addedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { openFileExternally(context, file, document.mimeType) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in another app")
                    }
                    IconButton(onClick = { shareFile(context, file, document.mimeType, "Share ${document.name}") }) {
                        Icon(Icons.Filled.Share, "Share")
                    }
                    if (onDelete != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, "Delete file", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (document.kind) {
                        DocumentKind.IMAGE -> Box(Modifier.fillMaxSize().background(Color.Black)) { ZoomableImage(document.path) }
                        DocumentKind.PDF -> PdfBody(document, onOpenExternally = { openFileExternally(context, file, document.mimeType) })
                        DocumentKind.TEXT -> TextBody(document)
                        DocumentKind.OTHER -> NoPreviewBody(
                            document,
                            "This file type can't be previewed here.",
                            onOpen = { openFileExternally(context, file, document.mimeType) },
                            onShare = { shareFile(context, file, document.mimeType, "Share ${document.name}") },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Delete this file?") },
            text = { Text("“${document.name}” will be removed from this site and deleted from the device.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------------------------

private const val TEXT_PREVIEW_LIMIT = 200_000

@Composable
private fun TextBody(document: SiteDocument) {
    val loaded by produceState<String?>(null, document.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(document.path)
                val buffer = ByteArray(TEXT_PREVIEW_LIMIT)
                var read = 0
                file.inputStream().use { stream ->
                    while (read < buffer.size) {
                        val n = stream.read(buffer, read, buffer.size - read)
                        if (n < 0) break
                        read += n
                    }
                }
                val text = String(buffer, 0, read, Charsets.UTF_8)
                if (file.length() > TEXT_PREVIEW_LIMIT) text + "\n\n… preview shortened, open the file to see all of it" else text
            }.getOrNull() ?: ""
        }
    }
    val text = loaded
    when {
        text == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        text.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This file is empty or can't be read.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> SelectionContainer {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// PDF
// ---------------------------------------------------------------------------------------------

/** Owns the renderer. PdfRenderer allows one open page at a time, so renders are serialised. */
private class PdfHandle private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val aspects: List<Float>,
) {
    val pageCount: Int get() = aspects.size
    private val lock = Mutex()
    @Volatile private var closed = false
    private var released = false

    /** Width / height of the page. */
    fun aspect(index: Int): Float = aspects[index]

    suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        if (closed) return@withLock null
        try {
            withContext(Dispatchers.IO) {
                val page = renderer.openPage(index)
                try {
                    val w = widthPx.coerceAtLeast(1)
                    val h = (w * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            }
        } finally {
            if (closed) release()
        }
    }

    /** Safe to call at any time; if a page is mid-render the release happens right after it. */
    fun close() {
        closed = true
        if (lock.tryLock()) {
            try { release() } finally { lock.unlock() }
        }
    }

    private fun release() {
        if (released) return
        released = true
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }

    companion object {
        fun open(file: File): PdfHandle {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(fd)
                val aspects = (0 until renderer.pageCount).map { i ->
                    val page = renderer.openPage(i)
                    try { page.width.toFloat() / page.height.coerceAtLeast(1) } finally { page.close() }
                }
                if (aspects.isEmpty()) throw IllegalStateException("empty pdf")
                return PdfHandle(fd, renderer, aspects)
            } catch (e: Exception) {
                runCatching { fd.close() }
                throw e
            }
        }
    }
}

@Composable
private fun PdfBody(document: SiteDocument, onOpenExternally: () -> Unit) {
    var failed by remember { mutableStateOf(false) }
    val handle by produceState<PdfHandle?>(null, document.path) {
        val opened = withContext(Dispatchers.IO) { runCatching { PdfHandle.open(File(document.path)) }.getOrNull() }
        if (opened == null) failed = true
        value = opened
    }
    DisposableEffect(handle) {
        val h = handle
        onDispose { h?.close() }
    }

    val pdf = handle
    when {
        failed -> NoPreviewBody(
            document,
            "This PDF can't be previewed. It may be password-protected or damaged.",
            onOpen = onOpenExternally,
            onShare = null,
        )
        pdf == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> PdfPages(pdf)
    }
}

@Composable
private fun PdfPages(pdf: PdfHandle) {
    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        val widthPx = with(density) { (maxWidth - 24.dp).toPx() }.toInt().coerceIn(200, 1800)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(pdf.pageCount) { index -> PdfPageView(pdf, index, widthPx) }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            Text(
                "Page ${currentPage.coerceAtMost(pdf.pageCount)} of ${pdf.pageCount}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PdfPageView(pdf: PdfHandle, index: Int, widthPx: Int) {
    val image by produceState<ImageBitmap?>(null, pdf, index, widthPx) {
        value = pdf.render(index, widthPx)?.asImageBitmap()
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(pdf.aspect(index)).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, "Page ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        } else {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Fallback
// ---------------------------------------------------------------------------------------------

@Composable
private fun NoPreviewBody(document: SiteDocument, message: String, onOpen: () -> Unit, onShare: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
            Icon(
                document.kind.icon(), null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(20.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(document.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            "${document.mimeType} · ${formatBytes(document.sizeBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Open with…", onClick = onOpen, icon = Icons.AutoMirrored.Filled.OpenInNew, modifier = Modifier.fillMaxWidth())
        if (onShare != null) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Share", onClick = onShare, icon = Icons.Filled.Share, modifier = Modifier.fillMaxWidth())
        }
    }
}
