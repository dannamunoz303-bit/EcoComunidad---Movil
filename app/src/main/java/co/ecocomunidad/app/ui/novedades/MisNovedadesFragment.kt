package co.ecocomunidad.app.ui.novedades

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import co.ecocomunidad.app.R
import co.ecocomunidad.app.databinding.FragmentMisNovedadesBinding
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.navegacion.ARG_NOVEDAD_ENVIADA
import co.ecocomunidad.app.ui.reportes.EstadoVista
import co.ecocomunidad.app.ui.reportes.mostrar
import com.google.android.material.snackbar.Snackbar

/** Las novedades del ciudadano. Por ahora se llega al enviar una; en el paso 10, también desde "Mi cuenta". */
class MisNovedadesFragment : Fragment() {

    private var _binding: FragmentMisNovedadesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MisNovedadesViewModel by viewModels {
        viewModelFactory { initializer { MisNovedadesViewModel(contenedor.novedadesRepository) } }
    }

    private val adaptador = NovedadesAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMisNovedadesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.botonVolver.setOnClickListener { findNavController().popBackStack() }
        binding.lista.adapter = adaptador
        // Solo la primera vez: al girar la pantalla no se repite
        if (savedInstanceState == null && arguments?.getBoolean(ARG_NOVEDAD_ENVIADA) == true) {
            Snackbar.make(binding.root, R.string.novedad_enviada, Snackbar.LENGTH_LONG).show()
        }
        observar(viewModel.estado, ::mostrarEstado)
    }

    private fun mostrarEstado(estado: MisNovedadesEstado) = with(binding) {
        val vistaEstado = when {
            estado.cargando -> EstadoVista.Cargando(estado.cargaLarga, R.string.novedades_cargando)
            estado.error != null -> EstadoVista.Error(R.string.novedades_error_titulo, estado.error)
            estado.vacio -> EstadoVista.Vacio(R.string.novedades_vacio)
            else -> null
        }
        estadoCarga.root.visibility = if (vistaEstado == null) View.GONE else View.VISIBLE
        vistaEstado?.let { estadoCarga.mostrar(it, viewModel::reintentar) }
        lista.visibility = if (vistaEstado == null) View.VISIBLE else View.GONE
        adaptador.submitList(estado.novedades)
    }

    override fun onDestroyView() {
        binding.lista.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
