package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.Notificacion
import co.ecocomunidad.app.data.remoto.NotificacionesApi
import kotlinx.serialization.json.Json

class NotificacionesRepositoryRemoto(
    private val api: NotificacionesApi,
    private val json: Json,
) : NotificacionesRepository {

    override suspend fun listar(): Resultado<List<Notificacion>> = llamarApi(json) { api.listar() }

    override suspend fun contarNoLeidas(): Resultado<Int> = llamarApi(json) { api.noLeidas().total }

    override suspend fun marcarLeida(id: Int): Resultado<Notificacion> = llamarApi(json) { api.leer(id) }
}
