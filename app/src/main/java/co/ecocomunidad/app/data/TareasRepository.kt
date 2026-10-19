package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.Accion
import co.ecocomunidad.app.data.modelo.TareaGestor

/**
 * Tareas del gestor con sesión (RN-11, RN-13, RN-14). Las reglas las aplica el backend.
 * La implementación real es [TareasRepositoryRemoto].
 */
interface TareasRepository {

    /** Todas las tareas del gestor (también terminadas y canceladas), más nuevas primero. */
    suspend fun mias(): Resultado<List<TareaGestor>>

    /** Una tarea del gestor. Como no hay GET tareas/{id}/, se busca en mias(); si no está, 404. */
    suspend fun tarea(id: Int): Resultado<TareaGestor>

    /** Acciones de la tarea con sus fotos de evidencia (url completas). */
    suspend fun acciones(id: Int): Resultado<List<Accion>>

    suspend fun iniciar(id: Int): Resultado<TareaGestor>

    /** Los 400 (por ejemplo, sin foto de evidencia) llegan con el detail del backend. */
    suspend fun terminar(id: Int): Resultado<TareaGestor>
}
