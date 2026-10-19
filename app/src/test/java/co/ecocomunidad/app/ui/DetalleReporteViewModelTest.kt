package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.EstadoReporte
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.ui.ReportesFalso.Companion.ANA
import co.ecocomunidad.app.ui.ReportesFalso.Companion.LUIS
import co.ecocomunidad.app.ui.ReportesFalso.Companion.accion
import co.ecocomunidad.app.ui.ReportesFalso.Companion.reporte
import co.ecocomunidad.app.ui.ReportesFalso.Companion.tarea
import co.ecocomunidad.app.ui.reportes.DetalleReporteViewModel
import co.ecocomunidad.app.ui.reportes.LineaDeTiempo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class DetalleReporteViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val repositorio = ReportesFalso()

    // Ana (id 7) tiene la sesión abierta
    private val usuario = MutableStateFlow(RepositorioFalso.usuarioSesion(Rol.CIUDADANO))

    private fun viewModel() = DetalleReporteViewModel(repositorio, usuario, id = 1)

    @Test
    fun reportePropio_conLineaDeTiempoMasRecientePrimero() = runTest(main.despachador) {
        repositorio.detalle = Resultado.Exito(reporte(1, ciudadano = ANA))
        repositorio.tareas = Resultado.Exito(
            listOf(
                tarea(10, LUIS, accion(1, "2026-10-02T09:30:00-05:00")),
                tarea(11, null, accion(2, "2026-10-03T08:00:00-05:00"), accion(3, "2026-10-01T18:00:00-05:00")),
            ),
        )
        val vm = viewModel()

        advanceUntilIdle()

        with(vm.estado.value) {
            assertFalse(cargando)
            assertTrue(esPropio)
            assertEquals(1, reporte?.id)
            val acciones = (linea as LineaDeTiempo.Lista).acciones
            assertEquals(listOf(2, 1, 3), acciones.map { it.accion.id })
            assertEquals(LUIS, acciones[1].gestor)
        }
    }

    @Test
    fun reporteAjeno_sinLineaDeTiempoCuandoElBackendLaNiega() = runTest(main.despachador) {
        repositorio.detalle = Resultado.Exito(reporte(1, ciudadano = null))
        repositorio.tareas = RepositorioFalso.fallo(ErrorAuth.Servidor(403, "No puedes ver las tareas.", null))
        val vm = viewModel()

        advanceUntilIdle()

        assertFalse(vm.estado.value.esPropio)
        assertEquals(LineaDeTiempo.NoDisponible, vm.estado.value.linea)
        // La app no decide por su cuenta: le pregunta al backend
        assertEquals(listOf(1), repositorio.tareasPedidas)
    }

    @Test
    fun rechazadoPropio_traeElMotivo() = runTest(main.despachador) {
        repositorio.detalle = Resultado.Exito(
            reporte(1, EstadoReporte.RECHAZADO, ciudadano = ANA, motivoRechazo = "No es un tema ambiental."),
        )
        val vm = viewModel()

        advanceUntilIdle()

        assertTrue(vm.estado.value.esPropio)
        assertEquals("No es un tema ambiental.", vm.estado.value.reporte?.motivoRechazo)
    }

    @Test
    fun sinAccionesTodavia() = runTest(main.despachador) {
        repositorio.detalle = Resultado.Exito(reporte(1, ciudadano = ANA))
        val vm = viewModel()

        advanceUntilIdle()

        assertTrue((vm.estado.value.linea as LineaDeTiempo.Lista).acciones.isEmpty())
    }

    @Test
    fun cargaLarga_muestraBroticoTrasUnSegundo() = runTest(main.despachador) {
        val espera = CompletableDeferred<Unit>()
        repositorio.esperaDetalle = espera
        val vm = viewModel()

        advanceTimeBy(500)
        assertTrue(vm.estado.value.cargando)
        assertFalse(vm.estado.value.cargaLarga)
        advanceTimeBy(501)
        assertTrue(vm.estado.value.cargaLarga)

        espera.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.estado.value.cargando)
        assertFalse(vm.estado.value.cargaLarga)
    }

    @Test
    fun sinConexion_yReintentar() = runTest(main.despachador) {
        repositorio.detalle = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.estado.value.error!!.sinConexion)
        assertNull(vm.estado.value.reporte)

        repositorio.detalle = Resultado.Exito(reporte(1, ciudadano = ANA))
        vm.reintentar()
        advanceUntilIdle()
        assertNull(vm.estado.value.error)
        assertEquals(1, vm.estado.value.reporte?.id)
    }

    @Test
    fun reporteQueYaNoSePuedeVer() = runTest(main.despachador) {
        repositorio.detalle = RepositorioFalso.fallo(ErrorAuth.Servidor(404, "No encontrado.", null))
        val vm = viewModel()

        advanceUntilIdle()

        assertFalse(vm.estado.value.error!!.sinConexion)
        assertTrue(repositorio.tareasPedidas.isEmpty())
    }

    // --- Editar y eliminar (RN-07) ---

    @Test
    fun soloElDuenoConPendientePuedeModificar() = runTest(main.despachador) {
        val casos = listOf(
            reporte(1, EstadoReporte.PENDIENTE, ciudadano = ANA) to true,
            reporte(1, EstadoReporte.APROBADO, ciudadano = ANA) to false,
            reporte(1, EstadoReporte.RECHAZADO, ciudadano = ANA) to false,
            // Un pendiente ajeno no debería llegar (RN-10), pero tampoco mostraría botones
            reporte(1, EstadoReporte.PENDIENTE, ciudadano = null) to false,
        )
        for ((reporte, esperado) in casos) {
            repositorio.detalle = Resultado.Exito(reporte)
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals("${reporte.estado} ${reporte.ciudadano}", esperado, vm.estado.value.puedeModificar)
        }
    }

    @Test
    fun eliminarConExito() = runTest(main.despachador) {
        repositorio.detalle = Resultado.Exito(reporte(1, EstadoReporte.PENDIENTE, ciudadano = ANA))
        val vm = viewModel()
        advanceUntilIdle()

        vm.eliminar()
        vm.eliminar()
        advanceUntilIdle()

        assertEquals(listOf("eliminar"), repositorio.operaciones)
        assertTrue(vm.estado.value.eliminado)
        assertFalse(vm.estado.value.eliminando)
        vm.eliminadoAtendido()
        assertFalse(vm.estado.value.eliminado)
    }

    @Test
    fun eliminarCuandoYaNoEstaPendiente_muestraElMensajeDelBackend() = runTest(main.despachador) {
        repositorio.detalle = Resultado.Exito(reporte(1, EstadoReporte.PENDIENTE, ciudadano = ANA))
        repositorio.eliminado = RepositorioFalso.fallo(
            ErrorAuth.Validacion(mapOf("detail" to listOf("Solo se puede modificar el reporte mientras está pendiente."))),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.eliminar()
        advanceUntilIdle()

        assertFalse(vm.estado.value.eliminado)
        assertEquals("Solo se puede modificar el reporte mientras está pendiente.", vm.estado.value.avisoEliminar)
        vm.avisoEliminarAtendido()
        assertNull(vm.estado.value.avisoEliminar)
    }

    @Test
    fun eliminarSinPermiso403_muestraElMensajeDelBackend() = runTest(main.despachador) {
        repositorio.eliminado = RepositorioFalso.fallo(
            ErrorAuth.Servidor(403, "Solo el ciudadano que creó el reporte puede modificarlo.", null),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.eliminar()
        advanceUntilIdle()

        assertEquals("Solo el ciudadano que creó el reporte puede modificarlo.", vm.estado.value.avisoEliminar)
    }

    @Test
    fun eliminarSinConexion() = runTest(main.despachador) {
        repositorio.eliminado = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()
        advanceUntilIdle()

        vm.eliminar()
        advanceUntilIdle()

        assertFalse(vm.estado.value.eliminado)
        assertEquals(
            "No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.",
            vm.estado.value.avisoEliminar,
        )
    }

    @Test
    fun errorAlCargarLasAcciones_noOcultaElReporte() = runTest(main.despachador) {
        repositorio.detalle = Resultado.Exito(reporte(1, ciudadano = ANA))
        repositorio.tareas = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()

        advanceUntilIdle()

        assertEquals(1, vm.estado.value.reporte?.id)
        assertTrue(vm.estado.value.linea is LineaDeTiempo.Error)
    }

    @Test
    fun reportarNovedad_soloElDuenoYEnLosEstadosDeRN18() = runTest(main.despachador) {
        val permitidos = setOf(EstadoReporte.ASIGNADO, EstadoReporte.EN_PROCESO, EstadoReporte.RESUELTO)
        for (estado in EstadoReporte.entries) {
            repositorio.detalle = Resultado.Exito(reporte(1, estado, ciudadano = ANA))
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals("$estado propio", estado in permitidos, vm.estado.value.puedeReportarNovedad)
        }
        // Un reporte ajeno nunca muestra el botón, aunque esté en proceso
        repositorio.detalle = Resultado.Exito(reporte(1, EstadoReporte.EN_PROCESO, ciudadano = LUIS))
        val ajeno = viewModel()
        advanceUntilIdle()
        assertFalse(ajeno.estado.value.puedeReportarNovedad)
    }
}
