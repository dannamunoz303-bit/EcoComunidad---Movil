package co.ecocomunidad.app.ui.cuenta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.AuthRepository
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "AP" para Ana Pérez. */
fun inicialesDe(nombre: String, apellido: String): String =
    listOf(nombre, apellido).mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }.joinToString("")

/**
 * Mi cuenta: los datos de auth/yo/. Se muestra al instante lo guardado con la sesión y se pide
 * auth/yo/ para actualizarlo (el repositorio guarda la respuesta y [usuario] cambia solo).
 */
class MiCuentaViewModel(private val repositorio: AuthRepository) : ViewModel() {

    val usuario: StateFlow<UsuarioSesion?> =
        repositorio.usuario.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Si falla se queda lo guardado; una sesión cerrada la maneja AppViewModel
        viewModelScope.launch { repositorio.usuarioActual() }
    }

    /** Lo mismo que hacía el botón de la barra del inicio: borrar la sesión y volver al login. */
    fun cerrarSesion(alTerminar: () -> Unit) {
        viewModelScope.launch {
            repositorio.cerrarSesion()
            alTerminar()
        }
    }
}
