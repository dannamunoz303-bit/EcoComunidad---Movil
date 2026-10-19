package co.ecocomunidad.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import co.ecocomunidad.app.data.modelo.TipoSancion
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

class SancionesRepositoryTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var servidor: MockWebServer
    private lateinit var alcanceDataStore: CoroutineScope
    private lateinit var sesion: SesionStore
    private lateinit var repositorio: SancionesRepository

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
        repositorio = SancionesRepositoryRemoto(cliente.sancionesApi, cliente.json)
    }

    @After
    fun limpiar() {
        servidor.shutdown()
        alcanceDataStore.cancel()
    }

    @Test
    fun listarMias_leeLosCamposDeSancionCiudadanoSerializer() = runTest {
        responder(
            200,
            """[{"id":2,"tipo":"bloqueo_temporal","motivo":"Segundo reporte falso.",
                 "fecha_inicio":"2026-10-05T09:00:00-05:00","fecha_fin":"2026-10-07T09:00:00-05:00","reporte":8},
                {"id":1,"tipo":"advertencia","motivo":"Lenguaje ofensivo.",
                 "fecha_inicio":"2026-10-01T09:00:00-05:00","fecha_fin":null,"reporte":null}]""",
        )

        val sanciones = (repositorio.listarMias() as Resultado.Exito).datos

        val peticion = servidor.takeRequest()
        assertEquals("GET", peticion.method)
        assertEquals("/api/sanciones/mias/", peticion.path)
        with(sanciones.first()) {
            assertEquals(TipoSancion.BLOQUEO_TEMPORAL, tipo)
            assertEquals("Segundo reporte falso.", motivo)
            assertEquals("2026-10-05T09:00:00-05:00", fechaInicio)
            assertEquals("2026-10-07T09:00:00-05:00", fechaFin)
            assertEquals(8, reporteId)
        }
        with(sanciones[1]) {
            assertEquals(TipoSancion.ADVERTENCIA, tipo)
            assertNull(fechaFin)
            assertNull(reporteId)
        }
    }

    @Test
    fun error400_traeElMensajeDelBackend() = runTest {
        responder(400, """{"detail":"Solicitud no válida."}""")

        val error = (repositorio.listarMias() as Resultado.Fallo).error

        assertEquals("Solicitud no válida.", error.detail)
    }

    @Test
    fun otroRol_respondeSinPermiso() = runTest {
        responder(403, """{"detail":"Esta acción es solo para ciudadanos."}""")

        val error = (repositorio.listarMias() as Resultado.Fallo).error

        assertEquals(ErrorAuth.Servidor(403, "Esta acción es solo para ciudadanos.", null), error)
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
}
