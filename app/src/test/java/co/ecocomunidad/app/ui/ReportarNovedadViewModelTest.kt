package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.EstadoReporte
import co.ecocomunidad.app.data.modelo.NuevaNovedad
import co.ecocomunidad.app.data.modelo.TipoNovedad
import co.ecocomunidad.app.ui.ReportesFalso.Companion.ANA
import co.ecocomunidad.app.ui.ReportesFalso.Companion.LUIS
import co.ecocomunidad.app.ui.ReportesFalso.Companion.reporte
import co.ecocomunidad.app.ui.ReportesFalso.Companion.tarea
import co.ecocomunidad.app.data.modelo.UsuarioResumen
import co.ecocomunidad.app.ui.novedades.CampoNovedad
import co.ecocomunidad.app.ui.novedades.OpcionGestor
import co.ecocomunidad.app.ui.novedades.ReportarNovedadViewModel
import kotlinx.coroutines.CompletableDeferred
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

class ReportarNovedadViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val reportes = ReportesFalso().apply {
        detalle = Resultado.Exito(reporte(1, EstadoReporte.EN_PROCESO, ciudadano = ANA))
    }
    private val novedades = NovedadesFalso()

    private fun viewModel() = ReportarNovedadViewModel(reportes, novedades, idReporte = 1)

    @Test
    fun cargaElReporteYLosGestoresSinRepetir() = runTest(main.despachador) {
        val carlos = UsuarioResumen(id = 5, nombre = "Carlos", apellido = "Ruiz")
        // El backend las manda más recientes primero; Luis tiene dos tareas
        reportes.tareas = Resultado.Exito(listOf(tarea(13, LUIS), tarea(12, carlos), tarea(10, LUIS), tarea(9, null)))
        val vm = viewModel()

        advanceUntilIdle()

        with(vm.estado.value) {
            assertFalse(cargandoReporte)
            assertEquals("Basura en la esquina", reporte?.titulo)
            assertEquals(listOf(OpcionGestor(13, "Luis Gómez"), OpcionGestor(12, "Carlos Ruiz")), gestores)
            assertNull(gestor) // "Ninguno en particular"
        }
    }

    @Test
    fun siNoCarganLosGestores_igualSePuedeEnviar() = runTest(main.despachador) {
        reportes.tareas = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()
        advanceUntilIdle()

        vm.elegirTipo(TipoNovedad.INCIDENTE)
        vm.cambiarDescripcion("Volvieron a botar escombros.")
        vm.enviar()
        advanceUntilIdle()

        assertTrue(vm.estado.value.errorGestores != null)
        assertTrue(vm.estado.value.enviada)
        assertNull(novedades.creadas.single().tareaId)
    }

    @Test
    fun descripcionVacia_noEnviaYMarcaElCampo() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.elegirTipo(TipoNovedad.QUEJA)
        vm.cambiarDescripcion("   ")
        vm.enviar()
        advanceUntilIdle()

        assertEquals("Cuéntanos qué pasó.", vm.estado.value.errores[CampoNovedad.DESCRIPCION])
        assertTrue(novedades.creadas.isEmpty())
        assertFalse(vm.estado.value.enviando)
    }

    @Test
    fun sinTipo_noEnvia() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.cambiarDescripcion("No recogieron todo.")
        vm.enviar()
        advanceUntilIdle()

        assertEquals("Elige si es una queja o un incidente.", vm.estado.value.errores[CampoNovedad.TIPO])
        assertTrue(novedades.creadas.isEmpty())
        // Al elegir, el error se quita
        vm.elegirTipo(TipoNovedad.QUEJA)
        assertNull(vm.estado.value.errores[CampoNovedad.TIPO])
    }

    @Test
    fun envioCorrecto_mandaLaNovedadYAbreMisNovedades() = runTest(main.despachador) {
        reportes.tareas = Resultado.Exito(listOf(tarea(13, LUIS)))
        novedades.esperaCrear = CompletableDeferred()
        val vm = viewModel()
        advanceUntilIdle()

        vm.elegirTipo(TipoNovedad.QUEJA)
        vm.elegirGestor(vm.estado.value.gestores.single())
        vm.cambiarDescripcion("  El gestor no volvió.  ")
        vm.enviar()
        advanceTimeBy(1_001)

        // Mientras envía: carga, y Brotico piensa pasado 1 s
        assertTrue(vm.estado.value.enviando)
        assertTrue(vm.estado.value.cargaLarga)

        novedades.esperaCrear!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            NuevaNovedad(reporteId = 1, tipo = TipoNovedad.QUEJA, descripcion = "El gestor no volvió.", tareaId = 13),
            novedades.creadas.single(),
        )
        with(vm.estado.value) {
            assertFalse(enviando)
            assertFalse(cargaLarga)
            assertTrue(enviada)
        }
        vm.enviadaAtendida()
        assertFalse(vm.estado.value.enviada)
    }

    @Test
    fun errorDelBackend_seMuestraSinPerderLoEscrito() = runTest(main.despachador) {
        novedades.creada = RepositorioFalso.fallo(
            ErrorAuth.Validacion(
                mapOf("reporte" to listOf("Solo puedes crear novedades cuando el reporte está asignado, en proceso o resuelto; este está pendiente.")),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.elegirTipo(TipoNovedad.QUEJA)
        vm.cambiarDescripcion("No recogieron todo.")
        vm.enviar()
        advanceUntilIdle()

        with(vm.estado.value) {
            assertFalse(enviada)
            assertFalse(enviando)
            assertEquals(
                "Solo puedes crear novedades cuando el reporte está asignado, en proceso o resuelto; este está pendiente.",
                aviso,
            )
            assertEquals("No recogieron todo.", descripcion)
            assertEquals(TipoNovedad.QUEJA, tipo)
        }
    }

    @Test
    fun errorDeUnCampo_vaEnEseCampo() = runTest(main.despachador) {
        novedades.creada = RepositorioFalso.fallo(
            ErrorAuth.Validacion(mapOf("tarea" to listOf("La tarea no pertenece a este reporte."))),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.elegirTipo(TipoNovedad.INCIDENTE)
        vm.cambiarDescripcion("Algo nuevo")
        vm.enviar()
        advanceUntilIdle()

        assertEquals("La tarea no pertenece a este reporte.", vm.estado.value.errores[CampoNovedad.GESTOR])
        assertNull(vm.estado.value.aviso)
    }

    @Test
    fun sinConexionAlEnviar_avisaConMensajeSobrio() = runTest(main.despachador) {
        novedades.creada = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()
        advanceUntilIdle()

        vm.elegirTipo(TipoNovedad.QUEJA)
        vm.cambiarDescripcion("Algo")
        vm.enviar()
        advanceUntilIdle()

        assertEquals(
            "No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.",
            vm.estado.value.aviso,
        )
    }

    @Test
    fun reporteQueNoAbre_muestraErrorYReintenta() = runTest(main.despachador) {
        reportes.detalle = RepositorioFalso.fallo(ErrorAuth.Servidor(404, "No encontrado.", null))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("No encontrado.", vm.estado.value.errorReporte?.mensaje)

        reportes.detalle = Resultado.Exito(reporte(1, EstadoReporte.RESUELTO, ciudadano = ANA))
        vm.reintentar()
        advanceUntilIdle()

        assertNull(vm.estado.value.errorReporte)
        assertEquals(EstadoReporte.RESUELTO, vm.estado.value.reporte?.estado)
    }

    @Test
    fun hayCambios_soloConAlgoEscritoOElegido() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.hayCambios())
        vm.cambiarDescripcion("Hola")
        assertTrue(vm.hayCambios())
    }
}
