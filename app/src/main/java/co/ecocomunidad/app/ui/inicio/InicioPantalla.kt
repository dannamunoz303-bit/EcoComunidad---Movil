package co.ecocomunidad.app.ui.inicio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.ecocomunidad.app.R
import co.ecocomunidad.app.ui.componentes.LogoConNombre
import co.ecocomunidad.app.ui.theme.Fondo
import co.ecocomunidad.app.ui.theme.VerdeBosque
import co.ecocomunidad.app.ui.theme.VerdeBrote

/** Inicio temporal de ciudadano y gestor: saludo de Brotico y cierre de sesión. */
@Composable
fun InicioPantalla(
    viewModel: InicioViewModel,
    nombreRol: String,
    alCerrarSesion: () -> Unit,
) {
    val usuario by viewModel.usuario.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LogoConNombre()
            TextButton(onClick = { viewModel.cerrarSesion(alCerrarSesion) }) {
                Icon(
                    painterResource(R.drawable.ic_salir),
                    contentDescription = null,
                    tint = VerdeBosque,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("Cerrar sesión", style = MaterialTheme.typography.labelLarge, color = VerdeBosque)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Tarjeta destacada en verde bosque (marca.md)
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = VerdeBosque,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painterResource(R.drawable.brotico_saluda),
                    contentDescription = "Brotico saludando",
                    modifier = Modifier.size(140.dp),
                )
                Text(
                    text = usuario?.nombre?.let { "¡Hola de nuevo, $it!" } ?: "¡Hola de nuevo!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
                Text(
                    "Tu barrio te está esperando.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = VerdeBrote,
                )
                Text(
                    nombreRol,
                    style = MaterialTheme.typography.labelMedium,
                    color = VerdeBosque,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .background(VerdeBrote, RoundedCornerShape(percent = 50))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}
