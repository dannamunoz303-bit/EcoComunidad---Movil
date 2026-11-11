package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.EstadoTarea
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.data.push.CanalesPush
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import co.ecocomunidad.app.ui.RepositorioFalso.Companion.usuarioSesion
import co.ecocomunidad.app.ui.TareasFalso.Companion.tarea
import co.ecocomunidad.app.ui.notificaciones.AvisoPushViewModel
import co.ecocomunidad.app.ui.notificaciones.CampanaViewModel
import co.ecocomunidad.app.ui.notificaciones.DestinoNotificacion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Avisos push (RN-26): a dónde lleva tocarlos y la campana que se actualiza al llegar uno. */
@OptIn(ExperimentalCoroutinesApi::class)
class AvisosPushTest {

    @get:Rule
    val main = ReglaMain()

    private val tareas = TareasFalso()

    /** Destinos que pide abrir el ViewModel tras tocar el aviso con [tipo] y [reporteId] (como llegan en data). */
    private fun TestScope.abrir(sesion: UsuarioSesion?, tipo: String?, reporteId: String?): List<DestinoNotificacion> {
        val vm = AvisoPushViewModel(tareas, flowOf(sesion))
        val abiertos = mutableListOf<DestinoNotificacion>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.abrir.toList(abiertos) }
        vm.avisoTocado(tipo, reporteId)
        advanceUntilIdle()
        return abiertos
    }

    @Test
    fun canalSegunElTipo_comoElBackend() {
        assertEquals("tareas_asignadas", CanalesPush.paraTipo("tarea_asignada"))
        assertEquals("avisos", CanalesPush.paraTipo("tarea_cancelada"))
        assertEquals("avisos", CanalesPush.paraTipo("reporte_estado"))
        assertEquals("avisos", CanalesPush.paraTipo(null))
    }

    @Test
    fun ciudadano_abreElDetalleDelReporte() = runTest(main.despachador) {
        val ciudadano = usuarioSesion(Rol.CIUDADANO)
        assertEquals(listOf(DestinoNotificacion.Reporte(5)), abrir(ciudadano, "reporte_estado", "5"))
        assertEquals(listOf(DestinoNotificacion.MisSanciones), abrir(ciudadano, "sancion", ""))
        assertEquals(listOf(DestinoNotificacion.MisNovedades), abrir(ciudadano, "novedad_respondida", "5"))
    }

    @Test
    fun ciudadano_sinReporteOConReporteEliminado_noAbreNada() = runTest(main.despachador) {
        val ciudadano = usuarioSesion(Rol.CIUDADANO)
        assertTrue(abrir(ciudadano, "reporte_estado", "").isEmpty())
        assertTrue(abrir(ciudadano, "reporte_eliminado", "5").isEmpty())
        assertTrue(abrir(ciudadano, "contrasena_restablecida", "").isEmpty())
    }

    @Test
    fun gestor_tareaAsignada_abreElDetalleDeLaTareaDeEseReporte() = runTest(main.despachador) {
        tareas.mias = Resultado.Exito(
            listOf(tarea(22, EstadoTarea.ASIGNADA, reporte = 6), tarea(21, EstadoTarea.ASIGNADA, reporte = 5)),
        )

        assertEquals(listOf(DestinoNotificacion.Tarea(21)), abrir(usuarioSesion(Rol.GESTOR), "tarea_asignada", "5"))
    }

    @Test
    fun gestor_siNoSePuedenPedirLasTareas_quedaEnMisTareas() = runTest(main.despachador) {
        tareas.mias = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))

        assertEquals(listOf(DestinoNotificacion.MisTareas), abrir(usuarioSesion(Rol.GESTOR), "tarea_asignada", "5"))
    }

    @Test
    fun gestor_tareaCancelada_vaAMisTareas() = runTest(main.despachador) {
        assertEquals(listOf(DestinoNotificacion.MisTareas), abrir(usuarioSesion(Rol.GESTOR), "tarea_cancelada", "5"))
    }

    @Test
    fun sinSesionConContrasenaPendienteOSinTipo_noAbreNada() = runTest(main.despachador) {
        assertTrue(abrir(null, "reporte_estado", "5").isEmpty())
        assertTrue(abrir(usuarioSesion(Rol.GESTOR, debeCambiar = true), "tarea_asignada", "5").isEmpty())
        // Abrir la app desde el ícono: no hay tipo
        assertTrue(abrir(usuarioSesion(Rol.CIUDADANO), null, null).isEmpty())
        assertEquals(0, tareas.listados)
    }

    @Test
    fun campana_seActualizaAlLlegarUnAviso() = runTest(main.despachador) {
        val notificaciones = NotificacionesFalso()
        val llegadas = MutableSharedFlow<String>()
        val vm = CampanaViewModel(notificaciones, llegadas)
        advanceUntilIdle()
        assertFalse(vm.hayNoLeidas.value)

        notificaciones.noLeidas = Resultado.Exito(1)
        llegadas.emit("tarea_asignada")
        advanceUntilIdle()

        assertTrue(vm.hayNoLeidas.value)
    }
}
