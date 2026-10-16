package co.ecocomunidad.app.ui.registro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.ecocomunidad.app.ui.theme.Fondo
import co.ecocomunidad.app.ui.theme.VerdeBosque

/** Lugar del registro de ciudadanos (RN-01); se construye en el próximo paso. */
@Composable
fun RegistroPantalla(alVolver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Registro (próximo paso)", style = MaterialTheme.typography.headlineSmall, color = VerdeBosque)
        TextButton(onClick = alVolver, modifier = Modifier.padding(top = 8.dp)) {
            Text("Volver a iniciar sesión", style = MaterialTheme.typography.labelLarge, color = VerdeBosque)
        }
    }
}
