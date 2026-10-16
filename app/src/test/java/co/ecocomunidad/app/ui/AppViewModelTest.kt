package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.modelo.ErrorBackend
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.data.sesion.SesionCerrada
import co.ecocomunidad.app.ui.RepositorioFalso.Companion.fallo
import co.ecocomunidad.app.ui.RepositorioFalso.Companion.usuarioSesion
import co.ecocomunidad.app.ui.navegacion.AppViewModel
import co.ecocomunidad.app.ui.navegacion.Mensajes
import co.ecocomunidad.app.ui.navegacion.Ruta
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val repositorio = RepositorioFalso()

    @Test
    fun sinSesion_empiezaEnLogin() = runTest(main.despachador) {
        val vm = AppViewModel(repositorio, emptyFlow())
        advanceUntilIdle()
        assertEquals(Ruta.Login(), vm.inicio.value)
    }

    @Test
    fun sesionDeCiudadanoConContrasenaPendiente_empiezaEnCambiarla() = runTest(main.despachador) {
        repositorio.usuario.value = usuarioSesion(Rol.CIUDADANO, debeCambiar = true)
        val vm = AppViewModel(repositorio, emptyFlow())
        advanceUntilIdle()
        assertEquals(Ruta.CambiarContrasena, vm.inicio.value)
    }

    @Test
    fun sesionDeAdministrador_seCierra() = runTest(main.despachador) {
        repositorio.usuario.value = usuarioSesion(Rol.ADMINISTRADOR)
        val vm = AppViewModel(repositorio, emptyFlow())
        advanceUntilIdle()
        assertEquals(Ruta.Login(Mensajes.PANEL_WEB), vm.inicio.value)
        assertEquals(1, repositorio.cierres)
    }

    @Test
    fun sesionGuardadaDeUsuarioYaInactivo_seCierraConMotivo() = runTest(main.despachador) {
        repositorio.usuario.value = usuarioSesion(Rol.GESTOR)
        repositorio.resultadoYo = fallo(ErrorAuth.Inactivo("x"))
        val vm = AppViewModel(repositorio, emptyFlow())
        advanceUntilIdle()

        assertEquals(Ruta.InicioGestor, vm.inicio.value)
        assertEquals("Tu cuenta está inactiva y no puede iniciar sesión.", vm.cierres.first())
        assertEquals(1, repositorio.cierres)
    }

    @Test
    fun sesionCerradaPorBloqueo_traeLaFecha() = runTest(main.despachador) {
        val evento = SesionCerrada(ErrorBackend("x", "usuario_bloqueado", "2026-10-20T08:30:00-05:00"))
        val vm = AppViewModel(repositorio, flowOf(evento))

        val motivo = vm.cierres.first()

        assertTrue(motivo, motivo.startsWith("Tu cuenta está bloqueada hasta el 20 de octubre de 2026 a las 8:30"))
    }

    @Test
    fun sesionVencida_mensajeSobrio() = runTest(main.despachador) {
        val vm = AppViewModel(repositorio, flowOf(SesionCerrada(ErrorBackend(code = "token_not_valid"))))
        assertEquals(Mensajes.SESION_TERMINADA, vm.cierres.first())
    }
}
