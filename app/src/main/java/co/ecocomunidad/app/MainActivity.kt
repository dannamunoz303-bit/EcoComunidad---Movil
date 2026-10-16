package co.ecocomunidad.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.ecocomunidad.app.ui.navegacion.NavegacionApp
import co.ecocomunidad.app.ui.theme.EcoComunidadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val contenedor = (application as EcoComunidadApp).contenedor
        setContent {
            EcoComunidadTheme {
                NavegacionApp(contenedor)
            }
        }
    }
}
