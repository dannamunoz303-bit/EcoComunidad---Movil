package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.AuthRepository
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.EstadoUsuario
import co.ecocomunidad.app.data.modelo.RegistroPeticion
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.data.modelo.UsuarioActual
import co.ecocomunidad.app.data.modelo.UsuarioRegistrado
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class RepositorioFalso : AuthRepository {

    override val usuario = MutableStateFlow<UsuarioSesion?>(null)

    var resultadoLogin: Resultado<UsuarioActual> = Resultado.Exito(usuarioActual(Rol.CIUDADANO))
    var resultadoCambio: Resultado<Unit> = Resultado.Exito(Unit)
    var resultadoYo: Resultado<UsuarioActual> = Resultado.Exito(usuarioActual(Rol.CIUDADANO))

    /** Si no es null, login espera a que se complete (para ver el estado de carga). */
    var esperaLogin: CompletableDeferred<Unit>? = null

    val logins = mutableListOf<Pair<String, String>>()
    val cambios = mutableListOf<Pair<String, String>>()
    var cierres = 0

    var resultadoRegistro: Resultado<UsuarioRegistrado> =
        Resultado.Exito(UsuarioRegistrado(id = 9, nombre = "Ana", apellido = "Pérez", correo = "ana@correo.co"))
    val registros = mutableListOf<RegistroPeticion>()

    /** Si no es null, registrar espera a que se complete (para ver el estado de carga). */
    var esperaRegistro: CompletableDeferred<Unit>? = null

    override suspend fun registrar(peticion: RegistroPeticion): Resultado<UsuarioRegistrado> {
        registros += peticion
        esperaRegistro?.await()
        return resultadoRegistro
    }

    override suspend fun login(correo: String, contrasena: String): Resultado<UsuarioActual> {
        logins += correo to contrasena
        esperaLogin?.await()
        return resultadoLogin
    }

    override suspend fun cerrarSesion() {
        cierres++
        usuario.value = null
    }

    override suspend fun cambiarContrasena(contrasenaActual: String, contrasenaNueva: String): Resultado<Unit> {
        cambios += contrasenaActual to contrasenaNueva
        return resultadoCambio
    }

    var consultasYo = 0

    override suspend fun usuarioActual(): Resultado<UsuarioActual> {
        consultasYo++
        return resultadoYo
    }

    companion object {
        fun usuarioActual(rol: Rol, debeCambiar: Boolean = false) = UsuarioActual(
            id = 7, nombre = "Ana", apellido = "Pérez", correo = "ana@correo.co", rol = rol,
            estado = EstadoUsuario.ACTIVO, fechaRegistro = "2026-10-01T10:00:00-05:00",
            debeCambiarContrasena = debeCambiar,
        )

        fun usuarioSesion(rol: Rol, debeCambiar: Boolean = false) = UsuarioSesion(
            id = 7, nombre = "Ana", apellido = "Pérez", correo = "ana@correo.co", rol = rol,
            estado = EstadoUsuario.ACTIVO, debeCambiarContrasena = debeCambiar,
        )

        fun fallo(error: ErrorAuth) = Resultado.Fallo(error)
    }
}

/** viewModelScope usa Dispatchers.Main: en las pruebas se cambia por uno de prueba. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReglaMain(val despachador: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(despachador)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
