package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.ui.ReportesFalso.Companion.reporte
import co.ecocomunidad.app.ui.reportes.FiltroReportes
import co.ecocomunidad.app.ui.reportes.ListaReportesViewModel
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

class ListaReportesViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val repositorio = ReportesFalso()

    @Test
    fun alAbrir_cargaTodosYMuestraLoQueDevuelveLaApi() = runTest(main.despachador) {
        val espera = CompletableDeferred<Unit>()
        repositorio.esperaListar = espera
        val vm = ListaReportesViewModel(repositorio)

        runCurrent()
        assertTrue(vm.estado.value.cargando)
        assertFalse(vm.estado.value.cargaLarga)
        advanceTimeBy(1_001)
        assertTrue(vm.estado.value.cargaLarga)

        espera.complete(Unit)
        advanceUntilIdle()
        with(vm.estado.value) {
            assertEquals(FiltroReportes.TODOS, filtro)
            assertEquals(listOf(1, 2), reportes.map { it.id })
            assertFalse(cargando)
            assertFalse(cargaLarga)
            assertFalse(vacio)
        }
        assertEquals(listOf(false), repositorio.listados)
    }

    @Test
    fun misReportes_usaElFiltroDelBackendYNoSeVuelveAPedirAlRegresar() = runTest(main.despachador) {
        val vm = ListaReportesViewModel(repositorio)
        advanceUntilIdle()

        vm.elegirFiltro(FiltroReportes.MIOS)
        advanceUntilIdle()
        assertEquals(FiltroReportes.MIOS, vm.estado.value.filtro)
        assertEquals(listOf(1), vm.estado.value.reportes.map { it.id })

        vm.elegirFiltro(FiltroReportes.TODOS)
        advanceUntilIdle()
        assertEquals(listOf(1, 2), vm.estado.value.reportes.map { it.id })
        // Todos ya estaba cargado: solo se pidió una vez cada pestaña
        assertEquals(listOf(false, true), repositorio.listados)
    }

    @Test
    fun listaVacia() = runTest(main.despachador) {
        repositorio.todos = Resultado.Exito(emptyList())
        val vm = ListaReportesViewModel(repositorio)

        advanceUntilIdle()

        assertTrue(vm.estado.value.vacio)
        assertNull(vm.estado.value.error)
    }

    @Test
    fun sinConexion_errorConBroticoYReintentar() = runTest(main.despachador) {
        repositorio.todos = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = ListaReportesViewModel(repositorio)
        advanceUntilIdle()

        val error = vm.estado.value.error!!
        assertTrue(error.sinConexion)
        assertEquals("No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.", error.mensaje)
        assertFalse(vm.estado.value.vacio)

        repositorio.todos = Resultado.Exito(listOf(reporte(5)))
        vm.reintentar()
        advanceUntilIdle()
        assertNull(vm.estado.value.error)
        assertEquals(listOf(5), vm.estado.value.reportes.map { it.id })
    }

    @Test
    fun errorDelServidor_esSobrio() = runTest(main.despachador) {
        repositorio.todos = RepositorioFalso.fallo(ErrorAuth.Servidor(500, null, null))
        val vm = ListaReportesViewModel(repositorio)

        advanceUntilIdle()

        val error = vm.estado.value.error!!
        assertFalse(error.sinConexion)
        assertEquals("El servidor no respondió bien. Inténtalo en unos minutos.", error.mensaje)
    }

    @Test
    fun recargar_mantieneLaListaMientrasCargaYLaReemplaza() = runTest(main.despachador) {
        val vm = ListaReportesViewModel(repositorio)
        advanceUntilIdle()
        repositorio.todos = Resultado.Exito(listOf(reporte(3), reporte(2), reporte(1)))
        val espera = CompletableDeferred<Unit>()
        repositorio.esperaListar = espera

        vm.recargar()
        runCurrent()
        assertTrue(vm.estado.value.recargando)
        assertEquals(listOf(1, 2), vm.estado.value.reportes.map { it.id })

        espera.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.estado.value.recargando)
        assertEquals(listOf(3, 2, 1), vm.estado.value.reportes.map { it.id })
    }

    @Test
    fun recargaFallida_conservaLaListaYAvisa() = runTest(main.despachador) {
        val vm = ListaReportesViewModel(repositorio)
        advanceUntilIdle()
        repositorio.todos = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))

        vm.recargar()
        advanceUntilIdle()

        with(vm.estado.value) {
            assertEquals(listOf(1, 2), reportes.map { it.id })
            assertNull(error)
            assertFalse(recargando)
            assertEquals("No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.", avisoRecarga)
        }
        vm.avisoAtendido()
        assertNull(vm.estado.value.avisoRecarga)
    }

    @Test
    fun trasCrearUnReporte_muestraMisReportesRecienPedidos() = runTest(main.despachador) {
        val vm = ListaReportesViewModel(repositorio)
        advanceUntilIdle()
        vm.elegirFiltro(FiltroReportes.MIOS)
        advanceUntilIdle()
        vm.elegirFiltro(FiltroReportes.TODOS)
        repositorio.mios = Resultado.Exito(listOf(reporte(9), reporte(1)))

        vm.reporteCreado()
        advanceUntilIdle()

        assertEquals(FiltroReportes.MIOS, vm.estado.value.filtro)
        assertEquals(listOf(9, 1), vm.estado.value.reportes.map { it.id })
        // Se pidió de nuevo aunque ya estaba guardada
        assertEquals(listOf(false, true, true), repositorio.listados)

        // "Todos" también quedó vieja: se vuelve a pedir
        vm.elegirFiltro(FiltroReportes.TODOS)
        advanceUntilIdle()
        assertEquals(listOf(false, true, true, false), repositorio.listados)
    }

    @Test
    fun trasEditarOEliminar_recargaLaPestanaSinQuitarLaLista() = runTest(main.despachador) {
        val vm = ListaReportesViewModel(repositorio)
        advanceUntilIdle()
        vm.elegirFiltro(FiltroReportes.MIOS)
        advanceUntilIdle()
        repositorio.mios = Resultado.Exito(emptyList())
        val espera = CompletableDeferred<Unit>()
        repositorio.esperaListar = espera

        vm.reportesCambiados()
        runCurrent()
        assertTrue(vm.estado.value.recargando)
        assertEquals(listOf(1), vm.estado.value.reportes.map { it.id })

        espera.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.estado.value.vacio)
        // "Todos" también se vuelve a pedir
        vm.elegirFiltro(FiltroReportes.TODOS)
        advanceUntilIdle()
        assertEquals(listOf(false, true, true, false), repositorio.listados)
    }

    @Test
    fun sinPaginacion_unaSolaPeticionTraeTodaLaLista() = runTest(main.despachador) {
        // El backend no pagina (no hay DEFAULT_PAGINATION_CLASS): la lista llega completa
        repositorio.todos = Resultado.Exito((1..120).map { reporte(it) })
        val vm = ListaReportesViewModel(repositorio)

        advanceUntilIdle()

        assertEquals(120, vm.estado.value.reportes.size)
        assertEquals(1, repositorio.listados.size)
    }
}
