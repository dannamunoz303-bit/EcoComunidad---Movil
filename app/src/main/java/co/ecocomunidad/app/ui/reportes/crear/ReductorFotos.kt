package co.ecocomunidad.app.ui.reportes.crear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Reduce cada foto antes de subirla: lado mayor de [LADO_MAYOR] px y JPEG de calidad [CALIDAD].
 * Así sube rápido y queda muy por debajo de los 5 MB que acepta el backend (RN-15).
 * Respeta la orientación que guarda la cámara en el EXIF.
 */
class ReductorFotos(private val contexto: Context) : PreparadorFotos {

    private val carpeta: File
        get() = File(contexto.cacheDir, "fotos_reporte").apply { mkdirs() }

    override suspend fun preparar(origen: String): File = withContext(Dispatchers.IO) {
        val uri = origen.toUri()
        try {
            reducir(uri)
        } finally {
            // La foto original de la cámara ya no hace falta
            archivoDeCamara(uri)?.delete()
        }
    }

    override fun descartar(archivo: File) {
        if (archivo.parentFile == carpeta) archivo.delete()
    }

    private fun reducir(uri: Uri): File {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        abrir(uri).use { BitmapFactory.decodeStream(it, null, limites) }
        val ladoOriginal = max(limites.outWidth, limites.outHeight)
        if (ladoOriginal <= 0) throw IOException("No es una imagen")

        // Se decodifica ya achicada (en potencias de 2) para no llenar la memoria con fotos grandes
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo(ladoOriginal) }
        val decodificada = abrir(uri).use { BitmapFactory.decodeStream(it, null, opciones) }
            ?: throw IOException("No se pudo leer la imagen")
        val orientacion = abrir(uri).use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }

        val final = conFondoBlanco(escalarYEnderezar(decodificada, orientacion))
        val archivo = File(carpeta, "${UUID.randomUUID()}.jpg")
        archivo.outputStream().use { final.compress(Bitmap.CompressFormat.JPEG, CALIDAD, it) }
        if (final !== decodificada) final.recycle()
        decodificada.recycle()
        return archivo
    }

    private fun abrir(uri: Uri): InputStream =
        contexto.contentResolver.openInputStream(uri) ?: throw IOException("No se pudo abrir $uri")

    /** Mayor potencia de 2 que deja el lado mayor todavía en [LADO_MAYOR] o más. */
    private fun muestreo(lado: Int): Int {
        var muestreo = 1
        while (lado / (muestreo * 2) >= LADO_MAYOR) muestreo *= 2
        return muestreo
    }

    private fun escalarYEnderezar(bitmap: Bitmap, orientacion: Int): Bitmap {
        val escala = min(1f, LADO_MAYOR.toFloat() / max(bitmap.width, bitmap.height))
        val matriz = Matrix().apply {
            postScale(escala, escala)
            when (orientacion) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
        if (matriz.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
    }

    /** JPEG no tiene transparencia: las partes transparentes de un PNG quedan blancas y no negras. */
    private fun conFondoBlanco(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val opaco = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(opaco).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        if (bitmap !== opaco) bitmap.recycle()
        return opaco
    }

    /** La foto que tomó la cámara en cache/camara (ver rutas_archivos.xml), si [uri] es una de esas. */
    private fun archivoDeCamara(uri: Uri): File? {
        if (uri.authority != autoridadArchivos(contexto)) return null
        val nombre = uri.lastPathSegment ?: return null
        return File(carpetaCamara(contexto), nombre).takeIf { it.exists() }
    }

    companion object {
        const val LADO_MAYOR = 1600
        const val CALIDAD = 80

        /** La del FileProvider del Manifest. */
        fun autoridadArchivos(contexto: Context) = "${contexto.packageName}.archivos"

        /** Donde la cámara deja la foto que toma; coincide con cache-path "camara" de rutas_archivos.xml. */
        fun carpetaCamara(contexto: Context) = File(contexto.cacheDir, "camara").apply { mkdirs() }
    }
}
