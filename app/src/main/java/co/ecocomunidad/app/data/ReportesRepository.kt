package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.data.modelo.Tarea

/**
 * Consulta de reportes. La visibilidad (RN-10) la decide el backend: aquí no se filtra nada.
 * La implementación real es [ReportesRepositoryRemoto].
 */
interface ReportesRepository {

    /** Todos los reportes que el usuario puede ver, o solo los propios si [soloMios]. Sin paginación. */
    suspend fun listar(soloMios: Boolean): Resultado<List<Reporte>>

    suspend fun detalle(id: Int): Resultado<Reporte>

    /** Tareas, acciones y evidencias del reporte. Para quien no es el dueño, el backend responde 403 (RN-14). */
    suspend fun tareas(id: Int): Resultado<List<Tarea>>
}
