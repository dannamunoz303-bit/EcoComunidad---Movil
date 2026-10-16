package co.ecocomunidad.app.ui.login

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.ecocomunidad.app.R
import co.ecocomunidad.app.ui.componentes.Aviso
import co.ecocomunidad.app.ui.componentes.BotonPrincipal
import co.ecocomunidad.app.ui.componentes.CampoContrasena
import co.ecocomunidad.app.ui.componentes.CampoTexto
import co.ecocomunidad.app.ui.componentes.FIN_ANIMACION_LOGIN_MS
import co.ecocomunidad.app.ui.componentes.LogoAnimado
import co.ecocomunidad.app.ui.componentes.NombreMarca
import co.ecocomunidad.app.ui.componentes.TarjetaClara
import co.ecocomunidad.app.ui.componentes.avance
import co.ecocomunidad.app.ui.navegacion.Ruta
import co.ecocomunidad.app.ui.theme.Fondo
import co.ecocomunidad.app.ui.theme.TintaSuave
import co.ecocomunidad.app.ui.theme.VerdeBosque

// La animación del logo se ve una sola vez por apertura de la app (marca.md)
private var animacionVista = false

@Composable
fun LoginPantalla(
    viewModel: LoginViewModel,
    alEntrar: (Ruta) -> Unit,
    alRegistrarse: () -> Unit,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val foco = LocalFocusManager.current

    LaunchedEffect(estado.destino) {
        estado.destino?.let {
            viewModel.destinoAtendido()
            alEntrar(it)
        }
    }

    val tiempo = rememberTiempoAnimacion()
    val desplazamiento = with(LocalDensity.current) { 6.dp.toPx() }
    val nombre = avance(tiempo, 1_450f, 400f)
    val formulario = avance(tiempo, 1_700f, 400f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogoAnimado(tiempoMs = tiempo, tamano = 96.dp)
        NombreMarca(
            modifier = Modifier
                .padding(top = 8.dp)
                .graphicsLayer {
                    alpha = nombre
                    translationY = desplazamiento * (1f - nombre)
                },
        )
        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = formulario
                    translationY = desplazamiento * (1f - formulario)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TarjetaClara(Modifier.fillMaxWidth()) {
                Text("Inicia sesión", style = MaterialTheme.typography.headlineSmall, color = VerdeBosque)

                estado.aviso?.let { Aviso(it) }

                CampoTexto(
                    valor = estado.correo,
                    alCambiar = viewModel::cambiarCorreo,
                    etiqueta = "Correo",
                    error = estado.errorCorreo,
                    tipoTeclado = KeyboardType.Email,
                    habilitado = !estado.cargando,
                )
                CampoContrasena(
                    valor = estado.contrasena,
                    alCambiar = viewModel::cambiarContrasena,
                    etiqueta = "Contraseña",
                    error = estado.errorContrasena,
                    accion = ImeAction.Done,
                    alAccion = {
                        foco.clearFocus()
                        viewModel.entrar()
                    },
                    habilitado = !estado.cargando,
                )
                BotonPrincipal(
                    texto = "Entrar",
                    alPulsar = {
                        foco.clearFocus()
                        viewModel.entrar()
                    },
                    cargando = estado.cargando,
                )

                if (estado.cargaLarga) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(
                            painterResource(R.drawable.brotico_piensa),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                        )
                        Text("Revisando tus datos…", style = MaterialTheme.typography.bodyMedium, color = TintaSuave)
                    }
                }
            }

            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("¿No tienes cuenta?", style = MaterialTheme.typography.bodyMedium, color = TintaSuave)
                TextButton(onClick = alRegistrarse, enabled = !estado.cargando) {
                    Text("Regístrate", style = MaterialTheme.typography.labelLarge, color = VerdeBosque)
                }
            }
        }
    }
}

/** Tiempo de la animación del login en ms. Sin animación si el sistema la tiene desactivada. */
@Composable
private fun rememberTiempoAnimacion(): Float {
    val contexto = LocalContext.current
    val tiempo = remember {
        val sinMovimiento = Settings.Global.getFloat(
            contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
        Animatable(if (animacionVista || sinMovimiento) FIN_ANIMACION_LOGIN_MS else 0f)
    }
    LaunchedEffect(Unit) {
        animacionVista = true
        tiempo.animateTo(
            FIN_ANIMACION_LOGIN_MS,
            tween(durationMillis = (FIN_ANIMACION_LOGIN_MS - tiempo.value).toInt(), easing = LinearEasing),
        )
    }
    return tiempo.value
}
