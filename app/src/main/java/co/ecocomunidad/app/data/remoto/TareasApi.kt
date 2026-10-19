package co.ecocomunidad.app.data.remoto

import co.ecocomunidad.app.data.modelo.Accion
import co.ecocomunidad.app.data.modelo.NuevaAccion
import co.ecocomunidad.app.data.modelo.TareaGestor
import okhttp3.MultipartBody
import retrofit2.http.Body
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

    /** RN-14: solo el gestor dueño y con la tarea asignada o en proceso. Responde 201 con la acción, sin fotos. */
    @POST("tareas/{id}/acciones/")
    suspend fun crearAccion(@Path("id") id: Int, @Body nueva: NuevaAccion): Accion

    /**
     * RN-15: fotos tipo evidencia de una acción (AccionViewSet). Multipart con varias partes "fotos":
     * de 1 a 5 por acción (contando las que ya tiene), JPG, PNG o WEBP de máximo 5 MB. Responde 201 con la acción.
     */
    @POST("acciones/{id}/fotos/")
    suspend fun subirEvidencias(@Path("id") id: Int, @Body cuerpo: MultipartBody): Accion
}
