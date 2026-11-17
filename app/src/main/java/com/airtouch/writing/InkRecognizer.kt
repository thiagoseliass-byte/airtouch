package com.airtouch.writing

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Reconhecimento de escrita à mão (ML Kit Digital Ink). O modelo de idioma (~20 MB) é
 * baixado UMA vez (precisa de internet só nesse momento); depois tudo roda no aparelho.
 */
class InkRecognizer(@Suppress("unused") private val context: Context) {

    private val manager = RemoteModelManager.getInstance()
    private var model: DigitalInkRecognitionModel? = null
    private var recognizer: DigitalInkRecognizer? = null

    private fun buildModel(tag: String): DigitalInkRecognitionModel? = try {
        val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)
        if (id == null) null else DigitalInkRecognitionModel.builder(id).build()
    } catch (e: Exception) {
        null
    }

    private fun candidates(): List<DigitalInkRecognitionModel> =
        listOfNotNull(buildModel("pt-BR"), buildModel("en-US"))

    /** Já existe algum modelo baixado? Também deixa o reconhecedor pronto. */
    suspend fun isReady(): Boolean {
        if (recognizer != null) return true
        for (m in candidates()) {
            val has = try { manager.isModelDownloaded(m).await() } catch (e: Exception) { false }
            if (has) {
                use(m)
                return true
            }
        }
        return false
    }

    /** Baixa o modelo (pt-BR; se não existir, en-US). Lança exceção se falhar. */
    suspend fun download() {
        var last: Exception? = null
        for (m in candidates()) {
            try {
                manager.download(m, DownloadConditions.Builder().build()).await()
                use(m)
                return
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Modelo de escrita indisponível")
    }

    private fun use(m: DigitalInkRecognitionModel) {
        recognizer?.close()
        model = m
        recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build())
    }

    /** Reconhece os traços. Coordenadas devem ter proporção correta (x e y na mesma escala). */
    suspend fun recognize(strokes: List<List<Pt>>): String? {
        val r = recognizer ?: return null
        if (strokes.isEmpty()) return null
        val ink = Ink.builder()
        for (s in strokes) {
            val sb = Ink.Stroke.builder()
            for (p in s) sb.addPoint(Ink.Point.create(p.x, p.y, p.t))
            ink.addStroke(sb.build())
        }
        val result = r.recognize(ink.build()).await()
        return result.candidates.firstOrNull()?.text
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
