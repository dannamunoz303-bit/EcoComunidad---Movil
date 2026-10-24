package co.ecocomunidad.app.ui.componentes

import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.core.graphics.PathParser
import co.ecocomunidad.app.R

/** Momento en que termina la animación del login, contando la aparición del formulario. */
const val FIN_ANIMACION_LOGIN_MS = 2_100f

// Curvas de CSS que nombra marca.md
private val easeOut = PathInterpolator(0f, 0f, 0.58f, 1f)
val ease: TimeInterpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
private val rebote = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)

/** Avance (0 a 1, o algo más con rebote) de un paso que empieza en [inicioMs] y dura [duracionMs]. */
fun avance(tiempoMs: Float, inicioMs: Float, duracionMs: Float, curva: TimeInterpolator = ease): Float =
    curva.getInterpolation(((tiempoMs - inicioMs) / duracionMs).coerceIn(0f, 1f))

/**
 * Logo armándose según "Animación del logo" de marca.md, con los trazos de docs/marca/logo.svg
 * (viewBox 64). Con [tiempoMs] en [FIN_ANIMACION_LOGIN_MS] se ve el logo terminado.
 */
class LogoAnimadoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var tiempoMs: Float = FIN_ANIMACION_LOGIN_MS
        set(valor) {
            field = valor
            invalidate()
        }

    private val losaIzquierda = trazo("M8 44 H30 L33 49 L29 53 L32 58 H8 Q4 58 4 54 V48 Q4 44 8 44 Z")
    private val losaDerecha = trazo("M34 44 H56 Q60 44 60 48 V54 Q60 58 56 58 H36 L33 53 L37 49 Z")
    private val tallo = trazo("M33 47 C33 39 31 35 32 27")
    private val hojaIzquierda = trazo("M32 35 C24 35 18 30 18 22 C26 22 32 27 32 35 Z")
    private val hojaDerecha = trazo("M32 29 C32 21 38 15 46 15 C46 23 40 29 32 29 Z")

    private val medidaTallo = PathMeasure(tallo, false)
    private val talloParcial = Path()

    private val pinturaPavimento = relleno(R.color.pavimento)
    private val pinturaHojaIzquierda = relleno(R.color.verde_hoja)
    private val pinturaHojaDerecha = relleno(R.color.verde_bosque)
    private val pinturaTallo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.verde_bosque)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val separacion = 2f * (1f - avance(tiempoMs, 100f, 450f, easeOut))
        val dibujado = avance(tiempoMs, 450f, 550f)
        val hojaIzq = avance(tiempoMs, 900f, 450f, rebote)
        val hojaDer = avance(tiempoMs, 1_100f, 450f, rebote)

        canvas.withScale(width / 64f, height / 64f) {
            dibujarMovido(this, losaIzquierda, pinturaPavimento, separacion)
            dibujarMovido(this, losaDerecha, pinturaPavimento, -separacion)

            if (dibujado > 0f) {
                talloParcial.reset()
                medidaTallo.getSegment(0f, medidaTallo.length * dibujado, talloParcial, true)
                drawPath(talloParcial, pinturaTallo)
            }
            if (hojaIzq > 0f) dibujarEscalado(this, hojaIzquierda, pinturaHojaIzquierda, hojaIzq, 32f, 35f)
            if (hojaDer > 0f) dibujarEscalado(this, hojaDerecha, pinturaHojaDerecha, hojaDer, 32f, 29f)
        }
    }

    private fun dibujarMovido(canvas: Canvas, trazo: Path, pintura: Paint, dx: Float) {
        canvas.withTranslation(dx, 0f) { drawPath(trazo, pintura) }
    }

    private fun dibujarEscalado(canvas: Canvas, trazo: Path, pintura: Paint, escala: Float, x: Float, y: Float) {
        canvas.withScale(escala, escala, x, y) { drawPath(trazo, pintura) }
    }

    private fun relleno(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = ContextCompat.getColor(context, color)
        style = Paint.Style.FILL
    }

    private fun trazo(d: String): Path = PathParser.createPathFromPathData(d)
}
