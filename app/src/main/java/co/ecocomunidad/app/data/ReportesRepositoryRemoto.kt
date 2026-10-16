package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.CambiosReporte
import co.ecocomunidad.app.data.modelo.Categoria
import co.ecocomunidad.app.data.modelo.Foto
import co.ecocomunidad.app.data.modelo.NuevoReporte
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.data.modelo.Tarea
import co.ecocomunidad.app.data.remoto.ReportesApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.Locale

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

    override suspend fun categorias(): Resultado<List<Categoria>> = llamarApi(json) { api.categorias() }

    override suspend fun crear(nuevo: NuevoReporte): Resultado<Reporte> =
        llamarApi(json) { api.crear(cuerpo(nuevo)).conUrlsCompletas() }

    /**
     * Campos de ReporteCrearSerializer. Las fotos van como varias partes con el mismo nombre
     * "fotos", igual que en las pruebas del backend. Las coordenadas, con los 8 decimales de la columna.
     */
    private fun cuerpo(nuevo: NuevoReporte): MultipartBody =
        MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("categoria", nuevo.categoria.toString())
            .addFormDataPart("titulo", nuevo.titulo)
            .addFormDataPart("descripcion", nuevo.descripcion)
            .addFormDataPart("direccion", nuevo.direccion)
            .apply { nuevo.descripcionUbicacion?.let { addFormDataPart("descripcion_ubicacion", it) } }
            .addFormDataPart("latitud", coordenada(nuevo.latitud))
            .addFormDataPart("longitud", coordenada(nuevo.longitud))
            .agregarFotos(nuevo.fotos)
            .build()

    override suspend fun editar(id: Int, cambios: CambiosReporte): Resultado<Reporte> =
        llamarApi(json) { api.editar(id, cuerpo(cambios)).conUrlsCompletas() }

    override suspend fun eliminar(id: Int): Resultado<Unit> = llamarApi(json) { api.eliminar(id) }

    override suspend fun agregarFotos(id: Int, fotos: List<File>): Resultado<Reporte> =
        llamarApi(json) {
            val cuerpo = MultipartBody.Builder().setType(MultipartBody.FORM).agregarFotos(fotos).build()
            api.agregarFotos(id, cuerpo).conUrlsCompletas()
        }

    override suspend fun borrarFoto(id: Int, fotoId: Int): Resultado<Unit> =
        llamarApi(json) { api.borrarFoto(id, fotoId) }

    /** JSON solo con los campos de ReporteEditarSerializer que cambiaron. */
    private fun cuerpo(cambios: CambiosReporte): JsonObject = buildJsonObject {
        cambios.categoria?.let { put("categoria", it) }
        cambios.titulo?.let { put("titulo", it) }
        cambios.descripcion?.let { put("descripcion", it) }
        cambios.direccion?.let { put("direccion", it) }
        cambios.descripcionUbicacion?.let { put("descripcion_ubicacion", it) }
        cambios.latitud?.let { put("latitud", coordenada(it)) }
        cambios.longitud?.let { put("longitud", coordenada(it)) }
    }

    private fun MultipartBody.Builder.agregarFotos(fotos: List<File>) = apply {
        for (foto in fotos) addFormDataPart("fotos", foto.name, foto.asRequestBody(JPEG))
    }

    private fun coordenada(valor: Double): String = String.format(Locale.ROOT, "%.8f", valor)

    private fun Reporte.conUrlsCompletas() = copy(fotos = fotos.conUrlsCompletas())

    private fun List<Foto>.conUrlsCompletas() = map { it.copy(url = raiz.resolve(it.url)?.toString() ?: it.url) }

    private companion object {
        val JPEG = "image/jpeg".toMediaType()
    }
}
