package co.ecocomunidad.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import co.ecocomunidad.app.data.modelo.EstadoNovedad
import co.ecocomunidad.app.data.modelo.NuevaNovedad
import co.ecocomunidad.app.data.modelo.TipoNovedad
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
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NovedadesRepositoryTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var servidor: MockWebServer
    private lateinit var alcanceDataStore: CoroutineScope
    private lateinit var sesion: SesionStore
    private lateinit var repositorio: NovedadesRepository

    @Before
    fun preparar() {
        servidor = MockWebServer().apply { start() }
        alcanceDataStore = CoroutineScope(Dispatchers.IO + SupervisorJob())
        sesion = SesionStore(
            PreferenceDataStoreFactory.create(scope = alcanceDataStore) {
                File(carpeta.root, "sesion.preferences_pb")
            }
        )
        val cliente = ApiCliente(sesion, EventosSesion(), servidor.url("/api").toString(), registrarPeticiones = false)
        repositorio = NovedadesRepositoryRemoto(cliente.novedadesApi, cliente.json)
    }

    @After
    fun limpiar() {
        servidor.shutdown()
        alcanceDataStore.cancel()
    }

    @Test
    fun crear_mandaLosCamposDeCrearNovedadSerializer() = runTest {
        sesion.guardarTokens("access-1", "refresh-1")
        responder(201, novedadJson(id = 20, tarea = "12"))

        val r = repositorio.crear(
            NuevaNovedad(reporteId = 5, tipo = TipoNovedad.QUEJA, descripcion = "No recogieron todo.", tareaId = 12),
        )

        val peticion = servidor.takeRequest()
        assertEquals("POST", peticion.method)
        assertEquals("/api/novedades/", peticion.path)
        assertEquals("Bearer access-1", peticion.getHeader("Authorization"))
        val cuerpo = Json.parseToJsonElement(peticion.body.readUtf8()).jsonObject
        assertEquals(
            JsonObject(
                mapOf(
                    "reporte" to JsonPrimitive(5),
                    "tipo" to JsonPrimitive("queja"),
                    "descripcion" to JsonPrimitive("No recogieron todo."),
                    "tarea" to JsonPrimitive(12),
                ),
            ),
            cuerpo,
        )
        with((r as Resultado.Exito).datos) {
            assertEquals(20, id)
            assertEquals(5, reporteId)
            assertEquals(12, tareaId)
            assertEquals(TipoNovedad.QUEJA, tipo)
            assertEquals(EstadoNovedad.ABIERTA, estado)
            assertNull(respuesta)
        }
    }

    @Test
    fun crearSinGestor_noMandaLaTarea() = runTest {
        responder(201, novedadJson(id = 21))

        repositorio.crear(NuevaNovedad(reporteId = 5, tipo = TipoNovedad.INCIDENTE, descripcion = "Volvieron a botar basura."))

        val cuerpo = Json.parseToJsonElement(servidor.takeRequest().body.readUtf8()).jsonObject
        assertFalse("tarea" in cuerpo)
        assertEquals(JsonPrimitive("incidente"), cuerpo["tipo"])
    }

    @Test
    fun listar_leeLosCamposDelBackend() = runTest {
        responder(
            200,
            "[${novedadJson(id = 2, estado = "atendida", respuesta = "\"Ya enviamos otro gestor.\"")}, ${novedadJson(id = 1)}]",
        )

        val novedades = (repositorio.listarMias() as Resultado.Exito).datos

        val peticion = servidor.takeRequest()
        assertEquals("GET", peticion.method)
        assertEquals("/api/novedades/", peticion.path)
        assertEquals(listOf(2, 1), novedades.map { it.id })
        with(novedades.first()) {
            assertEquals(EstadoNovedad.ATENDIDA, estado)
            assertEquals("Ya enviamos otro gestor.", respuesta)
            assertEquals("Basura en la esquina", reporteTitulo)
            assertEquals(7, creadoPor?.id)
            assertEquals("2026-10-05T09:00:00-05:00", fecha)
            assertNull(tareaId)
        }
    }

    @Test
    fun error400_traeElMensajeDelBackend() = runTest {
        responder(
            400,
            """{"reporte":["Solo puedes crear novedades cuando el reporte está asignado, en proceso o resuelto; este está pendiente."]}""",
        )

        val r = repositorio.crear(NuevaNovedad(reporteId = 5, tipo = TipoNovedad.QUEJA, descripcion = "Algo"))

        val error = (r as Resultado.Fallo).error as ErrorAuth.Validacion
        assertEquals(
            "Solo puedes crear novedades cuando el reporte está asignado, en proceso o resuelto; este está pendiente.",
            error.detail,
        )
        assertTrue("reporte" in error.campos)
    }

    @Test
    fun sinConexion() = runTest {
        servidor.shutdown()

        val error = (repositorio.listarMias() as Resultado.Fallo).error

        assertTrue(error is ErrorAuth.SinConexion)
    }

    private fun responder(codigo: Int, cuerpo: String) {
        servidor.enqueue(
            MockResponse().setResponseCode(codigo)
                .setHeader("Content-Type", "application/json")
                .setBody(cuerpo)
        )
    }

    /** Forma de NovedadSerializer. */
    private fun novedadJson(
        id: Int,
        tarea: String = "null",
        estado: String = "abierta",
        respuesta: String = "null",
    ) = """{"id":$id,"reporte":5,"reporte_titulo":"Basura en la esquina","tarea":$tarea,
        "creado_por":{"id":7,"nombre":"Ana","apellido":"Pérez"},"tipo":"queja",
        "descripcion":"No recogieron todo.","estado":"$estado","respuesta":$respuesta,
        "fecha":"2026-10-05T09:00:00-05:00"}"""
}
