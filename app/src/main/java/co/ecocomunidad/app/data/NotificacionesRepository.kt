package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.Notificacion

/** Notificaciones del usuario con sesión (sección 9). La implementación real es [NotificacionesRepositoryRemoto]. */
interface NotificacionesRepository {

    suspend fun listar(): Resultado<List<Notificacion>>

    /** Cuántas hay sin leer (para el punto de la campana). */
    suspend fun contarNoLeidas(): Resultado<Int>

    suspend fun marcarLeida(id: Int): Resultado<Notificacion>
}
