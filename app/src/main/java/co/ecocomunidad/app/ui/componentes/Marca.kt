package co.ecocomunidad.app.ui.componentes

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.ecocomunidad.app.R
import co.ecocomunidad.app.ui.theme.Pavimento
import co.ecocomunidad.app.ui.theme.VerdeBosque
import co.ecocomunidad.app.ui.theme.VerdeHoja
import co.ecocomunidad.app.ui.theme.VerdeMusgo

/** "Eco" en verde-musgo 500 y "Comunidad" en verde-bosque 700, Bricolage Grotesque (marca.md). */
@Composable
fun NombreMarca(modifier: Modifier = Modifier, estilo: TextStyle = MaterialTheme.typography.headlineMedium) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = VerdeMusgo, fontWeight = FontWeight.Medium)) { append("Eco") }
            withStyle(SpanStyle(color = VerdeBosque, fontWeight = FontWeight.Bold)) { append("Comunidad") }
        },
        style = estilo,
        modifier = modifier,
    )
}

/** Logo a la izquierda y el nombre, para las barras superiores. */
@Composable
fun LogoConNombre(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(36.dp))
        NombreMarca(estilo = MaterialTheme.typography.titleLarge)
    }
}

// Trazos de docs/marca/logo.svg (viewBox 64), separados para animarlos uno por uno
private fun trazo(d: String): Path = PathParser().parsePathString(d).toPath()
private val losaIzquierda = trazo("M8 44 H30 L33 49 L29 53 L32 58 H8 Q4 58 4 54 V48 Q4 44 8 44 Z")
private val losaDerecha = trazo("M34 44 H56 Q60 44 60 48 V54 Q60 58 56 58 H36 L33 53 L37 49 Z")
private val tallo = trazo("M33 47 C33 39 31 35 32 27")
private val hojaIzquierda = trazo("M32 35 C24 35 18 30 18 22 C26 22 32 27 32 35 Z")
private val hojaDerecha = trazo("M32 29 C32 21 38 15 46 15 C46 23 40 29 32 29 Z")

// Curvas de CSS que nombra marca.md
private val easeOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val rebote = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Momento en que termina la animación del login, contando la aparición del formulario. */
const val FIN_ANIMACION_LOGIN_MS = 2_100f

/** Avance (0 a 1) de un paso que empieza en [inicioMs] y dura [duracionMs]. */
fun avance(tiempoMs: Float, inicioMs: Float, duracionMs: Float, curva: Easing = ease): Float =
    curva.transform(((tiempoMs - inicioMs) / duracionMs).coerceIn(0f, 1f))

/**
 * Logo armándose según "Animación del logo" de marca.md. Con [tiempoMs] en [FIN_ANIMACION_LOGIN_MS]
 * se ve el logo terminado.
 */
@Composable
fun LogoAnimado(tiempoMs: Float, tamano: Dp, modifier: Modifier = Modifier) {
    val separacion = 2f * (1f - avance(tiempoMs, 100f, 450f, easeOut))
    val dibujado = avance(tiempoMs, 450f, 550f)
    val hojaIzq = avance(tiempoMs, 900f, 450f, rebote)
    val hojaDer = avance(tiempoMs, 1_100f, 450f, rebote)

    Canvas(modifier.size(tamano)) {
        scale(size.width / 64f, pivot = Offset.Zero) {
            translate(left = separacion) { drawPath(losaIzquierda, Pavimento) }
            translate(left = -separacion) { drawPath(losaDerecha, Pavimento) }

            if (dibujado > 0f) {
                val medida = PathMeasure().apply { setPath(tallo, false) }
                val parte = Path()
                medida.getSegment(0f, medida.length * dibujado, parte, true)
                drawPath(parte, VerdeBosque, style = Stroke(width = 4f, cap = StrokeCap.Round))
            }
            if (hojaIzq > 0f) {
                scale(hojaIzq, pivot = Offset(32f, 35f)) { drawPath(hojaIzquierda, VerdeHoja) }
            }
            if (hojaDer > 0f) {
                scale(hojaDer, pivot = Offset(32f, 29f)) { drawPath(hojaDerecha, VerdeBosque) }
            }
        }
    }
}
