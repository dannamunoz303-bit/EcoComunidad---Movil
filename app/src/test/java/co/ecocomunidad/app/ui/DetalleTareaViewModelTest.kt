package co.ecocomunidad.app.ui

import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.EstadoTarea
import co.ecocomunidad.app.ui.ReportesFalso.Companion.accion
import co.ecocomunidad.app.ui.ReportesFalso.Companion.foto
import co.ecocomunidad.app.ui.ReportesFalso.Companion.reporte
import co.ecocomunidad.app.ui.TareasFalso.Companion.tarea
import co.ecocomunidad.app.ui.tareas.AccionDisponible
import co.ecocomunidad.app.ui.tareas.DetalleTareaViewModel
import co.ecocomunidad.app.ui.tareas.MisAcciones
import co.ecocomunidad.app.ui.tareas.accionDisponible
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class DetalleTareaViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val tareas = TareasFalso()
    private val reportes = ReportesFalso().apply {
        detalle = Resultado.Exito(reporte(1, fotos = listOf(foto(10))))
    }

    private fun viewModel(id: Int = 12) = DetalleTareaViewModel(tareas, reportes, id)

    private fun conTarea(estado: EstadoTarea) {
        tareas.mias = Resultado.Exito(listOf(tarea(12, estado)))
    }

    @Test
    fun botonesSegunElEstado() {
        assertEquals(AccionDisponible.INICIAR, accionDisponible(EstadoTarea.ASIGNADA))
        assertEquals(AccionDisponible.TERMINAR, accionDisponible(EstadoTarea.EN_PROCESO))
        assertEquals(AccionDisponible.NINGUNA, accionDisponible(EstadoTarea.TERMINADA))
        assertEquals(AccionDisponible.NINGUNA, accionDisponible(EstadoTarea.CANCELADA))
    }

    @Test
    fun registrarAccion_soloConLaTareaAsignadaOEnProceso() = runTest(main.despachador) {
        val esperado = mapOf(
            EstadoTarea.ASIGNADA to true,
            EstadoTarea.EN_PROCESO to true,
            EstadoTarea.TERMINADA to false,
            EstadoTarea.CANCELADA to false,
        )
        for ((estado, puede) in esperado) {
            conTarea(estado)
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals("$estado", puede, vm.estado.value.puedeRegistrarAccion)
        }
    }

    @Test
    fun alVolverConLaAccionGuardada_recargaMisAccionesYAvisa() = runTest(main.despachador) {
        conTarea(EstadoTarea.EN_PROCESO)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(MisAcciones.Lista(emptyList()), vm.estado.value.acciones)

        tareas.acciones = Resultado.Exito(listOf(accion(40, "2026-10-05T10:00:00-05:00")))
        vm.accionRegistrada()
        advanceUntilIdle()

        assertEquals(listOf(40), (vm.estado.value.acciones as MisAcciones.Lista).acciones.map { it.id })
        assertEquals(R.string.accion_registrada, vm.estado.value.mensaje)
        assertFalse(vm.estado.value.cargando)
    }

    @Test
    fun cargaLaTareaElReporteYMisAcciones() = runTest(main.despachador) {
        conTarea(EstadoTarea.EN_PROCESO)
        tareas.acciones = Resultado.Exito(listOf(accion(2, "2026-10-03T10:00:00-05:00"), accion(1, "2026-10-02T10:00:00-05:00")))
        val vm = viewModel()

        advanceUntilIdle()

        with(vm.estado.value) {
            assertFalse(cargando)
            assertEquals(12, tarea?.id)
            assertEquals(AccionDisponible.TERMINAR, accion)
            assertEquals("https://fotos/10.jpg", reporte?.fotos?.single()?.url)
            assertEquals(listOf(2, 1), (acciones as MisAcciones.Lista).acciones.map { it.id })
        }
        assertEquals(listOf(1), reportes.detallesPedidos)
    }

    @Test
    fun sinAcciones_listaVacia() = runTest(main.despachador) {
        conTarea(EstadoTarea.ASIGNADA)
        val vm = viewModel()

        advanceUntilIdle()

        assertEquals(MisAcciones.Lista(emptyList()), vm.estado.value.acciones)
        assertEquals(AccionDisponible.INICIAR, vm.estado.value.accion)
    }

    @Test
    fun siNoSePuedePedirElReporte_igualMuestraLaTarea() = runTest(main.despachador) {
        conTarea(EstadoTarea.TERMINADA)
        reportes.detalle = RepositorioFalso.fallo(ErrorAuth.Servidor(404, "No encontrado.", null))
        val vm = viewModel()

        advanceUntilIdle()

        with(vm.estado.value) {
            assertNull(error)
            assertNull(reporte)
            assertEquals("Basura en la esquina", tarea?.reporte?.titulo)
            assertEquals(AccionDisponible.NINGUNA, accion)
        }
    }

    @Test
    fun tareaQueNoEsMia_muestraError() = runTest(main.despachador) {
        conTarea(EstadoTarea.ASIGNADA)
        val vm = viewModel(id = 99)

        advanceUntilIdle()

        assertEquals("No encontramos esta tarea entre las tuyas.", vm.estado.value.error?.mensaje)
        assertNull(vm.estado.value.tarea)
    }

    @Test
    fun iniciar_recargaYMuestraMensaje() = runTest(main.despachador) {
        conTarea(EstadoTarea.ASIGNADA)
        val vm = viewModel()
        advanceUntilIdle()
        val listadosAntes = tareas.listados

        vm.iniciar()
        advanceUntilIdle()

        assertEquals(listOf("iniciar 12"), tareas.operaciones)
        with(vm.estado.value) {
            assertFalse(procesando)
            assertEquals(EstadoTarea.EN_PROCESO, tarea?.estado)
            assertEquals(AccionDisponible.TERMINAR, accion)
            assertEquals(R.string.tarea_iniciada, mensaje)
        }
        assertTrue(tareas.listados > listadosAntes)
        vm.mensajeAtendido()
        assertNull(vm.estado.value.mensaje)
    }

    @Test
    fun terminar_quedaTerminadaSinBotones() = runTest(main.despachador) {
        conTarea(EstadoTarea.EN_PROCESO)
        val vm = viewModel()
        advanceUntilIdle()

        vm.terminar()
        advanceUntilIdle()

        assertEquals(EstadoTarea.TERMINADA, vm.estado.value.tarea?.estado)
        assertEquals(AccionDisponible.NINGUNA, vm.estado.value.accion)
        assertEquals(R.string.tarea_terminada, vm.estado.value.mensaje)
    }

    @Test
    fun terminarSinEvidencia_muestraElMensajeDelBackend() = runTest(main.despachador) {
        conTarea(EstadoTarea.EN_PROCESO)
        tareas.resultadoTerminar = RepositorioFalso.fallo(
            ErrorAuth.Validacion(
                mapOf("detail" to listOf("Para terminar la tarea registra al menos una acción con una foto de evidencia.")),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.terminar()
        advanceUntilIdle()

        with(vm.estado.value) {
            assertFalse(procesando)
            assertEquals("Para terminar la tarea registra al menos una acción con una foto de evidencia.", aviso)
            assertEquals(EstadoTarea.EN_PROCESO, tarea?.estado)
            assertNull(mensaje)
        }
    }

    @Test
    fun error_yReintentar() = runTest(main.despachador) {
        tareas.mias = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.estado.value.error!!.sinConexion)

        conTarea(EstadoTarea.ASIGNADA)
        vm.reintentar()
        advanceUntilIdle()

        assertNull(vm.estado.value.error)
        assertEquals(12, vm.estado.value.tarea?.id)
    }
}
