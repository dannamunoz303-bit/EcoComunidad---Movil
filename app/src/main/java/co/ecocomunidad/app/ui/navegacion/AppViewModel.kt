package co.ecocomunidad.app.ui.navegacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.AuthRepository
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.data.sesion.SesionCerrada
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Decide la primera pantalla y avisa cuando la sesión se cierra sola. */
class AppViewModel(
    private val repositorio: AuthRepository,
    sesionCerrada: Flow<SesionCerrada>,
) : ViewModel() {

    private val _inicio = MutableStateFlow<Ruta?>(null)

    /** null mientras se lee la sesión guardada. */
    val inicio: StateFlow<Ruta?> = _inicio.asStateFlow()

    private val cierresLocales = Channel<String>(Channel.BUFFERED)

    /** Motivo para volver al login, limpiando la pila. */
    val cierres: Flow<String> = merge(
        sesionCerrada.map { mensajeDeCierre(it.error) },
        cierresLocales.receiveAsFlow(),
    )

    init {
        viewModelScope.launch {
            val usuario = repositorio.usuario.first()
            _inicio.value = when {
                usuario == null -> Ruta.Login()
                usuario.rol == Rol.ADMINISTRADOR -> {
                    repositorio.cerrarSesion()
                    Ruta.Login(Mensajes.PANEL_WEB)
                }
                else -> {
                    revisarEstado()
                    destinoPara(usuario.rol, usuario.debeCambiarContrasena)
                }
            }
        }
    }

    /** La sesión guardada puede ser de alguien que ya fue inactivado o bloqueado (RN-04). */
    private fun revisarEstado() {
        viewModelScope.launch {
            val r = repositorio.usuarioActual()
            val error = (r as? Resultado.Fallo)?.error ?: return@launch
            if (error is ErrorAuth.Inactivo || error is ErrorAuth.Bloqueado || error is ErrorAuth.BloqueadoPermanente) {
                repositorio.cerrarSesion()
                cierresLocales.send(mensajeDeError(error))
            }
        }
    }
}
