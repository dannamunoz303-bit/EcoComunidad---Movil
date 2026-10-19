package co.ecocomunidad.app.data.remoto

import co.ecocomunidad.app.data.modelo.Notificacion
import co.ecocomunidad.app.data.modelo.TotalNoLeidas
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Rutas de NotificacionViewSet (notificaciones/views.py). Cada usuario ve solo las suyas. Sin paginación. */
interface NotificacionesApi {

    /** Más nuevas primero. */
    @GET("notificaciones/")
    suspend fun listar(): List<Notificacion>

    @GET("notificaciones/no-leidas/")
    suspend fun noLeidas(): TotalNoLeidas

    /** Sin cuerpo. Si ya estaba leída no cambia nada; responde la notificación. */
    @POST("notificaciones/{id}/leer/")
    suspend fun leer(@Path("id") id: Int): Notificacion
}
