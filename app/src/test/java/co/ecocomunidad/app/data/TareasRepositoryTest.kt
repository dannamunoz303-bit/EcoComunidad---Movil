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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartReader
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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

    // --- Registrar acción (RN-14) y subir evidencias (RN-15) ---

    @Test
    fun crearAccion_mandaSoloLaDescripcionEnJson() = runTest {
        responder(201, """{"id":40,"descripcion":"Recogí las bolsas.","fecha":"2026-10-05T10:00:00-05:00","fotos":[]}""")

        val accion = (repositorio.crearAccion(12, "Recogí las bolsas.") as Resultado.Exito).datos

        val peticion = servidor.takeRequest()
        assertEquals("POST", peticion.method)
        assertEquals("/api/tareas/12/acciones/", peticion.path)
        assertTrue(peticion.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals(
            JsonObject(mapOf("descripcion" to JsonPrimitive("Recogí las bolsas."))),
            Json.parseToJsonElement(peticion.body.readUtf8()),
        )
        assertEquals(40, accion.id)
        assertTrue(accion.fotos.isEmpty())
    }

    @Test
    fun accionConFotos_creaLaAccionYLuegoSubeLasFotosEnMultipart() = runTest {
        responder(201, """{"id":40,"descripcion":"Recogí las bolsas.","fecha":"2026-10-05T10:00:00-05:00","fotos":[]}""")
        responder(
            201,
            """{"id":40,"descripcion":"Recogí las bolsas.","fecha":"2026-10-05T10:00:00-05:00",
                "fotos":[{"id":7,"url":"/media/evidencias/a.jpg","fecha":"2026-10-05T10:01:00-05:00"},
                         {"id":8,"url":"/media/evidencias/b.jpg","fecha":"2026-10-05T10:01:00-05:00"}]}""",
        )

        val creada = (repositorio.crearAccion(12, "Recogí las bolsas.") as Resultado.Exito).datos
        val conFotos = (repositorio.subirEvidencias(creada.id, listOf(foto("a.jpg", "AAA"), foto("b.jpg", "BBB"))) as Resultado.Exito).datos

        assertEquals("/api/tareas/12/acciones/", servidor.takeRequest().path)
        val subida = servidor.takeRequest()
        assertEquals("POST", subida.method)
        assertEquals("/api/acciones/40/fotos/", subida.path)
        // Varias partes con el mismo nombre "fotos", como espera EvidenciasSerializer
        assertEquals(
            listOf(
                Parte("fotos", "a.jpg", "image/jpeg", "AAA"),
                Parte("fotos", "b.jpg", "image/jpeg", "BBB"),
            ),
            partes(subida),
        )
        assertEquals(listOf(servidor.url("/media/evidencias/a.jpg").toString(), servidor.url("/media/evidencias/b.jpg").toString()),
            conFotos.fotos.map { it.url })
    }

    @Test
    fun crearAccionSinDescripcion_traeElErrorDelCampo() = runTest {
        responder(400, """{"descripcion":["La descripción no puede estar vacía."]}""")

        val error = (repositorio.crearAccion(12, " ") as Resultado.Fallo).error as ErrorAuth.Validacion

        assertEquals(listOf("La descripción no puede estar vacía."), error.campos["descripcion"])
    }

    @Test
    fun crearAccionEnTareaTerminada_traeElDetail() = runTest {
        responder(400, """{"detail":"No se puede registrar acciones en una tarea terminada."}""")

        val error = (repositorio.crearAccion(12, "Algo") as Resultado.Fallo).error

        assertEquals("No se puede registrar acciones en una tarea terminada.", error.detail)
    }

    @Test
    fun subirDeMasEvidencias_traeElErrorDeFotos() = runTest {
        responder(400, """{"fotos":"Una acción puede tener máximo 5 evidencias."}""")

        val error = (repositorio.subirEvidencias(40, listOf(foto("a.jpg", "AAA"))) as Resultado.Fallo).error as ErrorAuth.Validacion

        assertEquals(listOf("Una acción puede tener máximo 5 evidencias."), error.campos["fotos"])
    }

    @Test
    fun crearAccionSinConexion() = runTest {
        servidor.shutdown()

        val error = (repositorio.crearAccion(12, "Algo") as Resultado.Fallo).error

        assertTrue(error is ErrorAuth.SinConexion)
    }

    private fun foto(nombre: String, contenido: String): File =
        File(carpeta.root, nombre).apply { writeText(contenido) }

    private data class Parte(val nombre: String, val archivo: String?, val tipo: String?, val contenido: String)

    /** Lee las partes del multipart que recibió el servidor, en orden. */
    private fun partes(peticion: RecordedRequest): List<Parte> {
        val tipo = peticion.getHeader("Content-Type")!!.toMediaType()
        assertEquals("multipart/form-data", "${tipo.type}/${tipo.subtype}")
        val lector = MultipartReader(peticion.body, tipo.parameter("boundary")!!)
        return generateSequence { lector.nextPart() }.map { parte ->
            val disposicion = parte.headers["Content-Disposition"]!!
            Parte(
                nombre = Regex("""name="([^"]+)"""").find(disposicion)!!.groupValues[1],
                archivo = Regex("""filename="([^"]+)"""").find(disposicion)?.groupValues?.get(1),
                tipo = parte.headers["Content-Type"],
                contenido = parte.body.readUtf8(),
            )
        }.toList()
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
