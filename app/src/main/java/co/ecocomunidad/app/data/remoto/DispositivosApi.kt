package co.ecocomunidad.app.data.remoto

import co.ecocomunidad.app.data.modelo.DispositivoPeticion
import retrofit2.http.Body
import retrofit2.http.POST

/** Celulares que reciben los avisos push (RN-26). Acciones de NotificacionViewSet (notificaciones/views.py). */
interface DispositivosApi {

    /** Responde 204. Si el token ya era de otra persona, pasa a quien tiene la sesión. */
    @POST("notificaciones/dispositivos/")
    suspend fun registrar(@Body peticion: DispositivoPeticion)

    /** Responde 204. Va con el token de la sesión: se llama antes de borrarla. */
    @POST("notificaciones/dispositivos/quitar/")
    suspend fun quitar(@Body peticion: DispositivoPeticion)
}
