package co.ecocomunidad.app.ui.registro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.AuthRepository
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.RegistroPeticion
import co.ecocomunidad.app.ui.navegacion.Ruta
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Campos del formulario. [clave] es el nombre del campo en RegistroSerializer del backend. */
enum class CampoRegistro(val clave: String) {
    NOMBRE("nombre"),
    APELLIDO("apellido"),
    CEDULA("cedula"),
    CORREO("correo"),
    TELEFONO("telefono"),
    CONTRASENA("password"),

    /** Solo existe en la app: el backend no recibe la confirmación. */
    CONFIRMAR("confirmar"),
}

data class RegistroEstado(
    val valores: Map<CampoRegistro, String> = emptyMap(),
    /** Error debajo de cada campo: vacío o confirmación local, o el mensaje del backend. */
    val errores: Map<CampoRegistro, String> = emptyMap(),
    val cargando: Boolean = false,
    /** La carga pasó de 1 s: se muestra Brotico pensando (marca.md). */
    val cargaLarga: Boolean = false,
    val aviso: String? = null,
    val destino: Ruta? = null,
) {
    fun valor(campo: CampoRegistro): String = valores[campo].orEmpty()
}

/**
 * Registro del ciudadano (RN-01). Aquí solo se revisa que no falten datos y que la confirmación
 * coincida; la cédula, el correo y la contraseña (RN-02, RN-03) los valida el backend.
 */
class RegistroViewModel(private val repositorio: AuthRepository) : ViewModel() {

    private val _estado = MutableStateFlow(RegistroEstado())
    val estado: StateFlow<RegistroEstado> = _estado.asStateFlow()

    fun cambiar(campo: CampoRegistro, valor: String) = _estado.update {
        it.copy(valores = it.valores + (campo to valor), errores = it.errores - campo)
    }

    fun registrar() {
        val e = _estado.value
        if (e.cargando) return

        val errores = erroresLocales(e)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores) }
            return
        }

        val correo = e.valor(CampoRegistro.CORREO).trim()
        val peticion = RegistroPeticion(
            nombre = e.valor(CampoRegistro.NOMBRE).trim(),
            apellido = e.valor(CampoRegistro.APELLIDO).trim(),
            cedula = e.valor(CampoRegistro.CEDULA).trim(),
            correo = correo,
            password = e.valor(CampoRegistro.CONTRASENA),
            telefono = e.valor(CampoRegistro.TELEFONO).trim().ifEmpty { null },
        )

        _estado.update { it.copy(cargando = true, aviso = null, errores = emptyMap()) }
        viewModelScope.launch {
            val avisoLento = launch {
                delay(UN_SEGUNDO)
                _estado.update { it.copy(cargaLarga = true) }
            }
            val r = repositorio.registrar(peticion)
            avisoLento.cancel()

            when (r) {
                // No se inicia sesión: el ciudadano entra desde el login con su correo ya escrito
                is Resultado.Exito -> _estado.update {
                    RegistroEstado(destino = Ruta.Login(correo = correo, cuentaCreada = true))
                }
                is Resultado.Fallo -> _estado.update {
                    it.conError(r.error).copy(cargando = false, cargaLarga = false)
                }
            }
        }
    }

    fun destinoAtendido() = _estado.update { it.copy(destino = null) }

    private fun erroresLocales(e: RegistroEstado): Map<CampoRegistro, String> = buildMap {
        for ((campo, mensaje) in OBLIGATORIOS) {
            if (e.valor(campo).isBlank()) put(campo, mensaje)
        }
        val confirmar = e.valor(CampoRegistro.CONFIRMAR)
        if (confirmar.isNotEmpty() && confirmar != e.valor(CampoRegistro.CONTRASENA)) {
            put(CampoRegistro.CONFIRMAR, "Las contraseñas no coinciden.")
        }
    }

    /** Los mensajes del backend se muestran tal cual debajo de su campo; los demás, en el aviso. */
    private fun RegistroEstado.conError(error: ErrorAuth): RegistroEstado {
        if (error !is ErrorAuth.Validacion) return copy(aviso = mensajeDeError(error))
        val porCampo = mutableMapOf<CampoRegistro, String>()
        val otros = mutableListOf<String>()
        for ((clave, mensajes) in error.campos) {
            val campo = CampoRegistro.entries.firstOrNull { it.clave == clave && it != CampoRegistro.CONFIRMAR }
            if (campo != null) porCampo[campo] = mensajes.joinToString("\n") else otros += mensajes
        }
        val aviso = when {
            otros.isNotEmpty() -> otros.joinToString("\n")
            porCampo.isEmpty() -> mensajeDeError(error) // 400 sin campos reconocibles
            else -> null
        }
        return copy(errores = porCampo, aviso = aviso)
    }

    private companion object {
        const val UN_SEGUNDO = 1_000L

        // El teléfono es opcional en el backend (null=True, blank=True)
        val OBLIGATORIOS = listOf(
            CampoRegistro.NOMBRE to "Escribe tu nombre.",
            CampoRegistro.APELLIDO to "Escribe tu apellido.",
            CampoRegistro.CEDULA to "Escribe tu cédula.",
            CampoRegistro.CORREO to "Escribe tu correo.",
            CampoRegistro.CONTRASENA to "Escribe una contraseña.",
            CampoRegistro.CONFIRMAR to "Repite la contraseña.",
        )
    }
}
