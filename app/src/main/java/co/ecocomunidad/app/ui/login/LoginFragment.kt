package co.ecocomunidad.app.ui.login

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import co.ecocomunidad.app.R
import co.ecocomunidad.app.databinding.FragmentLoginBinding
import co.ecocomunidad.app.ui.componentes.FIN_ANIMACION_LOGIN_MS
import co.ecocomunidad.app.ui.componentes.alEscribir
import co.ecocomunidad.app.ui.componentes.avance
import co.ecocomunidad.app.ui.componentes.cerrarTeclado
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.mostrar
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.navegacion.ARG_CORREO
import co.ecocomunidad.app.ui.navegacion.ARG_CUENTA_CREADA
import co.ecocomunidad.app.ui.navegacion.ARG_MOTIVO
import co.ecocomunidad.app.ui.navegacion.irA

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels {
        viewModelFactory {
            initializer {
                LoginViewModel(
                    contenedor.authRepository,
                    motivo = arguments?.getString(ARG_MOTIVO),
                    correo = arguments?.getString(ARG_CORREO),
                    cuentaCreada = arguments?.getBoolean(ARG_CUENTA_CREADA) ?: false,
                )
            }
        }
    }

    private var animador: ValueAnimator? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.campoCorreo.alEscribir(viewModel::cambiarCorreo)
        binding.campoContrasena.alEscribir(viewModel::cambiarContrasena)
        binding.textoContrasena.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) entrar()
            accion == EditorInfo.IME_ACTION_DONE
        }
        binding.botonEntrar.setOnClickListener { entrar() }
        binding.botonRegistrate.setOnClickListener { findNavController().navigate(R.id.registroFragment) }

        observar(viewModel.estado, ::mostrarEstado)
        animarEntrada()
    }

    private fun entrar() {
        cerrarTeclado()
        viewModel.entrar()
    }

    private fun mostrarEstado(estado: LoginEstado) {
        estado.destino?.let {
            viewModel.destinoAtendido()
            findNavController().irA(it)
            return
        }
        with(binding) {
            campoCorreo.mostrar(estado.correo, estado.errorCorreo)
            campoContrasena.mostrar(estado.contrasena, estado.errorContrasena)
            aviso.mostrar(estado.aviso)
            avisoExito.mostrar(if (estado.cuentaCreada) getString(R.string.registro_exito) else null)

            val habilitado = !estado.cargando
            campoCorreo.isEnabled = habilitado
            campoContrasena.isEnabled = habilitado
            botonRegistrate.isEnabled = habilitado
            botonEntrar.isEnabled = habilitado
            // Mientras carga, el botón muestra el indicador en lugar del texto
            botonEntrar.text = if (estado.cargando) "" else getString(R.string.boton_entrar)
            progreso.visibility = if (estado.cargando) View.VISIBLE else View.GONE
            cargaLarga.visibility = if (estado.cargaLarga) View.VISIBLE else View.GONE
        }
    }

    /** Animación del logo (marca.md): una vez por apertura y nunca con las animaciones apagadas. */
    private fun animarEntrada() {
        val sinAnimar = animacionVista || !ValueAnimator.areAnimatorsEnabled()
        animacionVista = true
        if (sinAnimar) {
            aplicarTiempo(FIN_ANIMACION_LOGIN_MS)
            return
        }
        aplicarTiempo(0f)
        animador = ValueAnimator.ofFloat(0f, FIN_ANIMACION_LOGIN_MS).apply {
            duration = FIN_ANIMACION_LOGIN_MS.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener { aplicarTiempo(it.animatedValue as Float) }
            start()
        }
    }

    private fun aplicarTiempo(tiempoMs: Float) {
        val subida = resources.getDimension(R.dimen.subida_aparicion)
        val nombre = avance(tiempoMs, 1_450f, 400f)
        val formulario = avance(tiempoMs, 1_700f, 400f)
        with(binding) {
            logo.tiempoMs = tiempoMs
            this.nombre.root.alpha = nombre
            this.nombre.root.translationY = subida * (1f - nombre)
            this.formulario.alpha = formulario
            this.formulario.translationY = subida * (1f - formulario)
        }
    }

    override fun onDestroyView() {
        animador?.cancel()
        animador = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        // En memoria: la animación se ve una sola vez por apertura de la app
        var animacionVista = false
    }
}
