package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.ui.RepositorioFalso.Companion.fallo
import co.ecocomunidad.app.ui.RepositorioFalso.Companion.usuarioSesion
import co.ecocomunidad.app.ui.contrasena.CambiarContrasenaViewModel
import co.ecocomunidad.app.ui.navegacion.Ruta
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class CambiarContrasenaViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val repositorio = RepositorioFalso().apply {
        usuario.value = usuarioSesion(Rol.GESTOR, debeCambiar = true)
    }

    private fun viewModel(nueva: String = "nueva-clave-9", confirmar: String = nueva) =
        CambiarContrasenaViewModel(repositorio).apply {
            cambiarActual("temporal-1")
            cambiarNueva(nueva)
            cambiarConfirmar(confirmar)
        }

    @Test
    fun camposVacios_noLlamaAlBackend() = runTest(main.despachador) {
        val vm = CambiarContrasenaViewModel(repositorio)

        vm.guardar()
        advanceUntilIdle()

        with(vm.estado.value) {
            assertEquals("Escribe tu contraseña actual.", errorActual)
            assertEquals("Escribe la contraseña nueva.", errorNueva)
            assertEquals("Repite la contraseña nueva.", errorConfirmar)
        }
        assertTrue(repositorio.cambios.isEmpty())
    }

    @Test
    fun confirmacionDistinta_noLlamaAlBackend() = runTest(main.despachador) {
        val vm = viewModel(confirmar = "otra-cosa")

        vm.guardar()
        advanceUntilIdle()

        assertEquals("Las contraseñas nuevas no coinciden.", vm.estado.value.errorConfirmar)
        assertTrue(repositorio.cambios.isEmpty())
    }

    @Test
    fun exito_vaAlInicioDelRol() = runTest(main.despachador) {
        val vm = viewModel()

        vm.guardar()
        advanceUntilIdle()

        assertEquals(listOf("temporal-1" to "nueva-clave-9"), repositorio.cambios)
        assertEquals(Ruta.InicioGestor, vm.estado.value.destino)
        assertEquals("", vm.estado.value.actual)
    }

    @Test
    fun erroresDelBackend_vanDebajoDeSuCampo() = runTest(main.despachador) {
        repositorio.resultadoCambio = fallo(
            ErrorAuth.Validacion(
                mapOf(
                    "contrasena_nueva" to listOf(
                        "Esta contraseña es demasiado común.",
                        "Esta contraseña es completamente numérica.",
                    ),
                ),
            ),
        )
        val vm = viewModel(nueva = "12345678")

        vm.guardar()
        advanceUntilIdle()

        with(vm.estado.value) {
            assertEquals("Esta contraseña es demasiado común.\nEsta contraseña es completamente numérica.", errorNueva)
            assertNull(errorActual)
            assertNull(aviso)
            assertNull(destino)
            assertEquals(false, cargando)
        }
    }

    @Test
    fun contrasenaActualIncorrecta() = runTest(main.despachador) {
        repositorio.resultadoCambio = fallo(
            ErrorAuth.Validacion(mapOf("contrasena_actual" to listOf("La contraseña actual no es correcta."))),
        )
        val vm = viewModel()

        vm.guardar()
        advanceUntilIdle()

        assertEquals("La contraseña actual no es correcta.", vm.estado.value.errorActual)
    }

    @Test
    fun sinConexion_muestraAviso() = runTest(main.despachador) {
        repositorio.resultadoCambio = fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()

        vm.guardar()
        advanceUntilIdle()

        assertEquals(
            "No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.",
            vm.estado.value.aviso,
        )
    }

    @Test
    fun cerrarSesion_vuelveAlLogin() = runTest(main.despachador) {
        val vm = viewModel()

        vm.cerrarSesion()
        advanceUntilIdle()

        assertEquals(1, repositorio.cierres)
        assertEquals(Ruta.Login(), vm.estado.value.destino)
    }
}
