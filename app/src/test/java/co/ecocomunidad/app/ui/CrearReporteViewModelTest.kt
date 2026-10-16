package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.ui.reportes.crear.CampoReporte
import co.ecocomunidad.app.ui.reportes.crear.CrearReporteViewModel
import co.ecocomunidad.app.ui.reportes.crear.FotoFormulario
import co.ecocomunidad.app.ui.reportes.crear.MAXIMO_FOTOS
import co.ecocomunidad.app.ui.reportes.crear.PreparadorFotos
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
import java.io.File
import java.io.IOException

class CrearReporteViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val repositorio = ReportesFalso()

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

    private fun viewModel() = CrearReporteViewModel(repositorio, preparador)

    /** Formulario completo y válido. */
    private fun CrearReporteViewModel.llenar() {
        elegirCategoria(3)
        cambiar(CampoReporte.TITULO, " Basura en el parque ")
        cambiar(CampoReporte.DESCRIPCION, "Bolsas acumuladas")
        cambiar(CampoReporte.DIRECCION, " Calle 10 # 5-20 ")
        agregarFotos(listOf("foto-a"))
        ponerUbicacion(4.60971, -74.08175)
    }

    @Test
    fun hayCambios_conCualquierCosaEscritaOElegida() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.hayCambios())

        vm.cambiar(CampoReporte.TITULO, "   ")
        assertFalse(vm.hayCambios())
        vm.ponerUbicacion(4.6, -74.0)
        assertTrue(vm.hayCambios())
    }

    @Test
    fun alAbrir_cargaLasCategoriasActivasDelBackend() = runTest(main.despachador) {
        val vm = viewModel()
        assertTrue(vm.estado.value.cargandoCategorias)

        advanceUntilIdle()

        assertFalse(vm.estado.value.cargandoCategorias)
        assertEquals(listOf(3, 4), vm.estado.value.categorias.map { it.id })
    }

    @Test
    fun faltaCategoria() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.cambiar(CampoReporte.TITULO, "Basura")
        vm.cambiar(CampoReporte.DESCRIPCION, "Bolsas")
        vm.cambiar(CampoReporte.DIRECCION, "Calle 10")
        vm.agregarFotos(listOf("foto-a"))
        vm.ponerUbicacion(4.6, -74.0)
        // Una categoría que no vino del backend no se puede elegir
        vm.elegirCategoria(99)
        advanceUntilIdle()
        assertNull(vm.estado.value.categoria)

        vm.enviar()
        advanceUntilIdle()

        assertEquals(setOf(CampoReporte.CATEGORIA), vm.estado.value.errores.keys)
        assertEquals("Elige una categoría.", vm.estado.value.errores[CampoReporte.CATEGORIA])
        assertTrue(repositorio.creados.isEmpty())
    }

    @Test
    fun faltaDireccion() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.llenar()
        vm.cambiar(CampoReporte.DIRECCION, "   ")
        advanceUntilIdle()

        vm.enviar()
        advanceUntilIdle()

        assertEquals("Escribe la dirección.", vm.estado.value.errores[CampoReporte.DIRECCION])
        assertEquals(setOf(CampoReporte.DIRECCION), vm.estado.value.errores.keys)
        assertTrue(repositorio.creados.isEmpty())
    }

    @Test
    fun sinFotos() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.llenar()
        advanceUntilIdle()
        vm.quitarFoto(FotoFormulario.Nueva(File("foto-a.jpg")))

        vm.enviar()
        advanceUntilIdle()

        assertEquals("Agrega al menos una foto.", vm.estado.value.errores[CampoReporte.FOTOS])
        assertEquals(listOf(File("foto-a.jpg")), preparador.descartadas)
        assertTrue(repositorio.creados.isEmpty())
    }

    @Test
    fun sinUbicacion() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.elegirCategoria(3)
        vm.cambiar(CampoReporte.TITULO, "Basura")
        vm.cambiar(CampoReporte.DESCRIPCION, "Bolsas")
        vm.cambiar(CampoReporte.DIRECCION, "Calle 10")
        vm.agregarFotos(listOf("foto-a"))
        advanceUntilIdle()

        vm.enviar()
        advanceUntilIdle()

        assertEquals(
            "Marca el lugar en el mapa o usa tu ubicación.",
            vm.estado.value.errores[CampoReporte.UBICACION],
        )
        assertTrue(repositorio.creados.isEmpty())

        vm.ponerUbicacion(4.6, -74.0)
        assertNull(vm.estado.value.errores[CampoReporte.UBICACION])
    }

    @Test
    fun formularioVacio_marcaTodoLoQueExigeElBackend() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.enviar()
        advanceUntilIdle()

        assertEquals(
            setOf(
                CampoReporte.CATEGORIA, CampoReporte.TITULO, CampoReporte.DESCRIPCION,
                CampoReporte.DIRECCION, CampoReporte.FOTOS, CampoReporte.UBICACION,
            ),
            vm.estado.value.errores.keys,
        )
        // La descripción de la ubicación es opcional en el backend
        assertNull(vm.estado.value.errores[CampoReporte.DESCRIPCION_UBICACION])
    }

    @Test
    fun exito_enviaTodoEnUnaSolaPeticion() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.llenar()
        vm.agregarFotos(listOf("foto-b"))
        advanceUntilIdle()
        val espera = CompletableDeferred<Unit>()
        repositorio.esperaCrear = espera

        vm.enviar()
        vm.enviar()
        runCurrent()
        assertTrue(vm.estado.value.enviando)
        advanceTimeBy(1_001)
        assertTrue(vm.estado.value.cargaLarga)

        espera.complete(Unit)
        advanceUntilIdle()

        val nuevo = repositorio.creados.single()
        assertEquals(3, nuevo.categoria)
        assertEquals("Basura en el parque", nuevo.titulo)
        assertEquals("Bolsas acumuladas", nuevo.descripcion)
        assertEquals("Calle 10 # 5-20", nuevo.direccion)
        assertNull(nuevo.descripcionUbicacion)
        assertEquals(4.60971, nuevo.latitud, 0.0)
        assertEquals(-74.08175, nuevo.longitud, 0.0)
        assertEquals(listOf("foto-a.jpg", "foto-b.jpg"), nuevo.fotos.map { it.name })
        with(vm.estado.value) {
            assertTrue(creado)
            assertFalse(enviando)
            assertFalse(cargaLarga)
        }
        vm.creadoAtendido()
        assertFalse(vm.estado.value.creado)
    }

    @Test
    fun erroresPorCampoDelBackend_noSeCreaNada() = runTest(main.despachador) {
        repositorio.creado = RepositorioFalso.fallo(
            ErrorAuth.Validacion(
                mapOf(
                    "categoria" to listOf("La categoría no existe o no está activa."),
                    "latitud" to listOf("La latitud debe estar entre -90 y 90."),
                    "longitud" to listOf("La longitud debe estar entre -180 y 180."),
                    "fotos" to listOf("Cada foto debe pesar máximo 5 MB."),
                ),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.llenar()
        advanceUntilIdle()

        vm.enviar()
        advanceUntilIdle()

        with(vm.estado.value) {
            assertEquals("La categoría no existe o no está activa.", errores[CampoReporte.CATEGORIA])
            assertEquals(
                "La latitud debe estar entre -90 y 90.\nLa longitud debe estar entre -180 y 180.",
                errores[CampoReporte.UBICACION],
            )
            assertEquals("Cada foto debe pesar máximo 5 MB.", errores[CampoReporte.FOTOS])
            assertNull(aviso)
            assertFalse(creado)
            assertFalse(enviando)
        }
    }

    @Test
    fun errorDelBackendSinCampo_vaAlAviso() = runTest(main.despachador) {
        repositorio.creado = RepositorioFalso.fallo(
            ErrorAuth.Validacion(mapOf("detail" to listOf("Solo los ciudadanos pueden crear reportes."))),
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.llenar()
        advanceUntilIdle()

        vm.enviar()
        advanceUntilIdle()

        assertEquals("Solo los ciudadanos pueden crear reportes.", vm.estado.value.aviso)
    }

    @Test
    fun sinConexion() = runTest(main.despachador) {
        repositorio.creado = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()
        advanceUntilIdle()
        vm.llenar()
        advanceUntilIdle()

        vm.enviar()
        advanceUntilIdle()

        assertEquals(
            "No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.",
            vm.estado.value.aviso,
        )
        assertFalse(vm.estado.value.creado)
        // Lo escrito sigue ahí para reintentar
        assertEquals(1, vm.estado.value.fotos.size)
        assertEquals(" Basura en el parque ", vm.estado.value.texto(CampoReporte.TITULO))
    }

    @Test
    fun categoriasSinConexion_yReintentar() = runTest(main.despachador) {
        repositorio.categorias = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(
            "No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.",
            vm.estado.value.errorCategorias,
        )

        repositorio.categorias = Resultado.Exito(listOf(co.ecocomunidad.app.data.modelo.Categoria(3, "Basura")))
        vm.reintentarCategorias()
        advanceUntilIdle()
        assertNull(vm.estado.value.errorCategorias)
        assertEquals(1, vm.estado.value.categorias.size)
    }

    @Test
    fun fotos_noPasanDelMaximoDelBackendYAvisanSiUnaFalla() = runTest(main.despachador) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.agregarFotos((1..7).map { "foto-$it" })
        advanceUntilIdle()
        assertEquals(MAXIMO_FOTOS, vm.estado.value.fotos.size)
        assertEquals(0, vm.estado.value.fotosDisponibles)
        // El mismo texto que usa el backend para este límite
        assertEquals("Un reporte puede tener máximo 5 fotos.", vm.estado.value.aviso)

        vm.quitarFoto(vm.estado.value.fotos.first())
        vm.agregarFotos(listOf("danada"))
        advanceUntilIdle()
        assertEquals(MAXIMO_FOTOS - 1, vm.estado.value.fotos.size)
        assertEquals("No pudimos usar una de las fotos. Prueba con otra.", vm.estado.value.aviso)
    }
}
