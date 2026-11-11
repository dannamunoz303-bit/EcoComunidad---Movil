package co.ecocomunidad.app.data.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Canales de Android que crea EcoComunidadApp; el backend manda el mismo id (notificaciones/push.py). */
object CanalesPush {
    /** tarea_asignada: suena como alarma (RN-26). */
    const val TAREAS_ASIGNADAS = "tareas_asignadas"

    /** Los demás tipos: notificación normal. */
    const val AVISOS = "avisos"

    fun paraTipo(tipo: String?): String = if (tipo == TIPO_TAREA_ASIGNADA) TAREAS_ASIGNADAS else AVISOS

    private const val TIPO_TAREA_ASIGNADA = "tarea_asignada"
}

/** Claves de data del aviso push y de los extras con que se abre MainActivity al tocarlo. */
const val EXTRA_TIPO = "tipo"
const val EXTRA_REPORTE_ID = "reporte_id"

/**
 * Llegó un aviso con la app abierta (onMessageReceived solo se llama así). Las pantallas lo usan
 * para actualizar la campana. Sin buffer de reproducción: si nadie escucha, al volver se pide igual.
 */
class EventosPush {
    private val _llegadas = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** El tipo de cada aviso que llega (sección 9). */
    val llegadas: SharedFlow<String> = _llegadas.asSharedFlow()

    fun llego(tipo: String) {
        _llegadas.tryEmit(tipo)
    }
}
