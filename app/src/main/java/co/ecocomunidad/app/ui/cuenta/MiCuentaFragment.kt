package co.ecocomunidad.app.ui.cuenta

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import co.ecocomunidad.app.databinding.FragmentMiCuentaBinding
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.navegacion.ARG_DESDE_MI_CUENTA
import co.ecocomunidad.app.ui.navegacion.RESULTADO_CONTRASENA_CAMBIADA
import co.ecocomunidad.app.ui.navegacion.Ruta
import co.ecocomunidad.app.ui.navegacion.irA
import com.google.android.material.snackbar.Snackbar

/** Mi cuenta del ciudadano: quién tiene la sesión y las opciones de la cuenta. */
class MiCuentaFragment : Fragment() {

    private var _binding: FragmentMiCuentaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MiCuentaViewModel by viewModels {
        viewModelFactory { initializer { MiCuentaViewModel(contenedor.authRepository) } }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMiCuentaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val nav = findNavController()
        binding.botonVolver.setOnClickListener { nav.popBackStack() }
        binding.opcionNovedades.setOnClickListener { nav.navigate(R.id.misNovedadesFragment) }
        binding.opcionSanciones.setOnClickListener { nav.navigate(R.id.misSancionesFragment) }
        binding.opcionContrasena.setOnClickListener {
            nav.navigate(R.id.cambiarContrasenaFragment, bundleOf(ARG_DESDE_MI_CUENTA to true))
        }
        binding.opcionCerrarSesion.setOnClickListener {
            it.isEnabled = false
            viewModel.cerrarSesion {
                // La vista puede haberse ido mientras se borraba la sesión
                if (view.isAttachedToWindow) nav.irA(Ruta.Login())
            }
        }
        escucharContrasena()
        observar(viewModel.usuario, ::mostrarUsuario)
    }

    /** Al volver de Cambiar contraseña con la contraseña guardada. */
    private fun escucharContrasena() {
        val resultados = findNavController().getBackStackEntry(R.id.miCuentaFragment).savedStateHandle
        observar(resultados.getStateFlow(RESULTADO_CONTRASENA_CAMBIADA, false)) { cambiada ->
            if (!cambiada) return@observar
            resultados[RESULTADO_CONTRASENA_CAMBIADA] = false
            Snackbar.make(binding.raiz, R.string.contrasena_cambiada, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun mostrarUsuario(usuario: UsuarioSesion?) {
        if (usuario == null) return
        binding.mostrarDatos(usuario)
    }

    private fun FragmentMiCuentaBinding.mostrarDatos(usuario: UsuarioSesion) {
        iniciales.text = inicialesDe(usuario.nombre, usuario.apellido)
        nombre.text = getString(R.string.nombre_completo, usuario.nombre, usuario.apellido)
        correo.text = usuario.correo
        rol.setText(
            when (usuario.rol) {
                Rol.CIUDADANO -> R.string.rol_ciudadano
                Rol.GESTOR -> R.string.rol_gestor
                Rol.ADMINISTRADOR -> R.string.rol_administrador
            },
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
