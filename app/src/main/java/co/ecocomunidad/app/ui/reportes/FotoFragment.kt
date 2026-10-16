package co.ecocomunidad.app.ui.reportes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import co.ecocomunidad.app.databinding.FragmentFotoBinding
import co.ecocomunidad.app.ui.navegacion.ARG_URL_FOTO

/** Una foto del reporte o una evidencia en grande. */
class FotoFragment : Fragment() {

    private var _binding: FragmentFotoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.foto.cargarFoto(requireArguments().getString(ARG_URL_FOTO), ImageView.ScaleType.FIT_CENTER)
        binding.botonCerrar.setOnClickListener { findNavController().popBackStack() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
