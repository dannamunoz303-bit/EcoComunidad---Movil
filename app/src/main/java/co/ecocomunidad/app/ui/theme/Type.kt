package co.ecocomunidad.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import co.ecocomunidad.app.R

private val proveedor = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val bricolage = GoogleFont("Bricolage Grotesque")
private val instrument = GoogleFont("Instrument Sans")

// Títulos y números grandes: Bricolage Grotesque 500 y 700
val FuenteTitulos = FontFamily(
    Font(googleFont = bricolage, fontProvider = proveedor, weight = FontWeight.Medium),
    Font(googleFont = bricolage, fontProvider = proveedor, weight = FontWeight.Bold),
)

// Texto, formularios y tablas: Instrument Sans 400, 500 y 600
val FuenteTexto = FontFamily(
    Font(googleFont = instrument, fontProvider = proveedor, weight = FontWeight.Normal),
    Font(googleFont = instrument, fontProvider = proveedor, weight = FontWeight.Medium),
    Font(googleFont = instrument, fontProvider = proveedor, weight = FontWeight.SemiBold),
)

private val base = Typography()

val Tipografia = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = FuenteTitulos, fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontFamily = FuenteTitulos, fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontFamily = FuenteTitulos, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = FuenteTitulos, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = FuenteTitulos, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = FuenteTitulos, fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontFamily = FuenteTitulos, fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontFamily = FuenteTexto, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = FuenteTexto, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = FuenteTexto),
    bodyMedium = base.bodyMedium.copy(fontFamily = FuenteTexto),
    bodySmall = base.bodySmall.copy(fontFamily = FuenteTexto),
    labelLarge = base.labelLarge.copy(fontFamily = FuenteTexto, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = FuenteTexto, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = FuenteTexto, fontWeight = FontWeight.Medium),
)
