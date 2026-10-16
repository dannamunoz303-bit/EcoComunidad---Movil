package co.ecocomunidad.app.ui.navegacion

import co.ecocomunidad.app.data.modelo.Rol
import kotlinx.serialization.Serializable

sealed interface Ruta {
    /**
     * [motivo]: por qué se volvió al login (sesión cerrada, cuenta bloqueada, administrador).
     * [correo] y [cuentaCreada]: al volver de un registro exitoso, con el correo ya escrito.
     */
    @Serializable data class Login(
        val motivo: String? = null,
        val correo: String? = null,
        val cuentaCreada: Boolean = false,
    ) : Ruta
    @Serializable data object Registro : Ruta
    @Serializable data object CambiarContrasena : Ruta
    @Serializable data object InicioCiudadano : Ruta
    @Serializable data object InicioGestor : Ruta
}

/**
 * A dónde va un usuario con sesión. La contraseña pendiente va antes que todo (RN-05).
 * El administrador no entra a la app: se filtra antes de llamar aquí.
 */
fun destinoPara(rol: Rol, debeCambiarContrasena: Boolean): Ruta = when {
    debeCambiarContrasena -> Ruta.CambiarContrasena
    rol == Rol.GESTOR -> Ruta.InicioGestor
    rol == Rol.CIUDADANO -> Ruta.InicioCiudadano
    else -> Ruta.Login(Mensajes.PANEL_WEB)
}
