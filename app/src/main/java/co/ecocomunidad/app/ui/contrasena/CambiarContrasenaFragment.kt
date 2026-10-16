package co.ecocomunidad.app.ui.contrasena

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import co.ecocomunidad.app.R
import co.ecocomunidad.app.databinding.FragmentCambiarContrasenaBinding
import co.ecocomunidad.app.ui.componentes.alEscribir
import co.ecocomunidad.app.ui.componentes.cerrarTeclado
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.mostrar
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.navegacion.irA

/** Cambio obligatorio de la contraseña temporal (RN-05). Es la única pantalla de la pila. */
class CambiarContrasenaFragment : Fragment() {

    private var _binding: FragmentCambiarContrasenaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CambiarContrasenaViewModel by viewModels {
        viewModelFactory { initializer { CambiarContrasenaViewModel(contenedor.authRepository) } }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCambiarContrasenaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.campoActual.alEscribir(viewModel::cambiarActual)
        binding.campoNueva.alEscribir(viewModel::cambiarNueva)
        binding.campoConfirmar.alEscribir(viewModel::cambiarConfirmar)
        binding.textoConfirmar.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) guardar()
            accion == EditorInfo.IME_ACTION_DONE
        }
        binding.botonGuardar.setOnClickListener { guardar() }
        binding.botonCerrarSesion.setOnClickListener { viewModel.cerrarSesion() }

        observar(viewModel.estado, ::mostrarEstado)
    }

    private fun guardar() {
        cerrarTeclado()
        viewModel.guardar()
    }

    private fun mostrarEstado(estado: CambiarContrasenaEstado) {
        estado.destino?.let {
            viewModel.destinoAtendido()
            findNavController().irA(it)
            return
        }
        with(binding) {
            // Los mensajes del backend (RN-03) llegan tal cual, uno por línea
            campoActual.mostrar(estado.actual, estado.errorActual)
            campoNueva.mostrar(estado.nueva, estado.errorNueva)
            campoConfirmar.mostrar(estado.confirmar, estado.errorConfirmar)
            aviso.mostrar(estado.aviso)

            val habilitado = !estado.cargando
            campoActual.isEnabled = habilitado
            campoNueva.isEnabled = habilitado
            campoConfirmar.isEnabled = habilitado
            botonGuardar.isEnabled = habilitado
            botonCerrarSesion.isEnabled = habilitado
            botonGuardar.text = if (estado.cargando) "" else getString(R.string.boton_guardar_contrasena)
            progreso.visibility = if (estado.cargando) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
