package co.ecocomunidad.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Solo tema claro y sin colores dinámicos: la app siempre usa la paleta de marca.
private val EsquemaClaro = lightColorScheme(
    primary = VerdeBosque,
    onPrimary = Color.White,
    primaryContainer = VerdeBrote,
    onPrimaryContainer = VerdeBosque,
    secondary = VerdeMusgo,
    onSecondary = Color.White,
    secondaryContainer = VerdeBrote,
    onSecondaryContainer = VerdeBosque,
    tertiary = VerdeHoja,
    onTertiary = Tinta,
    background = Fondo,
    onBackground = Tinta,
    surface = Superficie,
    onSurface = Tinta,
    surfaceVariant = Fondo,
    onSurfaceVariant = TintaSuave,
    outline = TintaSuave,
    outlineVariant = Borde,
    error = Arcilla,
    onError = Color.White,
)

@Composable
fun EcoComunidadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaClaro,
        typography = Tipografia,
        content = content,
    )
}
