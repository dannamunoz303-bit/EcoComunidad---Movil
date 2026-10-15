package co.ecocomunidad.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import co.ecocomunidad.app.ui.theme.EcoComunidadTheme
import co.ecocomunidad.app.ui.theme.VerdeBosque

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EcoComunidadTheme {
                PantallaTemporal()
            }
        }
    }
}

// Pantalla temporal para comprobar el tema; se reemplaza cuando exista la navegación.
@Composable
private fun PantallaTemporal() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "EcoComunidad",
            style = MaterialTheme.typography.headlineLarge,
            color = VerdeBosque,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PantallaTemporalPreview() {
    EcoComunidadTheme {
        PantallaTemporal()
    }
}
