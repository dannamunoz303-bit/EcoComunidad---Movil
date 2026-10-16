package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.Foto
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.data.modelo.Tarea
import co.ecocomunidad.app.data.remoto.ReportesApi
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * [urlApi]: la misma de ApiCliente. Sirve para completar las url de fotos relativas
 * (en desarrollo el backend las guarda como /media/...; en producción llegan absolutas).
 */
class ReportesRepositoryRemoto(
    private val api: ReportesApi,
    private val json: Json,
    urlApi: String,
) : ReportesRepository {

    private val raiz: HttpUrl = urlApi.toHttpUrl().resolve("/")!!

    override suspend fun listar(soloMios: Boolean): Resultado<List<Reporte>> =
        llamarApi(json) { api.listar(mios = if (soloMios) "true" else null).map { it.conUrlsCompletas() } }

    override suspend fun detalle(id: Int): Resultado<Reporte> =
        llamarApi(json) { api.detalle(id).conUrlsCompletas() }

    override suspend fun tareas(id: Int): Resultado<List<Tarea>> =
        llamarApi(json) {
            api.tareas(id).map { tarea ->
                tarea.copy(acciones = tarea.acciones.map { it.copy(fotos = it.fotos.conUrlsCompletas()) })
            }
        }

    private fun Reporte.conUrlsCompletas() = copy(fotos = fotos.conUrlsCompletas())

    private fun List<Foto>.conUrlsCompletas() = map { it.copy(url = raiz.resolve(it.url)?.toString() ?: it.url) }
}
