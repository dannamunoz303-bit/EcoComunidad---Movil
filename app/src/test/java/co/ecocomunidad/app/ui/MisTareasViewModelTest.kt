package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.EstadoTarea
import co.ecocomunidad.app.ui.ReportesFalso.Companion.foto
import co.ecocomunidad.app.ui.ReportesFalso.Companion.reporte
import co.ecocomunidad.app.ui.TareasFalso.Companion.tarea
import co.ecocomunidad.app.ui.tareas.FiltroTareas
import co.ecocomunidad.app.ui.tareas.MisTareasViewModel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class MisTareasViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val tareas = TareasFalso().apply {
        mias = Resultado.Exito(
            listOf(
                tarea(5, EstadoTarea.ASIGNADA, reporte = 1),
                tarea(4, EstadoTarea.EN_PROCESO, reporte = 2),
                tarea(3, EstadoTarea.TERMINADA, reporte = 1),
                tarea(2, EstadoTarea.CANCELADA, reporte = 3),
            ),
        )
    }
    private val reportes = ReportesFalso().apply {
        detalle = Resultado.Exito(reporte(1, fotos = listOf(foto(10))))
    }

    private fun viewModel() = MisTareasViewModel(tareas, reportes)

    @Test
    fun activas_soloAsignadasYEnProceso() = runTest(main.despachador) {
        val vm = viewModel()

        advanceUntilIdle()

        with(vm.estado.value) {
            assertEquals(FiltroTareas.ACTIVAS, filtro)
            assertEquals(listOf(5, 4), visibles.map { it.id })
            assertEquals(2, cantidadActivas)
        }
    }

    @Test
    fun terminadas_sinLasCanceladas() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.elegirFiltro(FiltroTareas.TERMINADAS)

        // Una sola petición: la pestaña filtra lo que ya llegó
        assertEquals(listOf(3), vm.estado.value.visibles.map { it.id })
        assertEquals(1, tareas.listados)
    }

    @Test
    fun sinActivas_estadoVacio() = runTest(main.despachador) {
        tareas.mias = Resultado.Exito(listOf(tarea(3, EstadoTarea.TERMINADA), tarea(2, EstadoTarea.CANCELADA)))
        val vm = viewModel()

        advanceUntilIdle()

        assertTrue(vm.estado.value.vacio)
        assertEquals(0, vm.estado.value.cantidadActivas)
        vm.elegirFiltro(FiltroTareas.TERMINADAS)
        assertFalse(vm.estado.value.vacio)
    }

    @Test
    fun fotos_unaPeticionPorReporteSinRepetirAlRecargar() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()

        // Reportes 1, 2 y 3 (el 1 tiene dos tareas)
        assertEquals(listOf(1, 2, 3), reportes.detallesPedidos.sorted())
        assertEquals("https://fotos/10.jpg", vm.estado.value.fotos[1])

        vm.recargar()
        advanceUntilIdle()

        assertEquals(3, reportes.detallesPedidos.size)
    }

    @Test
    fun siFallaUnaFoto_laTarjetaVaSinElla() = runTest(main.despachador) {
        reportes.detalle = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()

        advanceUntilIdle()

        assertTrue(vm.estado.value.fotos.isEmpty())
        assertEquals(2, vm.estado.value.visibles.size)
    }

    @Test
    fun error_yReintentar() = runTest(main.despachador) {
        val lista = tareas.mias
        tareas.mias = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.estado.value.error!!.sinConexion)
        assertFalse(vm.estado.value.vacio)

        tareas.mias = lista
        vm.reintentar()
        advanceUntilIdle()

        assertNull(vm.estado.value.error)
        assertEquals(2, vm.estado.value.visibles.size)
    }

    @Test
    fun recargaFallida_conservaLaListaYAvisa() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()

        tareas.mias = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        vm.recargar()
        advanceUntilIdle()

        with(vm.estado.value) {
            assertEquals(2, visibles.size)
            assertFalse(recargando)
            assertEquals("No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.", avisoRecarga)
        }
    }

    @Test
    fun alVolverDelDetalle_seVeElEstadoNuevo() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()

        tareas.iniciar(5)
        vm.recargar()
        advanceUntilIdle()

        assertEquals(EstadoTarea.EN_PROCESO, vm.estado.value.visibles.first { it.id == 5 }.estado)
    }
}
