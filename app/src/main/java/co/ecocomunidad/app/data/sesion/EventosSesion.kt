package co.ecocomunidad.app.data.sesion

import co.ecocomunidad.app.data.modelo.ErrorBackend
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * La sesión se cerró sin que el usuario lo pidiera (falló la renovación del token).
 * [error] trae el cuerpo del backend si lo hubo, por ejemplo un 403 con code usuario_bloqueado.
 */
data class SesionCerrada(val error: ErrorBackend?)

class EventosSesion {
    // Channel y no SharedFlow: el evento espera aunque la UI todavía no esté escuchando
    private val canal = Channel<SesionCerrada>(Channel.CONFLATED)

    val sesionCerrada: Flow<SesionCerrada> = canal.receiveAsFlow()

    fun emitirSesionCerrada(error: ErrorBackend?) {
        canal.trySend(SesionCerrada(error))
    }
}
