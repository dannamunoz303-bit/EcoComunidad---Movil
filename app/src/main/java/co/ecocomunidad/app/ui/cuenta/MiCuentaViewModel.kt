package co.ecocomunidad.app.ui.cuenta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.AuthRepository
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "AP" para Ana Pérez. */
fun inicialesDe(nombre: String, apellido: String): String =
    listOf(nombre, apellido).mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }.joinToString("")

/** Opciones de "Mi cuenta", en el orden de la pantalla. */
enum class OpcionCuenta { TODOS_LOS_REPORTES, MIS_NOVEDADES, MIS_SANCIONES, CAMBIAR_CONTRASENA, CERRAR_SESION }

/**
 * El gestor ve "Todos los reportes" (RN-10; el ciudadano ya los tiene en su inicio) y no ve
 * "Mis sanciones", que son solo para ciudadanos (RN-19).
 */
fun opcionesCuenta(rol: Rol): List<OpcionCuenta> = when (rol) {
    Rol.CIUDADANO -> OpcionCuenta.entries - OpcionCuenta.TODOS_LOS_REPORTES
    else -> OpcionCuenta.entries - OpcionCuenta.MIS_SANCIONES
}

/**
 * Mi cuenta del ciudadano y del gestor: los datos de auth/yo/. Se muestra al instante lo guardado con la sesión y se pide
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
