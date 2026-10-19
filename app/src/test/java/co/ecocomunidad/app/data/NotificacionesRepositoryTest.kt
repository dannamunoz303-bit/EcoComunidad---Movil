package co.ecocomunidad.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NotificacionesRepositoryTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var servidor: MockWebServer
    private lateinit var alcanceDataStore: CoroutineScope
    private lateinit var sesion: SesionStore
    private lateinit var repositorio: NotificacionesRepository

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
        repositorio = NotificacionesRepositoryRemoto(cliente.notificacionesApi, cliente.json)
    }

    @After
    fun limpiar() {
        servidor.shutdown()
        alcanceDataStore.cancel()
    }

    @Test
    fun listar_leeLosCamposDelBackend() = runTest {
        sesion.guardarTokens("access-1", "refresh-1")
        responder(
            200,
            """[{"id":3,"tipo":"novedad_respondida","mensaje":"El administrador respondió tu novedad.",
                 "leida":false,"fecha":"2026-10-07T09:00:00-05:00","reporte":5},
                {"id":2,"tipo":"contrasena_restablecida","mensaje":"Tu contraseña fue restablecida.",
                 "leida":true,"fecha":"2026-10-06T09:00:00-05:00","reporte":null}]""",
        )

        val lista = (repositorio.listar() as Resultado.Exito).datos

        val peticion = servidor.takeRequest()
        assertEquals("GET", peticion.method)
        assertEquals("/api/notificaciones/", peticion.path)
        assertEquals("Bearer access-1", peticion.getHeader("Authorization"))
        with(lista.first()) {
            assertEquals(3, id)
            assertEquals("novedad_respondida", tipo)
            assertEquals("El administrador respondió tu novedad.", mensaje)
            assertFalse(leida)
            assertEquals("2026-10-07T09:00:00-05:00", fecha)
            assertEquals(5, reporteId)
        }
        // Un código que la app no trata aparte también se lee
        assertEquals("contrasena_restablecida", lista[1].tipo)
        assertTrue(lista[1].leida)
        assertNull(lista[1].reporteId)
    }

    @Test
    fun contarNoLeidas_usaLaRutaDelBackend() = runTest {
        responder(200, """{"total":4}""")

        val total = (repositorio.contarNoLeidas() as Resultado.Exito).datos

        assertEquals("/api/notificaciones/no-leidas/", servidor.takeRequest().path)
        assertEquals(4, total)
    }

    @Test
    fun marcarLeida_mandaPostSinCuerpo() = runTest {
        responder(
            200,
            """{"id":3,"tipo":"sancion","mensaje":"Recibiste una advertencia.","leida":true,
                "fecha":"2026-10-07T09:00:00-05:00","reporte":null}""",
        )

        val notificacion = (repositorio.marcarLeida(3) as Resultado.Exito).datos

        val peticion = servidor.takeRequest()
        assertEquals("POST", peticion.method)
        assertEquals("/api/notificaciones/3/leer/", peticion.path)
        assertEquals(0L, peticion.bodySize)
        assertTrue(notificacion.leida)
    }

    @Test
    fun error400_traeElMensajeDelBackend() = runTest {
        responder(400, """{"leida":"Usa true o false."}""")

        val error = (repositorio.listar() as Resultado.Fallo).error as ErrorAuth.Validacion

        assertEquals("Usa true o false.", error.detail)
        assertEquals(listOf("Usa true o false."), error.campos["leida"])
    }

    @Test
    fun marcarUnaQueNoEsMia_respondeNoEncontrada() = runTest {
        responder(404, """{"detail":"No encontrado."}""")

        val error = (repositorio.marcarLeida(99) as Resultado.Fallo).error

        assertEquals(ErrorAuth.Servidor(404, "No encontrado.", null), error)
    }

    @Test
    fun sinConexion() = runTest {
        servidor.shutdown()

        val error = (repositorio.contarNoLeidas() as Resultado.Fallo).error

        assertTrue(error is ErrorAuth.SinConexion)
    }

    private fun responder(codigo: Int, cuerpo: String) {
        servidor.enqueue(
            MockResponse().setResponseCode(codigo)
                .setHeader("Content-Type", "application/json")
                .setBody(cuerpo)
        )
    }
}
