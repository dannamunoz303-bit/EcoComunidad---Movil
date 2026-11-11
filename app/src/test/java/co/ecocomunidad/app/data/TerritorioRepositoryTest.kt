package co.ecocomunidad.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import co.ecocomunidad.app.data.modelo.Municipio
import co.ecocomunidad.app.data.modelo.Zona
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TerritorioRepositoryTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var servidor: MockWebServer
    private lateinit var alcanceDataStore: CoroutineScope
    private lateinit var sesion: SesionStore
    private lateinit var repositorio: TerritorioRepository

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
        repositorio = TerritorioRepositoryRemoto(cliente.territorioApi, cliente.json)
    }

    @After
    fun limpiar() {
        servidor.shutdown()
        alcanceDataStore.cancel()
    }

    @Test
    fun municipios_leeMunicipioSerializerConSuZona() = runTest {
        sesion.guardarTokens("access-1", "refresh-1")
        responder(
            200,
            """[{"id":1,"nombre":"Popayán","zona":{"id":2,"nombre":"centro"}},
                {"id":4,"nombre":"Santander de Quilichao","zona":{"id":1,"nombre":"norte"}}]""",
        )

        val municipios = (repositorio.municipios() as Resultado.Exito).datos

        val peticion = servidor.takeRequest()
        assertEquals("GET", peticion.method)
        assertEquals("/api/municipios/", peticion.path)
        assertEquals("Bearer access-1", peticion.getHeader("Authorization"))
        assertEquals(
            listOf(Municipio(1, "Popayán", Zona(2, "centro")), Municipio(4, "Santander de Quilichao", Zona(1, "norte"))),
            municipios,
        )
    }

    @Test
    fun sinConexion() = runTest {
        servidor.shutdown()

        assertTrue((repositorio.municipios() as Resultado.Fallo).error is ErrorAuth.SinConexion)
    }

    private fun responder(codigo: Int, cuerpo: String) {
        servidor.enqueue(
            MockResponse().setResponseCode(codigo)
                .setHeader("Content-Type", "application/json")
                .setBody(cuerpo)
        )
    }
}
