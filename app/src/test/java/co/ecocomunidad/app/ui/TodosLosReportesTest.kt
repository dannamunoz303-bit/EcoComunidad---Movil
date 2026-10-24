package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.EstadoReporte
import co.ecocomunidad.app.data.modelo.EstadoTarea
import co.ecocomunidad.app.ui.ReportesFalso.Companion.reporte
import co.ecocomunidad.app.ui.TareasFalso.Companion.tarea
import co.ecocomunidad.app.ui.reportes.FiltroReportes
import co.ecocomunidad.app.ui.reportes.ListaReportesViewModel
import co.ecocomunidad.app.ui.tareas.AbrirReporteViewModel
import co.ecocomunidad.app.ui.tareas.DestinoReporte
import co.ecocomunidad.app.ui.tareas.destinoReporte
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** "Todos los reportes" del gestor: la lista (ListaReportesViewModel) y a dónde lleva tocar un reporte. */
class TodosLosReportesTest {

    @get:Rule
    val main = ReglaMain()

    private val reportes = ReportesFalso()
    private val tareas = TareasFalso()

    // --- Lista ---

    @Test
    fun lista_cargaTodosSinFiltroDeMios() = runTest(main.despachador) {
        reportes.todos = Resultado.Exito(
            listOf(reporte(3, EstadoReporte.APROBADO), reporte(2, EstadoReporte.RESUELTO), reporte(1, EstadoReporte.ASIGNADO)),
        )
        val vm = ListaReportesViewModel(reportes)

        advanceUntilIdle()

        // RN-10 la aplica el backend: se muestra lo que llega, en el mismo orden
        assertEquals(listOf(false), reportes.listados)
        with(vm.estado.value) {
            assertEquals(FiltroReportes.TODOS, filtro)
            assertEquals(listOf(3, 2, 1), this.reportes.map { it.id })
            assertFalse(cargando)
            assertFalse(vacio)
            assertNull(error)
        }
    }

    @Test
    fun lista_vacia() = runTest(main.despachador) {
        reportes.todos = Resultado.Exito(emptyList())
        val vm = ListaReportesViewModel(reportes)

        advanceUntilIdle()

        assertTrue(vm.estado.value.vacio)
    }

    @Test
    fun lista_errorYReintentar() = runTest(main.despachador) {
        reportes.todos = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = ListaReportesViewModel(reportes)
        advanceUntilIdle()

        assertTrue(vm.estado.value.error?.sinConexion == true)
        assertFalse(vm.estado.value.vacio)

        reportes.todos = Resultado.Exito(listOf(reporte(1, EstadoReporte.APROBADO)))
        vm.reintentar()
        advanceUntilIdle()

        assertNull(vm.estado.value.error)
        assertEquals(listOf(1), vm.estado.value.reportes.map { it.id })
    }

    // --- Tocar un reporte ---

    @Test
    fun destino_conTareaPropiaAbreLaTareaYSinEllaElReporte() {
        val mias = listOf(
            tarea(21, EstadoTarea.EN_PROCESO, reporte = 5),
            tarea(20, EstadoTarea.TERMINADA, reporte = 5),
            tarea(11, EstadoTarea.TERMINADA, reporte = 7),
        )
        assertEquals(DestinoReporte.Tarea(21), destinoReporte(mias, 5))
        // Aunque ya la terminó, sigue siendo su tarea
        assertEquals(DestinoReporte.Tarea(11), destinoReporte(mias, 7))
        assertEquals(DestinoReporte.Reporte(8), destinoReporte(mias, 8))
    }

    @Test
    fun tocar_conTareaPropia_abreSuTarea() = runTest(main.despachador) {
        tareas.mias = Resultado.Exito(listOf(tarea(21, EstadoTarea.ASIGNADA, reporte = 5)))
        val vm = AbrirReporteViewModel(tareas)

        vm.tocar(5)
        advanceUntilIdle()

        assertEquals(DestinoReporte.Tarea(21), vm.estado.value.abrir)
        vm.abrirAtendido()
        assertNull(vm.estado.value.abrir)
    }

    @Test
    fun tocar_sinTareaPropia_abreElReporte() = runTest(main.despachador) {
        tareas.mias = Resultado.Exito(listOf(tarea(21, EstadoTarea.ASIGNADA, reporte = 5)))
        val vm = AbrirReporteViewModel(tareas)

        vm.tocar(9)
        advanceUntilIdle()

        assertEquals(DestinoReporte.Reporte(9), vm.estado.value.abrir)
    }

    @Test
    fun tocar_seBuscaUnaVezAunqueToqueDosVeces() = runTest(main.despachador) {
        val vm = AbrirReporteViewModel(tareas)

        vm.tocar(9)
        vm.tocar(9)
        assertTrue(vm.estado.value.buscando)
        advanceUntilIdle()

        assertEquals(1, tareas.listados)
        assertFalse(vm.estado.value.buscando)
    }

    @Test
    fun tocar_siNoSePuedenPedirSusTareas_avisaYNoAbre() = runTest(main.despachador) {
        tareas.mias = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = AbrirReporteViewModel(tareas)

        vm.tocar(5)
        advanceUntilIdle()

        with(vm.estado.value) {
            assertNull(abrir)
            assertFalse(buscando)
            assertEquals("No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.", aviso)
        }
        vm.avisoAtendido()
        assertNull(vm.estado.value.aviso)
    }
}
