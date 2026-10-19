package co.ecocomunidad.app.ui.sanciones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import co.ecocomunidad.app.R
import co.ecocomunidad.app.databinding.FragmentMisSancionesBinding
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.reportes.EstadoListaAdapter
import co.ecocomunidad.app.ui.reportes.EstadoVista
import co.ecocomunidad.app.ui.reportes.mostrar

/** Mis sanciones (RN-19 a RN-21): aviso del bloqueo vigente y las sanciones, sin el administrador. */
class MisSancionesFragment : Fragment() {

    private var _binding: FragmentMisSancionesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MisSancionesViewModel by viewModels {
        viewModelFactory {
            initializer { MisSancionesViewModel(contenedor.sancionesRepository, contenedor.reportesRepository) }
        }
    }

    private val aviso = AvisoBloqueoAdapter()
    private val vacio = EstadoListaAdapter(alReintentar = {})
    private val sanciones = SancionesAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMisSancionesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.botonVolver.setOnClickListener { findNavController().popBackStack() }
        binding.lista.adapter = ConcatAdapter(aviso, vacio, sanciones)
        observar(viewModel.estado, ::mostrarEstado)
    }

    private fun mostrarEstado(estado: MisSancionesEstado) = with(binding) {
        // Cargando y error ocupan la pantalla; el vacío va en la lista, debajo del aviso "sin bloqueos"
        val vistaEstado = when {
            estado.cargando -> EstadoVista.Cargando(estado.cargaLarga, R.string.sanciones_cargando)
            estado.error != null -> EstadoVista.Error(R.string.sanciones_error_titulo, estado.error)
            else -> null
        }
        estadoCarga.root.visibility = if (vistaEstado == null) View.GONE else View.VISIBLE
        vistaEstado?.let { estadoCarga.mostrar(it, viewModel::reintentar) }
        lista.visibility = if (vistaEstado == null) View.VISIBLE else View.GONE

        aviso.bloqueo = if (vistaEstado == null) estado.bloqueo else null
        vacio.estado = if (estado.vacio) EstadoVista.Vacio(R.string.sanciones_vacio) else null
        sanciones.submitList(estado.sanciones)
    }

    override fun onDestroyView() {
        binding.lista.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
