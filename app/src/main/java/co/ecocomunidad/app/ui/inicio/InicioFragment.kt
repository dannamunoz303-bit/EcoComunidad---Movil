package co.ecocomunidad.app.ui.inicio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import co.ecocomunidad.app.R
import co.ecocomunidad.app.databinding.FragmentInicioBinding
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.navegacion.Ruta
import co.ecocomunidad.app.ui.navegacion.irA

/** Inicio temporal: el mismo para los dos roles, solo cambia la etiqueta [rol]. */
abstract class InicioFragment(@param:StringRes private val rol: Int) : Fragment() {

    private var _binding: FragmentInicioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InicioViewModel by viewModels {
        viewModelFactory { initializer { InicioViewModel(contenedor.authRepository) } }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInicioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rol.setText(rol)
        if (resources.getBoolean(R.bool.barra_compacta)) {
            // Solo el ícono, para que el nombre de marca quepa completo en la barra
            binding.botonCerrarSesion.apply {
                text = null
                iconPadding = 0
                contentDescription = getString(R.string.cerrar_sesion)
                tooltipText = getString(R.string.cerrar_sesion)
            }
        }
        binding.botonCerrarSesion.setOnClickListener {
            it.isEnabled = false
            viewModel.cerrarSesion {
                // La vista puede haberse ido mientras se borraba la sesión
                if (view.isAttachedToWindow) findNavController().irA(Ruta.Login())
            }
        }

        observar(viewModel.usuario) { usuario ->
            binding.saludo.text = usuario?.nombre
                ?.let { getString(R.string.saludo_con_nombre, it) }
                ?: getString(R.string.saludo_sin_nombre)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

class InicioCiudadanoFragment : InicioFragment(R.string.rol_ciudadano)

class InicioGestorFragment : InicioFragment(R.string.rol_gestor)
