package co.ecocomunidad.app.ui.tareas

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.EstadoTarea

/** Texto legible de cada estado de la tarea (sección 6.2). */
@get:StringRes
val EstadoTarea.etiqueta: Int
    get() = when (this) {
        EstadoTarea.ASIGNADA -> R.string.tarea_estado_asignada
        EstadoTarea.EN_PROCESO -> R.string.tarea_estado_en_proceso
        EstadoTarea.TERMINADA -> R.string.tarea_estado_terminada
        EstadoTarea.CANCELADA -> R.string.tarea_estado_cancelada
    }

/** Mismos tonos que los estados del reporte: de verde musgo (asignada) a verde bosque (terminada). */
@get:ColorRes
val EstadoTarea.color: Int
    get() = when (this) {
        EstadoTarea.ASIGNADA -> R.color.verde_musgo
        EstadoTarea.EN_PROCESO -> R.color.verde_hoja
        EstadoTarea.TERMINADA -> R.color.verde_bosque
        EstadoTarea.CANCELADA -> R.color.tinta_suave
    }
