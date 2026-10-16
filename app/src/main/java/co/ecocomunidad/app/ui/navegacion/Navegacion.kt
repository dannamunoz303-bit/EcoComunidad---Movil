package co.ecocomunidad.app.ui.navegacion

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.navOptions
import co.ecocomunidad.app.R

/** Nombres de los argumentos del login en nav_graph.xml. */
const val ARG_MOTIVO = "motivo"
const val ARG_CORREO = "correo"
const val ARG_CUENTA_CREADA = "cuentaCreada"

/** Argumentos del detalle de un reporte y de la foto en grande. */
const val ARG_ID_REPORTE = "idReporte"
const val ARG_URL_FOTO = "url"

/** Lo deja CrearReporteFragment en la pantalla anterior al crear bien un reporte. */
const val RESULTADO_REPORTE_CREADO = "reporteCreado"

/** Lo deja el formulario en el detalle al guardar los cambios de un reporte. */
const val RESULTADO_REPORTE_EDITADO = "reporteEditado"

/** Lo deja el detalle en la lista al eliminar el reporte. */
const val RESULTADO_REPORTE_ELIMINADO = "reporteEliminado"

/** Lo deja el detalle en la lista cuando el reporte cambió y la lista quedó vieja. */
const val RESULTADO_REPORTES_CAMBIADOS = "reportesCambiados"

/** Destino de nav_graph.xml y argumentos para cada [Ruta]. */
fun Ruta.destino(): Pair<Int, Bundle?> = when (this) {
    is Ruta.Login -> R.id.loginFragment to bundleOf(
        ARG_MOTIVO to motivo,
        ARG_CORREO to correo,
        ARG_CUENTA_CREADA to cuentaCreada,
    )
    Ruta.Registro -> R.id.registroFragment to null
    Ruta.CambiarContrasena -> R.id.cambiarContrasenaFragment to null
    Ruta.InicioCiudadano -> R.id.inicioCiudadanoFragment to null
    Ruta.InicioGestor -> R.id.inicioGestorFragment to null
}

/** Navega dejando [ruta] como única pantalla de la pila. */
fun NavController.irA(ruta: Ruta) {
    val (id, argumentos) = ruta.destino()
    navigate(id, argumentos, navOptions { popUpTo(graph.id) { inclusive = true } })
}
