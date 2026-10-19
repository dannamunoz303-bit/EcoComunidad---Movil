package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.EstadoTarea
import co.ecocomunidad.app.ui.TareasFalso.Companion.tarea
import co.ecocomunidad.app.ui.reportes.crear.PreparadorFotos
import co.ecocomunidad.app.ui.tareas.RegistrarAccionViewModel
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
import java.io.File
import java.io.IOException

class RegistrarAccionViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val tareas = TareasFalso().apply { mias = Resultado.Exito(listOf(tarea(12, EstadoTarea.EN_PROCESO))) }

    /** "Reduce" devolviendo un archivo con el mismo nombre; falla con el origen "danada". */
    private val preparador = object : PreparadorFotos {
        val descartadas = mutableListOf<File>()

        override suspend fun preparar(origen: String): File {
            if (origen == "danada") throw IOException("no se pudo leer")
            return File("$origen.jpg")
        }

        override fun descartar(archivo: File) {
            descartadas += archivo
        }
    }

    private fun viewModel() = RegistrarAccionViewModel(tareas, preparador, idTarea = 12)

    @Test
    fun cargaLaTareaParaLaTarjeta() = runTest(main.despachador) {
        val vm = viewModel()

        advanceUntilIdle()

        assertFalse(vm.estado.value.cargandoTarea)
        assertEquals(12, vm.estado.value.tarea?.id)
        assertEquals(EstadoTarea.EN_PROCESO, vm.estado.value.tarea?.estado)
    }

    @Test
    fun descripcionVacia_noEnvia() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.agregarFotos(listOf("a"))
        advanceUntilIdle()
        vm.cambiarDescripcion("   ")
        vm.guardar()
        advanceUntilIdle()

        assertEquals("Cuenta qué hiciste.", vm.estado.value.errorDescripcion)
        assertTrue(tareas.operaciones.isEmpty())
        // Al escribir, el error se quita
        vm.cambiarDescripcion("Recogí las bolsas")
        assertNull(vm.estado.value.errorDescripcion)
    }

    @Test
    fun limiteDeFotos_cincoYAvisa() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.agregarFotos(listOf("a", "b", "c"))
        vm.agregarFotos(listOf("d", "e", "f", "g"))
        advanceUntilIdle()

        with(vm.estado.value) {
            assertEquals(listOf("a.jpg", "b.jpg", "c.jpg", "d.jpg", "e.jpg"), fotos.map { it.name })
            assertEquals(0, fotosDisponibles)
            assertEquals("Una acción puede tener máximo 5 fotos de evidencia.", aviso)
        }

        // Al quitar una, vuelve a haber espacio y la copia reducida se borra
        vm.quitarFoto(vm.estado.value.fotos.first())
        assertEquals(1, vm.estado.value.fotosDisponibles)
        assertEquals(listOf("a.jpg"), preparador.descartadas.map { it.name })
    }

    @Test
    fun fotoDanada_avisaYNoSeAgrega() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.agregarFotos(listOf("danada", "b"))
        advanceUntilIdle()

        assertEquals(listOf("b.jpg"), vm.estado.value.fotos.map { it.name })
        assertEquals("No pudimos usar una de las fotos. Prueba con otra.", vm.estado.value.aviso)
    }

    @Test
    fun sinFotos_preguntaAntesDeGuardar() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.cambiarDescripcion("Revisé el lugar")

        vm.guardar()
        advanceUntilIdle()

        // Primero se pregunta "¿Guardar igual?" y no se envía nada
        assertTrue(vm.estado.value.confirmarSinFoto)
        assertTrue(tareas.operaciones.isEmpty())

        vm.sinFotoAtendido()
        vm.guardar(sinFotoConfirmado = true)
        advanceUntilIdle()

        // Sin fotos solo se crea la acción
        assertEquals(listOf("crear acción 12"), tareas.operaciones)
        assertEquals(listOf("Revisé el lugar"), tareas.descripciones)
        assertTrue(vm.estado.value.guardada)
    }

    @Test
    fun guardadoCorrecto_creaLaAccionYSubeLasFotos() = runTest(main.despachador) {
        tareas.esperaCrear = CompletableDeferred()
        val vm = viewModel()
        advanceUntilIdle()
        vm.cambiarDescripcion("  Recogí las bolsas.  ")
        vm.agregarFotos(listOf("a", "b"))
        advanceUntilIdle()

        vm.guardar()
        advanceTimeBy(1_001)

        // Mientras envía: carga, y Brotico piensa pasado 1 s
        assertTrue(vm.estado.value.enviando)
        assertTrue(vm.estado.value.cargaLarga)

        tareas.esperaCrear!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("crear acción 12", "subir a 40"), tareas.operaciones)
        assertEquals(listOf("Recogí las bolsas."), tareas.descripciones)
        assertEquals(listOf(40 to listOf("a.jpg", "b.jpg")), tareas.subidas)
        with(vm.estado.value) {
            assertFalse(enviando)
            assertTrue(guardada)
            assertFalse(confirmarSinFoto)
        }
        // Las copias reducidas ya no hacen falta
        assertEquals(listOf("a.jpg", "b.jpg"), preparador.descartadas.map { it.name })
        vm.guardadaAtendida()
        assertFalse(vm.estado.value.guardada)
    }

    @Test
    fun errorDelBackendAlCrear_noPierdeLoEscrito() = runTest(main.despachador) {
        tareas.accionCreada = RepositorioFalso.fallo(
            ErrorAuth.Validacion(mapOf("detail" to listOf("No se puede registrar acciones en una tarea terminada."))),
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.cambiarDescripcion("Recogí las bolsas.")
        vm.agregarFotos(listOf("a"))
        advanceUntilIdle()

        vm.guardar()
        advanceUntilIdle()

        with(vm.estado.value) {
            assertFalse(guardada)
            assertFalse(enviando)
            assertEquals("No se puede registrar acciones en una tarea terminada.", aviso)
            assertEquals("Recogí las bolsas.", descripcion)
            assertEquals(listOf("a.jpg"), fotos.map { it.name })
            assertNull(accionCreada)
        }
        // No se intentó subir fotos de una acción que no existe
        assertEquals(listOf("crear acción 12"), tareas.operaciones)
    }

    @Test
    fun errorDeDescripcionDelBackend_vaEnElCampo() = runTest(main.despachador) {
        tareas.accionCreada = RepositorioFalso.fallo(
            ErrorAuth.Validacion(mapOf("descripcion" to listOf("La descripción no puede estar vacía."))),
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.cambiarDescripcion("x")

        vm.guardar(sinFotoConfirmado = true)
        advanceUntilIdle()

        assertEquals("La descripción no puede estar vacía.", vm.estado.value.errorDescripcion)
        assertNull(vm.estado.value.aviso)
    }

    @Test
    fun siFallanLasFotos_alReintentarSoloSeSubenSinRepetirLaAccion() = runTest(main.despachador) {
        tareas.evidenciasSubidas = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()
        advanceUntilIdle()
        vm.cambiarDescripcion("Recogí las bolsas.")
        vm.agregarFotos(listOf("a"))
        advanceUntilIdle()

        vm.guardar()
        advanceUntilIdle()

        with(vm.estado.value) {
            assertFalse(guardada)
            assertEquals(40, accionCreada?.id)
            assertTrue(aviso!!.startsWith("La acción quedó guardada, pero no pudimos subir las fotos"))
            assertEquals(listOf("a.jpg"), fotos.map { it.name })
        }

        tareas.evidenciasSubidas = null
        vm.guardar()
        advanceUntilIdle()

        assertEquals(listOf("crear acción 12", "subir a 40", "subir a 40"), tareas.operaciones)
        assertTrue(vm.estado.value.guardada)
    }

    @Test
    fun hayCambios_conTextoOFotos() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.hayCambios())
        vm.agregarFotos(listOf("a"))
        advanceUntilIdle()
        assertTrue(vm.hayCambios())
    }
}
