package co.ecocomunidad.app.data.remoto

import co.ecocomunidad.app.data.modelo.Accion
import co.ecocomunidad.app.data.modelo.TareaGestor
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Rutas del gestor en TareaViewSet (tareas/views.py). El backend no tiene GET tareas/{id}/:
 * el detalle se arma con mias/, acciones/ y reportes/{id}/. Sin paginación.
 */
interface TareasApi {

    /** Las tareas del gestor con sesión, de todos los estados, más nuevas primero. */
    @GET("tareas/mias/")
    suspend fun mias(): List<TareaGestor>

    /** Acciones con sus fotos de evidencia, más nuevas primero. Solo el gestor dueño (RN-14). */
    @GET("tareas/{id}/acciones/")
    suspend fun acciones(@Path("id") id: Int): List<Accion>

    /** RN-13: asignada → en_proceso. Sin cuerpo. Si no está asignada, 400 con detail. */
    @POST("tareas/{id}/iniciar/")
    suspend fun iniciar(@Path("id") id: Int): TareaGestor

    /** RN-13: en_proceso → terminada. Exige una acción con foto de evidencia; si no, 400 con detail. */
    @POST("tareas/{id}/terminar/")
    suspend fun terminar(@Path("id") id: Int): TareaGestor
}
