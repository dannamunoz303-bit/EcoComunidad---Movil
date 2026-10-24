package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.EstadoTarea
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.ui.NotificacionesFalso.Companion.notificacion
import co.ecocomunidad.app.ui.RepositorioFalso.Companion.usuarioSesion
import co.ecocomunidad.app.ui.TareasFalso.Companion.tarea
import co.ecocomunidad.app.ui.notificaciones.CampanaViewModel
import co.ecocomunidad.app.ui.notificaciones.DestinoNotificacion
import co.ecocomunidad.app.ui.notificaciones.NotificacionesViewModel
import co.ecocomunidad.app.ui.notificaciones.destinoDe
import co.ecocomunidad.app.ui.notificaciones.tareaDelReporte
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
    private val tareas = TareasFalso()
    private val sesion = RepositorioFalso()

    private fun viewModel(rol: Rol = Rol.CIUDADANO): NotificacionesViewModel {
        sesion.usuario.value = usuarioSesion(rol)
        return NotificacionesViewModel(repositorio, tareas, sesion.usuario)
    }

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
    fun destinoDeCadaTipo_ciudadano() {
        fun destino(id: Int, tipo: String, reporte: Int? = null) = destinoDe(notificacion(id, tipo, reporte), Rol.CIUDADANO)

        assertEquals(DestinoNotificacion.MisNovedades, destino(1, "novedad_respondida", reporte = 5))
        assertEquals(DestinoNotificacion.MisSanciones, destino(2, "sancion", reporte = 5))
        assertEquals(DestinoNotificacion.MisSanciones, destino(3, "bloqueo_levantado"))
        for (tipo in listOf("reporte_aprobado", "reporte_rechazado", "reporte_asignado", "reporte_estado")) {
            assertEquals(tipo, DestinoNotificacion.Reporte(5), destino(4, tipo, reporte = 5))
        }
        // Un reporte eliminado ya no se puede abrir
        assertNull(destino(5, "reporte_eliminado", reporte = 5))
        // Sin reporte y sin pantalla propia: solo se marca como leída
        assertNull(destino(6, "contrasena_restablecida"))
        assertNull(destino(7, "reporte_estado"))
    }

    @Test
    fun destinoDeCadaTipo_gestor() {
        fun destino(id: Int, tipo: String, reporte: Int? = null) = destinoDe(notificacion(id, tipo, reporte), Rol.GESTOR)

        assertEquals(DestinoNotificacion.TareaDelReporte(5), destino(1, "tarea_asignada", reporte = 5))
        assertEquals(DestinoNotificacion.MisTareas, destino(2, "tarea_cancelada", reporte = 5))
        assertEquals(DestinoNotificacion.MisNovedades, destino(3, "novedad_respondida", reporte = 5))
        // Cualquier otro tipo: solo se marca como leída, aunque traiga reporte
        assertNull(destino(4, "contrasena_restablecida"))
        assertNull(destino(5, "reporte_estado", reporte = 5))
        assertNull(destino(6, "sancion"))
    }

    @Test
    fun tareaDelReporte_laActivaYSiNoLaMasReciente() {
        // Más nuevas primero, como tareas/mias/
        val lista = listOf(
            tarea(30, EstadoTarea.ASIGNADA, reporte = 6),
            tarea(21, EstadoTarea.EN_PROCESO, reporte = 5),
            tarea(20, EstadoTarea.TERMINADA, reporte = 5),
            tarea(11, EstadoTarea.CANCELADA, reporte = 7),
            tarea(10, EstadoTarea.TERMINADA, reporte = 7),
        )
        assertEquals(21, tareaDelReporte(lista, 5)?.id)
        assertEquals(11, tareaDelReporte(lista, 7)?.id)
        assertNull(tareaDelReporte(lista, 8))
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
        val vm = viewModel()

        advanceUntilIdle()

        assertEquals(listOf(3, 2, 1), vm.estado.value.notificaciones.map { it.id })
        assertFalse(vm.estado.value.vacio)
    }

    @Test
    fun sinNotificaciones_estadoVacio() = runTest(main.despachador) {
        val vm = viewModel()

        advanceUntilIdle()

        assertTrue(vm.estado.value.vacio)
    }

    @Test
    fun error_yReintentar() = runTest(main.despachador) {
        repositorio.lista = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()
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
        val vm = viewModel()
        advanceUntilIdle()

        vm.tocar(novedad)

        // Se ve leída al instante, sin esperar al backend
        assertTrue(vm.estado.value.notificaciones.single().leida)
        advanceUntilIdle()
        assertEquals(DestinoNotificacion.MisNovedades, vm.estado.value.abrir)
        assertEquals(listOf(1), repositorio.leidas)

        vm.abrirAtendido()
        assertNull(vm.estado.value.abrir)
    }

    @Test
    fun tocarUnaYaLeida_noLlamaAlBackend() = runTest(main.despachador) {
        val reporte = notificacion(1, "reporte_estado", reporte = 9, leida = true)
        repositorio.lista = Resultado.Exito(listOf(reporte))
        val vm = viewModel()
        advanceUntilIdle()

        vm.tocar(reporte)
        advanceUntilIdle()

        assertTrue(repositorio.leidas.isEmpty())
        assertEquals(DestinoNotificacion.Reporte(9), vm.estado.value.abrir)
    }

    // --- Gestor ---

    @Test
    fun gestor_tareaAsignada_abreSuTareaEnEseReporte() = runTest(main.despachador) {
        val asignada = notificacion(1, "tarea_asignada", reporte = 5)
        repositorio.lista = Resultado.Exito(listOf(asignada))
        tareas.mias = Resultado.Exito(
            listOf(tarea(22, EstadoTarea.ASIGNADA, reporte = 6), tarea(21, EstadoTarea.ASIGNADA, reporte = 5)),
        )
        val vm = viewModel(Rol.GESTOR)
        advanceUntilIdle()

        vm.tocar(asignada)
        advanceUntilIdle()

        assertEquals(DestinoNotificacion.Tarea(21), vm.estado.value.abrir)
        assertEquals(listOf(1), repositorio.leidas)
    }

    @Test
    fun gestor_tareaAsignadaQueYaNoEsta_abreMisTareas() = runTest(main.despachador) {
        val asignada = notificacion(1, "tarea_asignada", reporte = 5)
        repositorio.lista = Resultado.Exito(listOf(asignada))
        val vm = viewModel(Rol.GESTOR)
        advanceUntilIdle()

        vm.tocar(asignada)
        advanceUntilIdle()

        assertEquals(DestinoNotificacion.MisTareas, vm.estado.value.abrir)
    }

    @Test
    fun gestor_siNoSePuedenPedirLasTareas_avisaYNoAbre() = runTest(main.despachador) {
        val asignada = notificacion(1, "tarea_asignada", reporte = 5)
        repositorio.lista = Resultado.Exito(listOf(asignada))
        tareas.mias = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel(Rol.GESTOR)
        advanceUntilIdle()

        vm.tocar(asignada)
        advanceUntilIdle()

        assertNull(vm.estado.value.abrir)
        assertEquals(
            "No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.",
            vm.estado.value.aviso,
        )
        // Igual queda leída
        assertTrue(vm.estado.value.notificaciones.single().leida)
    }

    @Test
    fun gestor_otroTipo_soloSeMarcaComoLeida() = runTest(main.despachador) {
        val otra = notificacion(1, "contrasena_restablecida")
        repositorio.lista = Resultado.Exito(listOf(otra))
        val vm = viewModel(Rol.GESTOR)
        advanceUntilIdle()

        vm.tocar(otra)
        advanceUntilIdle()

        assertNull(vm.estado.value.abrir)
        assertEquals(listOf(1), repositorio.leidas)
        assertEquals(0, tareas.listados)
    }

    @Test
    fun siNoSePuedeMarcar_vuelveANoLeidaYAvisa() = runTest(main.despachador) {
        val sancion = notificacion(1, "sancion")
        repositorio.lista = Resultado.Exito(listOf(sancion))
        repositorio.resultadoLeer = RepositorioFalso.fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModel()
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
