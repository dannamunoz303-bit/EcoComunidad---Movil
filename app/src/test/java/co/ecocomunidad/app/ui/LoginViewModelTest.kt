package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.ui.RepositorioFalso.Companion.fallo
import co.ecocomunidad.app.ui.RepositorioFalso.Companion.usuarioActual
import co.ecocomunidad.app.ui.login.LoginViewModel
import co.ecocomunidad.app.ui.navegacion.Mensajes
import co.ecocomunidad.app.ui.navegacion.Ruta
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

class LoginViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val repositorio = RepositorioFalso()

    private fun viewModel(motivo: String? = null) = LoginViewModel(repositorio, motivo).apply {
        cambiarCorreo(" ana@correo.co ")
        cambiarContrasena("clave-segura-1")
    }

    private fun entrarCon(error: ErrorAuth): String? = run {
        repositorio.resultadoLogin = fallo(error)
        val vm = viewModel()
        vm.entrar()
        main.despachador.scheduler.advanceUntilIdle()
        vm.estado.value.aviso
    }

    @Test
    fun camposVacios_noLlamaAlBackend() = runTest(main.despachador) {
        val vm = LoginViewModel(repositorio)

        vm.entrar()
        advanceUntilIdle()

        assertEquals("Escribe tu correo.", vm.estado.value.errorCorreo)
        assertEquals("Escribe tu contraseña.", vm.estado.value.errorContrasena)
        assertTrue(repositorio.logins.isEmpty())

        vm.cambiarCorreo("a")
        assertNull(vm.estado.value.errorCorreo)
    }

    @Test
    fun ciudadano_vaASuInicio() = runTest(main.despachador) {
        val vm = viewModel()

        vm.entrar()
        advanceUntilIdle()

        assertEquals(listOf("ana@correo.co" to "clave-segura-1"), repositorio.logins)
        assertEquals(Ruta.InicioCiudadano, vm.estado.value.destino)
        vm.destinoAtendido()
        assertNull(vm.estado.value.destino)
    }

    @Test
    fun gestor_vaASuInicio() = runTest(main.despachador) {
        repositorio.resultadoLogin = Resultado.Exito(usuarioActual(Rol.GESTOR))
        val vm = viewModel()

        vm.entrar()
        advanceUntilIdle()

        assertEquals(Ruta.InicioGestor, vm.estado.value.destino)
    }

    @Test
    fun contrasenaPendiente_vaACambiarla() = runTest(main.despachador) {
        repositorio.resultadoLogin = Resultado.Exito(usuarioActual(Rol.GESTOR, debeCambiar = true))
        val vm = viewModel()

        vm.entrar()
        advanceUntilIdle()

        assertEquals(Ruta.CambiarContrasena, vm.estado.value.destino)
    }

    @Test
    fun administrador_noEntraYSeCierraLaSesion() = runTest(main.despachador) {
        repositorio.resultadoLogin = Resultado.Exito(usuarioActual(Rol.ADMINISTRADOR))
        val vm = viewModel()

        vm.entrar()
        advanceUntilIdle()

        assertNull(vm.estado.value.destino)
        assertEquals(Mensajes.PANEL_WEB, vm.estado.value.aviso)
        assertEquals(1, repositorio.cierres)
    }

    @Test
    fun mientrasCarga_bloqueaYTrasUnSegundoMuestraBrotico() = runTest(main.despachador) {
        val espera = CompletableDeferred<Unit>()
        repositorio.esperaLogin = espera
        val vm = viewModel()

        vm.entrar()
        runCurrent()
        assertTrue(vm.estado.value.cargando)
        assertFalse(vm.estado.value.cargaLarga)

        vm.entrar()
        advanceTimeBy(1_001)
        assertTrue(vm.estado.value.cargaLarga)
        assertEquals(1, repositorio.logins.size)

        espera.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.estado.value.cargando)
        assertFalse(vm.estado.value.cargaLarga)
    }

    @Test
    fun usuarioInactivo() {
        assertEquals("Tu cuenta está inactiva y no puede iniciar sesión.", entrarCon(ErrorAuth.Inactivo("x")))
    }

    @Test
    fun usuarioBloqueado_muestraLaFechaEnHoraDeColombia() {
        // 13:30 UTC son las 8:30 en Colombia
        val aviso = entrarCon(ErrorAuth.Bloqueado("x", OffsetDateTime.parse("2026-10-20T13:30:00Z")))!!

        assertTrue(aviso, aviso.startsWith("Tu cuenta está bloqueada hasta el 20 de octubre de 2026 a las 8:30"))
    }

    @Test
    fun usuarioBloqueadoPermanente() {
        assertEquals(
            "Tu cuenta está bloqueada de forma permanente.",
            entrarCon(ErrorAuth.BloqueadoPermanente("x")),
        )
    }

    @Test
    fun credencialesIncorrectas() {
        assertEquals(
            "El correo o la contraseña no son correctos.",
            entrarCon(ErrorAuth.CredencialesInvalidas("No active account found with the given credentials")),
        )
    }

    @Test
    fun sinConexion() {
        assertEquals(
            "No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.",
            entrarCon(ErrorAuth.SinConexion(IOException())),
        )
    }

    @Test
    fun trasRegistrarse_traeElCorreoYElAvisoDeCuentaCreada() {
        val vm = LoginViewModel(repositorio, correo = "ana@correo.co", cuentaCreada = true)

        assertEquals("ana@correo.co", vm.estado.value.correo)
        assertTrue(vm.estado.value.cuentaCreada)
        assertNull(vm.estado.value.aviso)
    }

    @Test
    fun avisoDeCuentaCreada_seBorraAlIntentarEntrar() = runTest(main.despachador) {
        val vm = LoginViewModel(repositorio, correo = "ana@correo.co", cuentaCreada = true)
        vm.cambiarContrasena("clave-segura-1")

        vm.entrar()
        runCurrent()

        assertFalse(vm.estado.value.cuentaCreada)
        assertEquals(listOf("ana@correo.co" to "clave-segura-1"), repositorio.logins)
    }

    @Test
    fun motivoDeCierre_seMuestraYSeBorraAlIntentarDeNuevo() = runTest(main.despachador) {
        val vm = viewModel(motivo = Mensajes.SESION_TERMINADA)
        assertEquals(Mensajes.SESION_TERMINADA, vm.estado.value.aviso)

        vm.entrar()
        advanceUntilIdle()

        assertNull(vm.estado.value.aviso)
    }
}
