package co.ecocomunidad.app

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import co.ecocomunidad.app.databinding.ActivityMainBinding
import co.ecocomunidad.app.ui.navegacion.AppViewModel
import co.ecocomunidad.app.ui.navegacion.Ruta
import co.ecocomunidad.app.ui.navegacion.destino
import co.ecocomunidad.app.ui.navegacion.irA
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val appViewModel: AppViewModel by viewModels {
        val contenedor = (application as EcoComunidadApp).contenedor
        viewModelFactory {
            initializer { AppViewModel(contenedor.authRepository, contenedor.eventosSesion.sesionCerrada) }
        }
    }

    private val navController: NavController
        get() = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // El contenido no queda debajo de las barras del sistema ni del teclado
        ViewCompat.setOnApplyWindowInsetsListener(binding.raiz) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime())
            vista.setPadding(barras.left, barras.top, barras.right, maxOf(barras.bottom, teclado.bottom))
            insets
        }

        lifecycleScope.launch {
            val inicio = appViewModel.inicio.filterNotNull().first()
            ponerGrafo(inicio)

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appViewModel.cierres.collect { motivo -> navController.irA(Ruta.Login(motivo)) }
            }
        }
    }

    /**
     * Arranca en la pantalla que decidió AppViewModel. Tras recrear la Activity,
     * NavController restaura su propia pila al recibir el grafo.
     */
    private fun ponerGrafo(inicio: Ruta) {
        val (id, argumentos) = inicio.destino()
        val grafo = navController.navInflater.inflate(R.navigation.nav_graph)
        grafo.setStartDestination(id)
        navController.setGraph(grafo, argumentos)
        binding.arranque.visibility = View.GONE
    }
}
