package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.CambiosReporte
import co.ecocomunidad.app.data.modelo.EstadoReporte
import co.ecocomunidad.app.ui.ReportesFalso.Companion.ANA
import co.ecocomunidad.app.ui.ReportesFalso.Companion.foto
import co.ecocomunidad.app.ui.ReportesFalso.Companion.reporte
import co.ecocomunidad.app.ui.reportes.crear.CampoReporte
import co.ecocomunidad.app.ui.reportes.crear.EditarReporteViewModel
import co.ecocomunidad.app.ui.reportes.crear.FotoFormulario
import co.ecocomunidad.app.ui.reportes.crear.PreparadorFotos
import co.ecocomunidad.app.ui.reportes.crear.Ubicacion
import kotlinx.coroutines.test.TestScope
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

class EditarReporteViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val repositorio = ReportesFalso()

    private val preparador = object : PreparadorFotos {
        override suspend fun preparar(origen: String) = File("$origen.jpg")
        override fun descartar(archivo: File) = Unit
    }

    private fun TestScope.abrir(vararg fotos: Int): EditarReporteViewModel {
        repositorio.detalle = Resultado.Exito(
            reporte(5, EstadoReporte.PENDIENTE, ciudadano = ANA, fotos = fotos.map { foto(it) }),
        )
        return EditarReporteViewModel(repositorio, preparador, id = 5).also { advanceUntilIdle() }
    }

    private fun EditarReporteViewModel.guardadas() =
        estado.value.fotos.filterIsInstance<FotoFormulario.Guardada>()

    @Test
    fun alAbrir_cargaLosDatosActualesYElPinEnSuLugar() = runTest(main.despachador) {
        val vm = abrir(1, 2)

        with(vm.estado.value) {
            assertFalse(cargandoReporte)
            assertEquals(3, categoria?.id)
            assertEquals("Basura en la esquina", texto(CampoReporte.TITULO))
            assertEquals("Hay bolsas desde el lunes.", texto(CampoReporte.DESCRIPCION))
            assertEquals("Calle 10 # 5-20", texto(CampoReporte.DIRECCION))
            assertEquals("Frente al parque", texto(CampoReporte.DESCRIPCION_UBICACION))
            assertEquals(Ubicacion(4.60971, -74.08175), ubicacion)
            assertEquals(ubicacion, ubicacionGuardada)
            assertEquals(listOf(1, 2), fotos.map { (it as FotoFormulario.Guardada).foto.id })
        }
        assertFalse(vm.hayCambios())
    }

    @Test
    fun editarConExito_mandaSoloLoQueCambio() = runTest(main.despachador) {
        val vm = abrir(1)
        vm.cambiar(CampoReporte.TITULO, " Basura en el parque ")
        vm.ponerUbicacion(4.7, -74.1)
        assertTrue(vm.hayCambios())

        vm.enviar()
        advanceUntilIdle()

        assertEquals(listOf("editar"), repositorio.operaciones)
        assertEquals(
            CambiosReporte(titulo = "Basura en el parque", latitud = 4.7, longitud = -74.1),
            repositorio.cambiosEnviados.single(),
        )
        assertTrue(vm.estado.value.guardado)
        assertFalse(vm.estado.value.enviando)
    }

    @Test
    fun sinCambios_noLlamaAlBackend() = runTest(main.despachador) {
        val vm = abrir(1)
        // Escribir lo mismo que ya tenía no es un cambio
        vm.cambiar(CampoReporte.TITULO, "Basura en la esquina ")

        vm.enviar()
        advanceUntilIdle()

        assertTrue(repositorio.operaciones.isEmpty())
        assertTrue(vm.estado.value.guardado)
    }

    @Test
    fun soloFotos_noMandaPatch() = runTest(main.despachador) {
        val vm = abrir(1, 2)
        vm.quitarFoto(vm.guardadas().first())
        vm.agregarFotos(listOf("nueva"))
        advanceUntilIdle()

        vm.enviar()
        advanceUntilIdle()

        assertEquals(listOf("borrar 1", "subir nueva.jpg"), repositorio.operaciones)
        assertTrue(vm.estado.value.guardado)
    }

    @Test
    fun quitarLaUltimaFoto_noSePermite() = runTest(main.despachador) {
        val vm = abrir(1)
        assertFalse(vm.estado.value.puedeQuitarFotos)

        vm.quitarFoto(vm.guardadas().single())

        assertEquals(1, vm.estado.value.fotos.size)
        assertEquals("No se puede borrar la última foto del reporte.", vm.estado.value.aviso)
        assertFalse(vm.hayCambios())
    }

    @Test
    fun cambiarLaUnicaFoto_subePrimeroYBorraDespues() = runTest(main.despachador) {
        val vm = abrir(1)
        vm.agregarFotos(listOf("nueva"))
        advanceUntilIdle()
        // Con la nueva ya se puede quitar la vieja
        vm.quitarFoto(vm.guardadas().single())

        vm.enviar()
        advanceUntilIdle()

        // El backend nunca queda sin fotos (RN-15)
        assertEquals(listOf("subir nueva.jpg", "borrar 1"), repositorio.operaciones)
        assertTrue(vm.estado.value.guardado)
    }

    @Test
    fun cambiarLasCincoFotos_nuncaPasaDelMaximoNiQuedaEnCero() = runTest(main.despachador) {
        val vm = abrir(1, 2, 3, 4, 5)
        vm.guardadas().take(4).forEach(vm::quitarFoto)
        vm.agregarFotos(listOf("n1", "n2", "n3", "n4"))
        advanceUntilIdle()
        vm.quitarFoto(vm.guardadas().single())
        vm.agregarFotos(listOf("n5"))
        advanceUntilIdle()

        vm.enviar()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "borrar 1", "borrar 2", "borrar 3", "borrar 4",
                "subir n1.jpg,n2.jpg,n3.jpg,n4.jpg",
                "borrar 5",
                "subir n5.jpg",
            ),
            repositorio.operaciones,
        )
        assertTrue(vm.estado.value.guardado)
    }

    @Test
    fun erroresPorCampoDelBackend() = runTest(main.despachador) {
        repositorio.editado = RepositorioFalso.fallo(
            ErrorAuth.Validacion(
                mapOf(
                    "categoria" to listOf("La categoría no existe o no está activa."),
                    "longitud" to listOf("La longitud debe estar entre -180 y 180."),
                ),
            ),
        )
        val vm = abrir(1)
        vm.elegirCategoria(4)
        vm.ponerUbicacion(4.6, 200.0)

        vm.enviar()
        advanceUntilIdle()

        with(vm.estado.value) {
            assertEquals("La categoría no existe o no está activa.", errores[CampoReporte.CATEGORIA])
            assertEquals("La longitud debe estar entre -180 y 180.", errores[CampoReporte.UBICACION])
            assertFalse(guardado)
            assertFalse(enviando)
        }
    }

    @Test
    fun yaNoEstaPendiente_muestraElMensajeDelBackend() = runTest(main.despachador) {
        // Así responde el backend: 400 con detail (reportes/views.py, _verificar_dueno_pendiente)
        repositorio.editado = RepositorioFalso.fallo(
            ErrorAuth.Validacion(mapOf("detail" to listOf("Solo se puede modificar el reporte mientras está pendiente."))),
        )
        val vm = abrir(1)
        vm.cambiar(CampoReporte.TITULO, "Nuevo")

        vm.enviar()
        advanceUntilIdle()

        assertEquals("Solo se puede modificar el reporte mientras está pendiente.", vm.estado.value.aviso)
        assertFalse(vm.estado.value.guardado)
    }

    @Test
    fun sinPermiso403_muestraElMensajeDelBackend() = runTest(main.despachador) {
        repositorio.editado = RepositorioFalso.fallo(
            ErrorAuth.Servidor(403, "Solo el ciudadano que creó el reporte puede modificarlo.", null),
        )
        val vm = abrir(1)
        vm.cambiar(CampoReporte.TITULO, "Nuevo")

        vm.enviar()
        advanceUntilIdle()

        assertEquals("Solo el ciudadano que creó el reporte puede modificarlo.", vm.estado.value.aviso)
        assertFalse(vm.estado.value.guardado)
    }

    @Test
    fun sinConexion() = runTest(main.despachador) {
        repositorio.editado = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = abrir(1)
        vm.cambiar(CampoReporte.TITULO, "Nuevo")

        vm.enviar()
        advanceUntilIdle()

        assertEquals(
            "No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.",
            vm.estado.value.aviso,
        )
        assertEquals("Nuevo", vm.estado.value.texto(CampoReporte.TITULO))
    }

    @Test
    fun falloAMitad_alReintentarSoloMandaLoQueFalto() = runTest(main.despachador) {
        val vm = abrir(1)
        vm.cambiar(CampoReporte.TITULO, "Nuevo")
        vm.agregarFotos(listOf("nueva"))
        advanceUntilIdle()
        repositorio.fotosAgregadas = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))

        vm.enviar()
        advanceUntilIdle()
        assertEquals(listOf("editar", "subir nueva.jpg"), repositorio.operaciones)
        assertFalse(vm.estado.value.guardado)
        // La foto que no subió sigue en el formulario
        assertEquals(1, vm.estado.value.fotosNuevas.size)

        repositorio.fotosAgregadas = null
        vm.enviar()
        advanceUntilIdle()
        // El título ya estaba guardado: no se vuelve a mandar
        assertEquals(listOf("editar", "subir nueva.jpg", "subir nueva.jpg"), repositorio.operaciones)
        assertTrue(vm.estado.value.guardado)
    }

    @Test
    fun hayCambios_vuelveAFalsoSiSeDeshace() = runTest(main.despachador) {
        val vm = abrir(1)

        vm.cambiar(CampoReporte.DIRECCION, "Otra calle")
        assertTrue(vm.hayCambios())
        vm.cambiar(CampoReporte.DIRECCION, "Calle 10 # 5-20")
        assertFalse(vm.hayCambios())
    }

    @Test
    fun noSePuedeAbrir_sinConexion() = runTest(main.despachador) {
        repositorio.detalle = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = EditarReporteViewModel(repositorio, preparador, id = 5)
        advanceUntilIdle()

        assertTrue(vm.estado.value.errorReporte!!.sinConexion)
        assertFalse(vm.hayCambios())

        vm.enviar()
        advanceUntilIdle()
        assertTrue(repositorio.operaciones.isEmpty())
        assertNull(vm.estado.value.ubicacion)
    }
}
