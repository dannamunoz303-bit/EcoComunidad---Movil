package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.EstadoNovedad
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.ui.NovedadesFalso.Companion.novedad
import co.ecocomunidad.app.ui.RepositorioFalso.Companion.usuarioSesion
import co.ecocomunidad.app.ui.novedades.MisNovedadesViewModel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class MisNovedadesViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val repositorio = NovedadesFalso()
    private val sesion = RepositorioFalso().apply { usuario.value = usuarioSesion(Rol.CIUDADANO) }

    @Test
    fun laTareaSeMuestraSoloAlGestor() = runTest(main.despachador) {
        val vm = MisNovedadesViewModel(repositorio, sesion.usuario)
        advanceUntilIdle()
        assertFalse(vm.conTarea.value)

        sesion.usuario.value = usuarioSesion(Rol.GESTOR)
        advanceUntilIdle()
        assertTrue(vm.conTarea.value)
    }

    @Test
    fun lista_masNuevaPrimeroConLaRespuesta() = runTest(main.despachador) {
        repositorio.mias = Resultado.Exito(
            listOf(
                novedad(1, fecha = "2026-10-01T09:00:00-05:00"),
                novedad(3, fecha = "2026-10-07T09:00:00-05:00", estado = EstadoNovedad.ATENDIDA, respuesta = "Ya fuimos."),
                novedad(2, fecha = "2026-10-03T09:00:00-05:00"),
            ),
        )
        val vm = MisNovedadesViewModel(repositorio, sesion.usuario)

        advanceUntilIdle()

        with(vm.estado.value) {
            assertFalse(cargando)
            assertFalse(vacio)
            assertEquals(listOf(3, 2, 1), novedades.map { it.id })
            assertEquals("Ya fuimos.", novedades.first().respuesta)
        }
    }

    @Test
    fun sinNovedades_estadoVacio() = runTest(main.despachador) {
        val vm = MisNovedadesViewModel(repositorio, sesion.usuario)

        advanceUntilIdle()

        assertTrue(vm.estado.value.vacio)
    }

    @Test
    fun error_yReintentar() = runTest(main.despachador) {
        repositorio.mias = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = MisNovedadesViewModel(repositorio, sesion.usuario)
        advanceUntilIdle()

        with(vm.estado.value) {
            assertTrue(error!!.sinConexion)
            assertFalse(vacio)
        }

        repositorio.mias = Resultado.Exito(listOf(novedad(1)))
        vm.reintentar()
        advanceUntilIdle()

        assertEquals(2, repositorio.listados)
        assertEquals(null, vm.estado.value.error)
        assertEquals(1, vm.estado.value.novedades.size)
    }
}
