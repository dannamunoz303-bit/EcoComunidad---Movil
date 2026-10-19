package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.ui.NotificacionesFalso.Companion.notificacion
import co.ecocomunidad.app.ui.notificaciones.CampanaViewModel
import co.ecocomunidad.app.ui.notificaciones.DestinoNotificacion
import co.ecocomunidad.app.ui.notificaciones.NotificacionesViewModel
import co.ecocomunidad.app.ui.notificaciones.destinoDe
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class NotificacionesViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val repositorio = NotificacionesFalso()

    // --- Punto rojo de la campana ---

    @Test
    fun campana_conNoLeidasMuestraElPunto() = runTest(main.despachador) {
        repositorio.noLeidas = Resultado.Exito(3)
        val vm = CampanaViewModel(repositorio)

        vm.actualizar()
        advanceUntilIdle()

        assertTrue(vm.hayNoLeidas.value)
    }

    @Test
    fun campana_sinNoLeidasNoMuestraElPunto() = runTest(main.despachador) {
        val vm = CampanaViewModel(repositorio)
        repositorio.noLeidas = Resultado.Exito(2)
        vm.actualizar()
        advanceUntilIdle()

        // Al volver al inicio ya se leyeron todas
        repositorio.noLeidas = Resultado.Exito(0)
        vm.actualizar()
        advanceUntilIdle()

        assertFalse(vm.hayNoLeidas.value)
    }

    @Test
    fun campana_siFallaDejaLoUltimoQueSeSupo() = runTest(main.despachador) {
        val vm = CampanaViewModel(repositorio)
        repositorio.noLeidas = Resultado.Exito(1)
        vm.actualizar()
        advanceUntilIdle()

        repositorio.noLeidas = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        vm.actualizar()
        advanceUntilIdle()

        assertTrue(vm.hayNoLeidas.value)
    }

    // --- A dónde lleva cada tipo ---

    @Test
    fun destinoDeCadaTipo() {
        assertEquals(DestinoNotificacion.MisNovedades, destinoDe(notificacion(1, "novedad_respondida", reporte = 5)))
        assertEquals(DestinoNotificacion.MisSanciones, destinoDe(notificacion(2, "sancion", reporte = 5)))
        assertEquals(DestinoNotificacion.MisSanciones, destinoDe(notificacion(3, "bloqueo_levantado")))
        for (tipo in listOf("reporte_aprobado", "reporte_rechazado", "reporte_asignado", "reporte_estado")) {
            assertEquals(tipo, DestinoNotificacion.Reporte(5), destinoDe(notificacion(4, tipo, reporte = 5)))
        }
        // Un reporte eliminado ya no se puede abrir
        assertNull(destinoDe(notificacion(5, "reporte_eliminado", reporte = 5)))
        // Sin reporte y sin pantalla propia: solo se marca como leída
        assertNull(destinoDe(notificacion(6, "contrasena_restablecida")))
        assertNull(destinoDe(notificacion(7, "reporte_estado")))
    }

    // --- Lista ---

    @Test
    fun lista_masNuevaPrimero() = runTest(main.despachador) {
        repositorio.lista = Resultado.Exito(
            listOf(
                notificacion(1, "reporte_aprobado", fecha = "2026-10-01T09:00:00-05:00"),
                notificacion(3, "sancion", fecha = "2026-10-07T09:00:00-05:00"),
                notificacion(2, "reporte_estado", fecha = "2026-10-03T09:00:00-05:00"),
            ),
        )
        val vm = NotificacionesViewModel(repositorio)

        advanceUntilIdle()

        assertEquals(listOf(3, 2, 1), vm.estado.value.notificaciones.map { it.id })
        assertFalse(vm.estado.value.vacio)
    }

    @Test
    fun sinNotificaciones_estadoVacio() = runTest(main.despachador) {
        val vm = NotificacionesViewModel(repositorio)

        advanceUntilIdle()

        assertTrue(vm.estado.value.vacio)
    }

    @Test
    fun error_yReintentar() = runTest(main.despachador) {
        repositorio.lista = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = NotificacionesViewModel(repositorio)
        advanceUntilIdle()

        assertTrue(vm.estado.value.error!!.sinConexion)

        repositorio.lista = Resultado.Exito(listOf(notificacion(1, "sancion")))
        vm.reintentar()
        advanceUntilIdle()

        assertNull(vm.estado.value.error)
        assertEquals(1, vm.estado.value.notificaciones.size)
    }

    // --- Tocar una ---

    @Test
    fun tocarUnaSinLeer_laMarcaYAbreSuPantalla() = runTest(main.despachador) {
        val novedad = notificacion(1, "novedad_respondida", reporte = 5)
        repositorio.lista = Resultado.Exito(listOf(novedad))
        val vm = NotificacionesViewModel(repositorio)
        advanceUntilIdle()

        vm.tocar(novedad)

        // Se ve leída al instante, sin esperar al backend
        assertTrue(vm.estado.value.notificaciones.single().leida)
        assertEquals(DestinoNotificacion.MisNovedades, vm.estado.value.abrir)
        advanceUntilIdle()
        assertEquals(listOf(1), repositorio.leidas)

        vm.abrirAtendido()
        assertNull(vm.estado.value.abrir)
    }

    @Test
    fun tocarUnaYaLeida_noLlamaAlBackend() = runTest(main.despachador) {
        val reporte = notificacion(1, "reporte_estado", reporte = 9, leida = true)
        repositorio.lista = Resultado.Exito(listOf(reporte))
        val vm = NotificacionesViewModel(repositorio)
        advanceUntilIdle()

        vm.tocar(reporte)
        advanceUntilIdle()

        assertTrue(repositorio.leidas.isEmpty())
        assertEquals(DestinoNotificacion.Reporte(9), vm.estado.value.abrir)
    }

    @Test
    fun siNoSePuedeMarcar_vuelveANoLeidaYAvisa() = runTest(main.despachador) {
        val sancion = notificacion(1, "sancion")
        repositorio.lista = Resultado.Exito(listOf(sancion))
        repositorio.resultadoLeer = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = NotificacionesViewModel(repositorio)
        advanceUntilIdle()

        vm.tocar(sancion)
        advanceUntilIdle()

        assertFalse(vm.estado.value.notificaciones.single().leida)
        assertEquals(
            "No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.",
            vm.estado.value.aviso,
        )
    }
}
