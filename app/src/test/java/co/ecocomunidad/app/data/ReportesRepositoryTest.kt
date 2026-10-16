package co.ecocomunidad.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import co.ecocomunidad.app.data.modelo.EstadoReporte
import co.ecocomunidad.app.data.modelo.EstadoTarea
import co.ecocomunidad.app.data.remoto.ApiCliente
import co.ecocomunidad.app.data.sesion.EventosSesion
import co.ecocomunidad.app.data.sesion.SesionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReportesRepositoryTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var servidor: MockWebServer
    private lateinit var alcanceDataStore: CoroutineScope
    private lateinit var sesion: SesionStore
    private lateinit var repositorio: ReportesRepository

    @Before
    fun preparar() {
        servidor = MockWebServer().apply { start() }
        alcanceDataStore = CoroutineScope(Dispatchers.IO + SupervisorJob())
        sesion = SesionStore(
            PreferenceDataStoreFactory.create(scope = alcanceDataStore) {
                File(carpeta.root, "sesion.preferences_pb")
            }
        )
        val urlApi = servidor.url("/api").toString()
        val cliente = ApiCliente(sesion, EventosSesion(), urlApi, registrarPeticiones = false)
        repositorio = ReportesRepositoryRemoto(cliente.reportesApi, cliente.json, urlApi)
    }

    @After
    fun limpiar() {
        servidor.shutdown()
        alcanceDataStore.cancel()
    }

    @Test
    fun listarTodos_leeLosCamposDelBackendSinFiltrar() = runTest {
        sesion.guardarTokens("access-1", "refresh-1")
        responder(200, "[${reporteJson(id = 1, ciudadano = "null")}, ${reporteJson(id = 2, estado = "pendiente")}]")

        val reportes = (repositorio.listar(soloMios = false) as Resultado.Exito).datos

        val peticion = servidor.takeRequest()
        assertEquals("/api/reportes/", peticion.path)
        assertEquals("Bearer access-1", peticion.getHeader("Authorization"))
        // RN-10 la aplica el backend: la app muestra todo lo que llega
        assertEquals(listOf(1, 2), reportes.map { it.id })
        with(reportes.first()) {
            assertNull(ciudadano)
            assertEquals(3, categoriaId)
            assertEquals("Basura acumulada", categoria)
            assertEquals("Calle 10 # 5-20", direccion)
            assertEquals(EstadoReporte.EN_PROCESO, estado)
            assertEquals("2026-10-01T10:00:00-05:00", fechaCreacion)
            assertEquals("https://res.cloudinary.com/demo/image/upload/a.jpg", fotos.single().url)
        }
        assertEquals(EstadoReporte.PENDIENTE, reportes[1].estado)
    }

    @Test
    fun misReportes_usaElFiltroMios() = runTest {
        responder(200, "[]")

        val r = repositorio.listar(soloMios = true)

        assertEquals("/api/reportes/?mios=true", servidor.takeRequest().path)
        assertTrue((r as Resultado.Exito).datos.isEmpty())
    }

    @Test
    fun fotoConUrlRelativa_seCompletaConElServidor() = runTest {
        responder(200, "[${reporteJson(id = 1, urlFoto = "/media/reportes/a.jpg")}]")

        val foto = (repositorio.listar(soloMios = false) as Resultado.Exito).datos.single().fotos.single()

        assertEquals(servidor.url("/media/reportes/a.jpg").toString(), foto.url)
    }

    @Test
    fun detalle_traeElMotivoDeRechazo() = runTest {
        responder(
            200,
            reporteJson(id = 5, estado = "rechazado", motivo = "\"No es un tema ambiental.\""),
        )

        val reporte = (repositorio.detalle(5) as Resultado.Exito).datos

        assertEquals("/api/reportes/5/", servidor.takeRequest().path)
        assertEquals(EstadoReporte.RECHAZADO, reporte.estado)
        assertEquals("No es un tema ambiental.", reporte.motivoRechazo)
        assertEquals(7, reporte.ciudadano?.id)
    }

    @Test
    fun tareas_traenAccionesConEvidencias() = runTest {
        responder(
            200,
            """[{"id":11,"gestor":{"id":4,"nombre":"Luis","apellido":"Gómez"},"estado":"terminada",
               "fecha_asignacion":"2026-10-02T09:00:00-05:00","fecha_fin":"2026-10-03T16:00:00-05:00",
               "acciones":[{"id":21,"descripcion":"Se recogió la basura.","fecha":"2026-10-03T15:00:00-05:00",
               "fotos":[{"id":31,"url":"/media/evidencias/b.jpg","fecha":"2026-10-03T15:00:00-05:00"}]}]}]""",
        )

        val tarea = (repositorio.tareas(5) as Resultado.Exito).datos.single()

        assertEquals("/api/reportes/5/tareas/", servidor.takeRequest().path)
        assertEquals("Luis", tarea.gestor?.nombre)
        assertEquals(EstadoTarea.TERMINADA, tarea.estado)
        val accion = tarea.acciones.single()
        assertEquals("Se recogió la basura.", accion.descripcion)
        assertEquals(servidor.url("/media/evidencias/b.jpg").toString(), accion.fotos.single().url)
    }

    @Test
    fun tareasDeUnReporteAjeno_respondenSinPermiso() = runTest {
        responder(403, """{"detail":"No puedes ver las tareas de este reporte."}""")

        val error = (repositorio.tareas(5) as Resultado.Fallo).error

        assertEquals(403, (error as ErrorAuth.Servidor).codigoHttp)
    }

    @Test
    fun reporteQueNoExiste() = runTest {
        responder(404, """{"detail":"No encontrado."}""")

        val error = (repositorio.detalle(99) as Resultado.Fallo).error

        assertEquals(404, (error as ErrorAuth.Servidor).codigoHttp)
    }

    @Test
    fun sinConexion() = runTest {
        servidor.shutdown()

        val error = (repositorio.listar(soloMios = false) as Resultado.Fallo).error

        assertTrue(error is ErrorAuth.SinConexion)
    }

    private fun responder(codigo: Int, cuerpo: String) {
        servidor.enqueue(
            MockResponse().setResponseCode(codigo)
                .setHeader("Content-Type", "application/json")
                .setBody(cuerpo)
        )
    }

    /** Forma de ReporteSerializer, con campos que la app ignora (latitud, longitud, eliminado). */
    private fun reporteJson(
        id: Int,
        estado: String = "en_proceso",
        ciudadano: String = """{"id":7,"nombre":"Ana","apellido":"Pérez"}""",
        motivo: String = "null",
        urlFoto: String = "https://res.cloudinary.com/demo/image/upload/a.jpg",
    ) = """{"id":$id,"ciudadano":$ciudadano,"categoria_id":3,"categoria":"Basura acumulada",
        "titulo":"Basura en la esquina","descripcion":"Hay bolsas desde el lunes.",
        "direccion":"Calle 10 # 5-20","descripcion_ubicacion":"Frente al parque",
        "latitud":"4.60971000","longitud":"-74.08175000","estado":"$estado","motivo_rechazo":$motivo,
        "eliminado":false,"fecha_creacion":"2026-10-01T10:00:00-05:00",
        "fecha_actualizacion":"2026-10-02T10:00:00-05:00","fecha_revision":null,
        "fotos":[{"id":1,"url":"$urlFoto","fecha":"2026-10-01T10:00:00-05:00"}]}"""
}
