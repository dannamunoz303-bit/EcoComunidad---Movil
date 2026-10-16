package co.ecocomunidad.app.ui.contrasena

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.ecocomunidad.app.ui.componentes.Aviso
import co.ecocomunidad.app.ui.componentes.BotonPrincipal
import co.ecocomunidad.app.ui.componentes.CampoContrasena
import co.ecocomunidad.app.ui.componentes.LogoConNombre
import co.ecocomunidad.app.ui.componentes.TarjetaClara
import co.ecocomunidad.app.ui.navegacion.Ruta
import co.ecocomunidad.app.ui.theme.Fondo
import co.ecocomunidad.app.ui.theme.TintaSuave
import co.ecocomunidad.app.ui.theme.VerdeBosque

@Composable
fun CambiarContrasenaPantalla(
    viewModel: CambiarContrasenaViewModel,
    alTerminar: (Ruta) -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val foco = LocalFocusManager.current

    LaunchedEffect(estado.destino) {
        estado.destino?.let {
            viewModel.destinoAtendido()
            alTerminar(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogoConNombre(Modifier.widthIn(max = 480.dp).fillMaxWidth())
        Spacer(Modifier.height(24.dp))

        TarjetaClara(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Text("Cambia tu contraseña", style = MaterialTheme.typography.headlineSmall, color = VerdeBosque)
            Text(
                "Tu contraseña es temporal. Antes de continuar, escribe una nueva que solo tú conozcas.",
                style = MaterialTheme.typography.bodyMedium,
                color = TintaSuave,
            )

            estado.aviso?.let { Aviso(it) }

            CampoContrasena(
                valor = estado.actual,
                alCambiar = viewModel::cambiarActual,
                etiqueta = "Contraseña actual",
                error = estado.errorActual,
                habilitado = !estado.cargando,
            )
            CampoContrasena(
                valor = estado.nueva,
                alCambiar = viewModel::cambiarNueva,
                etiqueta = "Contraseña nueva",
                error = estado.errorNueva,
                habilitado = !estado.cargando,
            )
            CampoContrasena(
                valor = estado.confirmar,
                alCambiar = viewModel::cambiarConfirmar,
                etiqueta = "Repite la contraseña nueva",
                error = estado.errorConfirmar,
                accion = ImeAction.Done,
                alAccion = {
                    foco.clearFocus()
                    viewModel.guardar()
                },
                habilitado = !estado.cargando,
            )
            BotonPrincipal(
                texto = "Guardar contraseña",
                alPulsar = {
                    foco.clearFocus()
                    viewModel.guardar()
                },
                cargando = estado.cargando,
            )
        }

        TextButton(
            onClick = viewModel::cerrarSesion,
            enabled = !estado.cargando,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("Cerrar sesión", style = MaterialTheme.typography.labelLarge, color = VerdeBosque)
        }
    }
}
