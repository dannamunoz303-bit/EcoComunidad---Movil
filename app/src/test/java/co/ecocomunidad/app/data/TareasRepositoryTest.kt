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

class TareasRepositoryTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var servidor: MockWebServer
    private lateinit var alcanceDataStore: CoroutineScope
    private lateinit var sesion: SesionStore
    private lateinit var repositorio: TareasRepository

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
        repositorio = TareasRepositoryRemoto(cliente.tareasApi, cliente.json, urlApi)
    }

    @After
    fun limpiar() {
        servidor.shutdown()
        alcanceDataStore.cancel()
    }

    @Test
    fun listar_leeLosCamposDeTareaGestorSerializer() = runTest {
        sesion.guardarTokens("access-1", "refresh-1")
        responder(200, "[${tareaJson(12, "en_proceso")}, ${tareaJson(9, "terminada", fin = "\"2026-10-04T16:00:00-05:00\"")}]")

        val tareas = (repositorio.mias() as Resultado.Exito).datos

        val peticion = servidor.takeRequest()
        assertEquals("GET", peticion.method)
        assertEquals("/api/tareas/mias/", peticion.path)
        assertEquals("Bearer access-1", peticion.getHeader("Authorization"))
        with(tareas.first()) {
            assertEquals(12, id)
            assertEquals(EstadoTarea.EN_PROCESO, estado)
            assertEquals("2026-10-02T09:00:00-05:00", fechaAsignacion)
            assertNull(fechaFin)
            assertEquals(5, reporte.id)
            assertEquals("Basura en la esquina", reporte.titulo)
            assertEquals("Basura acumulada", reporte.categoria)
            assertEquals("Calle 10 # 5-20", reporte.direccion)
            assertEquals("Frente al parque", reporte.descripcionUbicacion)
            assertEquals("4.60971000", reporte.latitud)
            assertEquals(EstadoReporte.EN_PROCESO, reporte.estado)
        }
        assertEquals("2026-10-04T16:00:00-05:00", tareas[1].fechaFin)
    }

    @Test
    fun detalle_buscaLaTareaEnMias() = runTest {
        responder(200, "[${tareaJson(12, "asignada")}, ${tareaJson(9, "terminada")}]")

        val tarea = (repositorio.tarea(9) as Resultado.Exito).datos

        assertEquals("/api/tareas/mias/", servidor.takeRequest().path)
        assertEquals(EstadoTarea.TERMINADA, tarea.estado)
    }

    @Test
    fun detalleDeUnaTareaAjena_respondeNoEncontrada() = runTest {
        responder(200, "[${tareaJson(12, "asignada")}]")

        val error = (repositorio.tarea(99) as Resultado.Fallo).error

        assertEquals(404, (error as ErrorAuth.Servidor).codigoHttp)
    }

    @Test
    fun acciones_traenSusEvidenciasConUrlCompleta() = runTest {
        responder(
            200,
            """[{"id":31,"descripcion":"Recogí las bolsas.","fecha":"2026-10-03T10:00:00-05:00",
                 "fotos":[{"id":7,"url":"/media/evidencias/a.jpg","fecha":"2026-10-03T10:01:00-05:00"},
                          {"id":8,"url":"https://res.cloudinary.com/demo/b.jpg","fecha":"2026-10-03T10:02:00-05:00"}]},
                {"id":30,"descripcion":"Llegué al lugar.","fecha":"2026-10-03T09:00:00-05:00","fotos":[]}]""",
        )

        val acciones = (repositorio.acciones(12) as Resultado.Exito).datos

        assertEquals("/api/tareas/12/acciones/", servidor.takeRequest().path)
        assertEquals(listOf(31, 30), acciones.map { it.id })
        assertEquals(
            listOf(servidor.url("/media/evidencias/a.jpg").toString(), "https://res.cloudinary.com/demo/b.jpg"),
            acciones.first().fotos.map { it.url },
        )
        assertTrue(acciones[1].fotos.isEmpty())
    }

    @Test
    fun iniciar_mandaPostSinCuerpo() = runTest {
        responder(200, tareaJson(12, "en_proceso"))

        val tarea = (repositorio.iniciar(12) as Resultado.Exito).datos

        val peticion = servidor.takeRequest()
        assertEquals("POST", peticion.method)
        assertEquals("/api/tareas/12/iniciar/", peticion.path)
        assertEquals(0L, peticion.bodySize)
        assertEquals(EstadoTarea.EN_PROCESO, tarea.estado)
    }

    @Test
    fun terminar_mandaPostYTraeLaFechaFin() = runTest {
        responder(200, tareaJson(12, "terminada", fin = "\"2026-10-05T17:00:00-05:00\""))

        val tarea = (repositorio.terminar(12) as Resultado.Exito).datos

        val peticion = servidor.takeRequest()
        assertEquals("POST", peticion.method)
        assertEquals("/api/tareas/12/terminar/", peticion.path)
        assertEquals(EstadoTarea.TERMINADA, tarea.estado)
        assertEquals("2026-10-05T17:00:00-05:00", tarea.fechaFin)
    }

    @Test
    fun terminarSinEvidencia_traeElMensajeDelBackend() = runTest {
        responder(400, """{"detail":"Para terminar la tarea registra al menos una acción con una foto de evidencia."}""")

        val error = (repositorio.terminar(12) as Resultado.Fallo).error

        assertTrue(error is ErrorAuth.Validacion)
        assertEquals("Para terminar la tarea registra al menos una acción con una foto de evidencia.", error.detail)
    }

    @Test
    fun sinConexion() = runTest {
        servidor.shutdown()

        val error = (repositorio.mias() as Resultado.Fallo).error

        assertTrue(error is ErrorAuth.SinConexion)
    }

    private fun responder(codigo: Int, cuerpo: String) {
        servidor.enqueue(
            MockResponse().setResponseCode(codigo)
                .setHeader("Content-Type", "application/json")
                .setBody(cuerpo)
        )
    }

    /** Forma de TareaGestorSerializer con su ReporteBasicoSerializer. */
    private fun tareaJson(id: Int, estado: String, fin: String = "null") =
        """{"id":$id,"reporte":{"id":5,"titulo":"Basura en la esquina","categoria":"Basura acumulada",
            "direccion":"Calle 10 # 5-20","descripcion_ubicacion":"Frente al parque",
            "latitud":"4.60971000","longitud":"-74.08175000","estado":"en_proceso"},
            "estado":"$estado","fecha_asignacion":"2026-10-02T09:00:00-05:00","fecha_fin":$fin}"""
}
