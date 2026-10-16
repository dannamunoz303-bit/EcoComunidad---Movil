package co.ecocomunidad.app.ui.navegacion

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import co.ecocomunidad.app.AppContainer
import co.ecocomunidad.app.R
import co.ecocomunidad.app.ui.contrasena.CambiarContrasenaPantalla
import co.ecocomunidad.app.ui.contrasena.CambiarContrasenaViewModel
import co.ecocomunidad.app.ui.inicio.InicioPantalla
import co.ecocomunidad.app.ui.inicio.InicioViewModel
import co.ecocomunidad.app.ui.login.LoginPantalla
import co.ecocomunidad.app.ui.login.LoginViewModel
import co.ecocomunidad.app.ui.registro.RegistroPantalla
import co.ecocomunidad.app.ui.theme.Fondo

@Composable
fun NavegacionApp(contenedor: AppContainer) {
    val repositorio = contenedor.authRepository
    val appViewModel = viewModel {
        AppViewModel(repositorio, contenedor.eventosSesion.sesionCerrada)
    }
    val inicio by appViewModel.inicio.collectAsStateWithLifecycle()

    val rutaInicial = inicio
    if (rutaInicial == null) {
        Arranque()
        return
    }

    val nav = rememberNavController()

    LaunchedEffect(nav) {
        appViewModel.cierres.collect { motivo -> nav.irA(Ruta.Login(motivo)) }
    }

    NavHost(navController = nav, startDestination = rutaInicial) {
        composable<Ruta.Login> { entrada ->
            val motivo = entrada.toRoute<Ruta.Login>().motivo
            LoginPantalla(
                viewModel = viewModel { LoginViewModel(repositorio, motivo) },
                alEntrar = nav::irA,
                alRegistrarse = { nav.navigate(Ruta.Registro) },
            )
        }
        composable<Ruta.Registro> {
            RegistroPantalla(alVolver = { nav.popBackStack() })
        }
        composable<Ruta.CambiarContrasena> {
            CambiarContrasenaPantalla(
                viewModel = viewModel { CambiarContrasenaViewModel(repositorio) },
                alTerminar = nav::irA,
            )
        }
        composable<Ruta.InicioCiudadano> {
            InicioPantalla(
                viewModel = viewModel { InicioViewModel(repositorio) },
                nombreRol = "Ciudadano",
                alCerrarSesion = { nav.irA(Ruta.Login()) },
            )
        }
        composable<Ruta.InicioGestor> {
            InicioPantalla(
                viewModel = viewModel { InicioViewModel(repositorio) },
                nombreRol = "Gestor ambiental",
                alCerrarSesion = { nav.irA(Ruta.Login()) },
            )
        }
    }
}

/** Navega dejando [ruta] como única pantalla de la pila. */
private fun NavController.irA(ruta: Ruta) {
    navigate(ruta) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** Mientras se lee la sesión guardada (unos milisegundos). */
@Composable
private fun Arranque() {
    Box(Modifier.fillMaxSize().background(Fondo), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.logo), contentDescription = "EcoComunidad", modifier = Modifier.size(96.dp))
    }
}
