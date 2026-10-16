package co.ecocomunidad.app.ui.navegacion

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.navOptions
import co.ecocomunidad.app.R

/** Nombre del argumento del login en nav_graph.xml. */
const val ARG_MOTIVO = "motivo"

/** Destino de nav_graph.xml y argumentos para cada [Ruta]. */
fun Ruta.destino(): Pair<Int, Bundle?> = when (this) {
    is Ruta.Login -> R.id.loginFragment to bundleOf(ARG_MOTIVO to motivo)
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
