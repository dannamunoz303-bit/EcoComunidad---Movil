package co.ecocomunidad.app

import android.content.Intent
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
import co.ecocomunidad.app.data.push.EXTRA_REPORTE_ID
import co.ecocomunidad.app.data.push.EXTRA_TIPO
import co.ecocomunidad.app.databinding.ActivityMainBinding
import co.ecocomunidad.app.ui.navegacion.AppViewModel
import co.ecocomunidad.app.ui.navegacion.Ruta
import co.ecocomunidad.app.ui.navegacion.destino
import co.ecocomunidad.app.ui.navegacion.irA
import co.ecocomunidad.app.ui.notificaciones.AvisoPushViewModel
import co.ecocomunidad.app.ui.notificaciones.abrir
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

    private val avisoPush: AvisoPushViewModel by viewModels {
        val contenedor = (application as EcoComunidadApp).contenedor
        viewModelFactory {
            initializer { AvisoPushViewModel(contenedor.tareasRepository, contenedor.authRepository.usuario) }
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

        // Al recrear la Activity (por ejemplo, al girar) el aviso ya se atendió
        if (savedInstanceState == null) atenderAviso(intent)

        lifecycleScope.launch {
            val inicio = appViewModel.inicio.filterNotNull().first()
            ponerGrafo(inicio)

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { appViewModel.cierres.collect { motivo -> navController.irA(Ruta.Login(motivo)) } }
                launch { avisoPush.abrir.collect { destino -> navController.abrir(destino) } }
            }
        }
    }

    /** Con la app ya abierta (launchMode singleTop), tocar un aviso push llega aquí. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        atenderAviso(intent)
    }

    /**
     * Se tocó un aviso push (RN-26): Android o ServicioAvisos dejan tipo y reporte_id en los extras.
     * Abrir la app desde el ícono no trae tipo y no hace nada.
     */
    private fun atenderAviso(intent: Intent) {
        val tipo = intent.getStringExtra(EXTRA_TIPO) ?: return
        avisoPush.avisoTocado(tipo, intent.getStringExtra(EXTRA_REPORTE_ID))
        intent.removeExtra(EXTRA_TIPO)
        intent.removeExtra(EXTRA_REPORTE_ID)
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
