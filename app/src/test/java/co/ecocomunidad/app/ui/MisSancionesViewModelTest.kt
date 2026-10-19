package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.TipoSancion
import co.ecocomunidad.app.ui.ReportesFalso.Companion.reporte
import co.ecocomunidad.app.ui.SancionesFalso.Companion.sancion
import co.ecocomunidad.app.ui.sanciones.Bloqueo
import co.ecocomunidad.app.ui.sanciones.MisSancionesViewModel
import co.ecocomunidad.app.ui.sanciones.bloqueoVigente
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

class MisSancionesViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val sanciones = SancionesFalso()
    private val reportes = ReportesFalso()

    // "Ahora" fijo: 6 de octubre de 2026 a mediodía en Colombia
    private val ahora = OffsetDateTime.parse("2026-10-06T12:00:00-05:00")

    private fun viewModel() = MisSancionesViewModel(sanciones, reportes) { ahora }

    // --- Aviso de bloqueo (RN-21) ---

    @Test
    fun soloAdvertencias_sinBloqueo() {
        assertEquals(Bloqueo.Ninguno, bloqueoVigente(listOf(sancion(1, TipoSancion.ADVERTENCIA)), ahora))
        assertEquals(Bloqueo.Ninguno, bloqueoVigente(emptyList(), ahora))
    }

    @Test
    fun bloqueoTemporalVigente_muestraHastaCuando() {
        val bloqueo = bloqueoVigente(
            listOf(sancion(1, TipoSancion.BLOQUEO_TEMPORAL, fin = "2026-10-07T09:00:00-05:00")),
            ahora,
        )

        assertEquals(Bloqueo.Temporal(OffsetDateTime.parse("2026-10-07T09:00:00-05:00")), bloqueo)
    }

    @Test
    fun bloqueoTemporalVencido_yaNoCuenta() {
        val bloqueo = bloqueoVigente(
            listOf(sancion(1, TipoSancion.BLOQUEO_TEMPORAL, fin = "2026-10-03T09:00:00-05:00")),
            ahora,
        )

        assertEquals(Bloqueo.Ninguno, bloqueo)
    }

    @Test
    fun variosTemporales_cuentaElQueTerminaMasTarde() {
        val bloqueo = bloqueoVigente(
            listOf(
                sancion(1, TipoSancion.BLOQUEO_TEMPORAL, fin = "2026-10-07T09:00:00-05:00"),
                sancion(2, TipoSancion.BLOQUEO_TEMPORAL, fin = "2026-10-08T09:00:00-05:00"),
            ),
            ahora,
        )

        assertEquals(Bloqueo.Temporal(OffsetDateTime.parse("2026-10-08T09:00:00-05:00")), bloqueo)
    }

    @Test
    fun permanente_prevaleceSobreElTemporal() {
        val bloqueo = bloqueoVigente(
            listOf(
                sancion(1, TipoSancion.BLOQUEO_TEMPORAL, fin = "2026-10-07T09:00:00-05:00"),
                sancion(2, TipoSancion.BLOQUEO_PERMANENTE),
            ),
            ahora,
        )

        assertEquals(Bloqueo.Permanente, bloqueo)
    }

    @Test
    fun permanenteLevantado_yaNoCuenta() {
        // Al levantar un bloqueo el backend pone fecha_fin en la fecha en que se levantó
        val bloqueo = bloqueoVigente(
            listOf(sancion(1, TipoSancion.BLOQUEO_PERMANENTE, fin = "2026-10-04T09:00:00-05:00")),
            ahora,
        )

        assertEquals(Bloqueo.Ninguno, bloqueo)
    }

    // --- Pantalla ---

    @Test
    fun lista_masNuevaPrimeroConElTituloDelReporte() = runTest(main.despachador) {
        sanciones.mias = Resultado.Exito(
            listOf(
                sancion(1, TipoSancion.ADVERTENCIA, inicio = "2026-10-01T09:00:00-05:00", reporte = 1),
                sancion(2, TipoSancion.BLOQUEO_TEMPORAL, inicio = "2026-10-05T09:00:00-05:00",
                    fin = "2026-10-07T09:00:00-05:00", reporte = 44),
            ),
        )
        reportes.mios = Resultado.Exito(listOf(reporte(1)))
        val vm = viewModel()

        advanceUntilIdle()

        with(vm.estado.value) {
            assertEquals(listOf(2, 1), this.sanciones.map { it.sancion.id })
            // El 44 no está en "Mis reportes" (por ejemplo, se eliminó): va sin título
            assertNull(this.sanciones[0].tituloReporte)
            assertEquals("Basura en la esquina", this.sanciones[1].tituloReporte)
            assertTrue(bloqueo is Bloqueo.Temporal)
        }
        assertEquals(listOf(true), reportes.listados)
    }

    @Test
    fun sinReportesLigados_noPideLosReportes() = runTest(main.despachador) {
        sanciones.mias = Resultado.Exito(listOf(sancion(1, TipoSancion.ADVERTENCIA)))
        val vm = viewModel()

        advanceUntilIdle()

        assertTrue(reportes.listados.isEmpty())
        assertEquals(Bloqueo.Ninguno, vm.estado.value.bloqueo)
    }

    @Test
    fun siNoCarganLosReportes_igualMuestraLasSanciones() = runTest(main.despachador) {
        sanciones.mias = Resultado.Exito(listOf(sancion(1, TipoSancion.ADVERTENCIA, reporte = 1)))
        reportes.mios = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()

        advanceUntilIdle()

        assertNull(vm.estado.value.error)
        assertNull(vm.estado.value.sanciones.single().tituloReporte)
    }

    @Test
    fun sinSanciones_estadoVacio() = runTest(main.despachador) {
        val vm = viewModel()

        advanceUntilIdle()

        assertTrue(vm.estado.value.vacio)
        assertEquals(Bloqueo.Ninguno, vm.estado.value.bloqueo)
    }

    @Test
    fun error_yReintentar() = runTest(main.despachador) {
        sanciones.mias = RepositorioFalso.fallo(ErrorAuth.Servidor(500, null, null))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("El servidor no respondió bien. Inténtalo en unos minutos.", vm.estado.value.error?.mensaje)

        sanciones.mias = Resultado.Exito(listOf(sancion(1, TipoSancion.BLOQUEO_PERMANENTE)))
        vm.reintentar()
        advanceUntilIdle()

        assertNull(vm.estado.value.error)
        assertEquals(Bloqueo.Permanente, vm.estado.value.bloqueo)
    }
}
