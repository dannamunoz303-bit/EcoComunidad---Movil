package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.Accion
import co.ecocomunidad.app.data.modelo.TareaGestor
import co.ecocomunidad.app.data.remoto.TareasApi
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** [urlApi]: la misma de ApiCliente, para completar las url de fotos relativas (como en ReportesRepositoryRemoto). */
class TareasRepositoryRemoto(
    private val api: TareasApi,
    private val json: Json,
    urlApi: String,
) : TareasRepository {

    private val raiz: HttpUrl = urlApi.toHttpUrl().resolve("/")!!

    override suspend fun mias(): Resultado<List<TareaGestor>> = llamarApi(json) { api.mias() }

    override suspend fun tarea(id: Int): Resultado<TareaGestor> =
        when (val r = mias()) {
            is Resultado.Exito -> r.datos.firstOrNull { it.id == id }?.let { Resultado.Exito(it) }
                ?: Resultado.Fallo(ErrorAuth.Servidor(404, "No encontramos esta tarea entre las tuyas.", null))
            is Resultado.Fallo -> r
        }

    override suspend fun acciones(id: Int): Resultado<List<Accion>> =
        llamarApi(json) {
            api.acciones(id).map { accion ->
                accion.copy(fotos = accion.fotos.map { it.copy(url = raiz.resolve(it.url)?.toString() ?: it.url) })
            }
        }

    override suspend fun iniciar(id: Int): Resultado<TareaGestor> = llamarApi(json) { api.iniciar(id) }

    override suspend fun terminar(id: Int): Resultado<TareaGestor> = llamarApi(json) { api.terminar(id) }
}
