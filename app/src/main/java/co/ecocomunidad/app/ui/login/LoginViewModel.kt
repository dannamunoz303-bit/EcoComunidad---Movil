package co.ecocomunidad.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.AuthRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.ui.navegacion.Mensajes
import co.ecocomunidad.app.ui.navegacion.Ruta
import co.ecocomunidad.app.ui.navegacion.destinoPara
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginEstado(
    val correo: String = "",
    val contrasena: String = "",
    val errorCorreo: String? = null,
    val errorContrasena: String? = null,
    val cargando: Boolean = false,
    /** La carga pasó de 1 s: se muestra Brotico pensando (marca.md). */
    val cargaLarga: Boolean = false,
    /** Error del último intento o motivo por el que se volvió al login. */
    val aviso: String? = null,
    /** Se acaba de crear la cuenta en el registro; se borra al intentar entrar. */
    val cuentaCreada: Boolean = false,
    /** A dónde ir tras entrar; la pantalla navega y llama a [LoginViewModel.destinoAtendido]. */
    val destino: Ruta? = null,
)

class LoginViewModel(
    private val repositorio: AuthRepository,
    motivo: String? = null,
    correo: String? = null,
    cuentaCreada: Boolean = false,
) : ViewModel() {

    private val _estado = MutableStateFlow(
        LoginEstado(correo = correo.orEmpty(), aviso = motivo, cuentaCreada = cuentaCreada),
    )
    val estado: StateFlow<LoginEstado> = _estado.asStateFlow()

    fun cambiarCorreo(valor: String) = _estado.update { it.copy(correo = valor, errorCorreo = null) }

    fun cambiarContrasena(valor: String) = _estado.update { it.copy(contrasena = valor, errorContrasena = null) }

    fun entrar() {
        val actual = _estado.value
        if (actual.cargando) return

        val correo = actual.correo.trim()
        val errorCorreo = if (correo.isEmpty()) "Escribe tu correo." else null
        val errorContrasena = if (actual.contrasena.isEmpty()) "Escribe tu contraseña." else null
        if (errorCorreo != null || errorContrasena != null) {
            _estado.update { it.copy(errorCorreo = errorCorreo, errorContrasena = errorContrasena) }
            return
        }

        _estado.update { it.copy(cargando = true, aviso = null, cuentaCreada = false) }
        viewModelScope.launch {
            val avisoLento = launch {
                delay(UN_SEGUNDO)
                _estado.update { it.copy(cargaLarga = true) }
            }
            val r = repositorio.login(correo, actual.contrasena)
            avisoLento.cancel()

            when (r) {
                is Resultado.Exito -> {
                    val usuario = r.datos
                    if (usuario.rol == Rol.ADMINISTRADOR) {
                        // El administrador no entra a la app: no se deja la sesión guardada
                        repositorio.cerrarSesion()
                        _estado.update { it.copy(cargando = false, cargaLarga = false, aviso = Mensajes.PANEL_WEB) }
                    } else {
                        _estado.update {
                            it.copy(
                                cargando = false,
                                cargaLarga = false,
                                contrasena = "",
                                destino = destinoPara(usuario.rol, usuario.debeCambiarContrasena),
                            )
                        }
                    }
                }
                is Resultado.Fallo -> _estado.update {
                    it.copy(cargando = false, cargaLarga = false, aviso = mensajeDeError(r.error))
                }
            }
        }
    }

    fun destinoAtendido() = _estado.update { it.copy(destino = null) }

    private companion object {
        const val UN_SEGUNDO = 1_000L
    }
}
