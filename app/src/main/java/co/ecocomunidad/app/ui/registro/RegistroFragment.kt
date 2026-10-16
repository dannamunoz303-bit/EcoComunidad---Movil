package co.ecocomunidad.app.ui.registro

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
import co.ecocomunidad.app.databinding.FragmentRegistroBinding
import co.ecocomunidad.app.ui.componentes.alEscribir
import co.ecocomunidad.app.ui.componentes.cerrarTeclado
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.mostrar
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.navegacion.irA
import com.google.android.material.textfield.TextInputLayout

/** Registro del ciudadano (RN-01). Se apila sobre el login; atrás vuelve a él. */
class RegistroFragment : Fragment() {

    private var _binding: FragmentRegistroBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegistroViewModel by viewModels {
        viewModelFactory { initializer { RegistroViewModel(contenedor.authRepository) } }
    }

    private val campos: Map<CampoRegistro, TextInputLayout>
        get() = with(binding) {
            mapOf(
                CampoRegistro.NOMBRE to campoNombre,
                CampoRegistro.APELLIDO to campoApellido,
                CampoRegistro.CEDULA to campoCedula,
                CampoRegistro.CORREO to campoCorreo,
                CampoRegistro.TELEFONO to campoTelefono,
                CampoRegistro.CONTRASENA to campoContrasena,
                CampoRegistro.CONFIRMAR to campoConfirmar,
            )
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegistroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        campos.forEach { (campo, vista) -> vista.alEscribir { viewModel.cambiar(campo, it) } }
        binding.textoConfirmar.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) registrar()
            accion == EditorInfo.IME_ACTION_DONE
        }
        binding.botonCrear.setOnClickListener { registrar() }
        binding.botonIniciarSesion.setOnClickListener { findNavController().popBackStack() }

        observar(viewModel.estado, ::mostrarEstado)
    }

    private fun registrar() {
        cerrarTeclado()
        viewModel.registrar()
    }

    private fun mostrarEstado(estado: RegistroEstado) {
        estado.destino?.let {
            viewModel.destinoAtendido()
            findNavController().irA(it)
            return
        }
        val habilitado = !estado.cargando
        // Los mensajes del backend (cédula o correo repetidos, RN-03) llegan tal cual, uno por línea
        campos.forEach { (campo, vista) ->
            vista.mostrar(estado.valor(campo), estado.errores[campo])
            vista.isEnabled = habilitado
        }
        with(binding) {
            aviso.mostrar(estado.aviso)
            botonCrear.isEnabled = habilitado
            botonIniciarSesion.isEnabled = habilitado
            botonCrear.text = if (estado.cargando) "" else getString(R.string.boton_crear_cuenta)
            progreso.visibility = if (estado.cargando) View.VISIBLE else View.GONE
            cargaLarga.visibility = if (estado.cargaLarga) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
