package com.airtouch.docs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Abre um PDF escolhido pelo usuário (sem permissão de armazenamento) e renderiza
 * páginas em Bitmap com o PdfRenderer do Android. Tudo local.
 */
class PdfController(private val context: Context) {

    private val mutex = Mutex()
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    /** Abre o arquivo e devolve o número de páginas. */
    suspend fun open(uri: Uri): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeLocked()
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("não foi possível ler o arquivo")
            try {
                val r = PdfRenderer(descriptor)
                fd = descriptor
                renderer = r
                r.pageCount
            } catch (e: Exception) {
                descriptor.close()
                throw e
            }
        }
    }

    /** Renderiza a página [page] (0-based) com largura [widthPx]. */
    suspend fun render(page: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val r = renderer ?: return@withLock null
            if (page < 0 || page >= r.pageCount) return@withLock null
            val p = r.openPage(page)
            try {
                val w = widthPx.coerceIn(200, 4096)
                val h = (w.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            } finally {
                p.close()
            }
        }
    }

    suspend fun close() {
        withContext(Dispatchers.IO) { mutex.withLock { closeLocked() } }
    }

    private fun closeLocked() {
        try {
            renderer?.close()
        } catch (_: Exception) {
        }
        try {
            fd?.close()
        } catch (_: Exception) {
        }
        renderer = null
        fd = null
    }
}
