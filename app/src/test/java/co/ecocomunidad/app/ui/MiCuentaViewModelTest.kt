package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.ui.RepositorioFalso.Companion.usuarioSesion
import co.ecocomunidad.app.ui.contrasena.CambiarContrasenaViewModel
import co.ecocomunidad.app.ui.cuenta.MiCuentaViewModel
import co.ecocomunidad.app.ui.cuenta.OpcionCuenta
import co.ecocomunidad.app.ui.cuenta.inicialesDe
import co.ecocomunidad.app.ui.cuenta.opcionesCuenta
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MiCuentaViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val repositorio = RepositorioFalso().apply { usuario.value = usuarioSesion(Rol.CIUDADANO) }

    @Test
    fun iniciales() {
        assertEquals("AP", inicialesDe("Ana", "Pérez"))
        assertEquals("ÁG", inicialesDe(" ángela", "gómez "))
        assertEquals("L", inicialesDe("Luis", ""))
    }

    @Test
    fun opcionesSegunElRol() {
        assertEquals(
            listOf(
                OpcionCuenta.MIS_NOVEDADES,
                OpcionCuenta.MIS_SANCIONES,
                OpcionCuenta.CAMBIAR_CONTRASENA,
                OpcionCuenta.CERRAR_SESION,
            ),
            opcionesCuenta(Rol.CIUDADANO),
        )
        // RN-19: las sanciones son solo para ciudadanos
        assertEquals(
            listOf(OpcionCuenta.MIS_NOVEDADES, OpcionCuenta.CAMBIAR_CONTRASENA, OpcionCuenta.CERRAR_SESION),
            opcionesCuenta(Rol.GESTOR),
        )
    }

    @Test
    fun muestraLaSesionYPideAuthYo() = runTest(main.despachador) {
        val vm = MiCuentaViewModel(repositorio)
        vm.usuario.launchIn(backgroundScope)

        advanceUntilIdle()

        assertEquals("ana@correo.co", vm.usuario.value?.correo)
        assertEquals(1, repositorio.consultasYo)
    }

    @Test
    fun cerrarSesion_borraLaSesion() = runTest(main.despachador) {
        val vm = MiCuentaViewModel(repositorio)
        var termino = false

        vm.cerrarSesion { termino = true }
        advanceUntilIdle()

        assertEquals(1, repositorio.cierres)
        assertTrue(termino)
    }

    @Test
    fun cambiarContrasenaDesdeMiCuenta_vuelveAtrasSinIrAlInicio() = runTest(main.despachador) {
        val vm = CambiarContrasenaViewModel(repositorio, desdeMiCuenta = true).apply {
            cambiarActual("actual-1")
            cambiarNueva("nueva-clave-9")
            cambiarConfirmar("nueva-clave-9")
        }

        vm.guardar()
        advanceUntilIdle()

        assertEquals(listOf("actual-1" to "nueva-clave-9"), repositorio.cambios)
        assertTrue(vm.estado.value.guardada)
        assertNull(vm.estado.value.destino)
        vm.guardadaAtendida()
        assertFalse(vm.estado.value.guardada)
    }
}
