package co.ecocomunidad.app.ui.notificaciones

import androidx.core.os.bundleOf
import androidx.navigation.NavController
import co.ecocomunidad.app.R
import co.ecocomunidad.app.ui.navegacion.ARG_ID_REPORTE
import co.ecocomunidad.app.ui.navegacion.ARG_ID_TAREA

/** Abre la pantalla de una notificación: al tocarla en la lista o al tocar su aviso push (MainActivity). */
fun NavController.abrir(destino: DestinoNotificacion) {
    when (destino) {
        DestinoNotificacion.MisNovedades -> navigate(R.id.misNovedadesFragment)
        DestinoNotificacion.MisSanciones -> navigate(R.id.misSancionesFragment)
        is DestinoNotificacion.Reporte -> navigate(R.id.detalleReporteFragment, bundleOf(ARG_ID_REPORTE to destino.id))
        // Mis tareas es el inicio del gestor: está debajo en la pila
        DestinoNotificacion.MisTareas -> popBackStack(R.id.inicioGestorFragment, false)
        is DestinoNotificacion.Tarea -> navigate(R.id.detalleTareaFragment, bundleOf(ARG_ID_TAREA to destino.id))
        // Quien la produce ya la cambió por Tarea o MisTareas
        is DestinoNotificacion.TareaDelReporte -> Unit
    }
}
