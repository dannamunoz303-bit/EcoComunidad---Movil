package co.ecocomunidad.app.ui.registro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import co.ecocomunidad.app.databinding.FragmentRegistroBinding

/** Lugar del registro de ciudadanos (RN-01); se construye en el próximo paso. */
class RegistroFragment : Fragment() {

    private var _binding: FragmentRegistroBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegistroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.botonVolver.setOnClickListener { findNavController().popBackStack() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
