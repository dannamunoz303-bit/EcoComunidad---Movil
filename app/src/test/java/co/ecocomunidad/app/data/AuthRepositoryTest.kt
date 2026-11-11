package co.ecocomunidad.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import co.ecocomunidad.app.data.modelo.EstadoUsuario
import co.ecocomunidad.app.data.modelo.RegistroPeticion
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.data.modelo.UsuarioActual
import co.ecocomunidad.app.data.remoto.ApiCliente
import co.ecocomunidad.app.data.sesion.EventosSesion
import co.ecocomunidad.app.data.sesion.SesionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

class AuthRepositoryTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var servidor: MockWebServer
    private lateinit var alcanceDataStore: CoroutineScope
    private lateinit var sesion: SesionStore
    private lateinit var eventos: EventosSesion
    private lateinit var alcanceRepositorio: CoroutineScope
    private lateinit var repositorio: AuthRepository

    /** Lo que entrega el "Firebase" de la prueba; null = no hay token (no se llama al backend). */
    private var tokenCelular: String? = null

    @Before
    fun preparar() {
        servidor = MockWebServer().apply { start() }
        alcanceDataStore = CoroutineScope(Dispatchers.IO + SupervisorJob())
        sesion = SesionStore(
            PreferenceDataStoreFactory.create(scope = alcanceDataStore) {
                File(carpeta.root, "sesion.preferences_pb")
            }
        )
        eventos = EventosSesion()
        val cliente = ApiCliente(sesion, eventos, servidor.url("/api").toString(), registrarPeticiones = false)
        alcanceRepositorio = CoroutineScope(Dispatchers.IO + SupervisorJob())
        repositorio = AuthRepositoryRemoto(
            cliente.authApi, sesion, cliente.json, cliente.dispositivosApi, { tokenCelular }, alcanceRepositorio,
        )
    }

    @After
    fun limpiar() {
        servidor.shutdown()
        alcanceRepositorio.cancel()
        alcanceDataStore.cancel()
    }

    // --- Login ---

    @Test
    fun loginCorrecto_guardaTokensYUsuario() = runTest {
        responder(200, LOGIN_OK)
        responder(200, yo(debeCambiar = false))

        val r = repositorio.login("ana@correo.co", "clave-segura-1")

        val usuario = (r as Resultado.Exito).datos
        assertEquals(Rol.CIUDADANO, usuario.rol)
        assertEquals(EstadoUsuario.ACTIVO, usuario.estado)
        assertEquals("access-1", sesion.access())
        assertEquals("refresh-1", sesion.refresh())
        assertEquals("ana@correo.co", sesion.usuario.first()?.correo)

        val login = servidor.takeRequest()
        assertEquals("/api/auth/login/", login.path)
        assertEquals("""{"correo":"ana@correo.co","password":"clave-segura-1"}""", login.body.readUtf8())
        assertNull(login.getHeader("Authorization"))
        assertNull(login.getHeader("Sin-Autorizacion"))

        val yo = servidor.takeRequest()
        assertEquals("/api/auth/yo/", yo.path)
        assertEquals("Bearer access-1", yo.getHeader("Authorization"))
    }

    @Test
    fun loginConContrasenaPendiente_entraConLaMarca() = runTest {
        responder(200, LOGIN_OK.replace("false", "true"))
        responder(200, yo(debeCambiar = true))

        val r = repositorio.login("gestor@correo.co", "temporal-1")

        assertTrue((r as Resultado.Exito).datos.debeCambiarContrasena)
        assertEquals(true, sesion.usuario.first()?.debeCambiarContrasena)
    }

    // --- Registro ---

    @Test
    fun registro_usaLaRutaYLosCamposDelBackend_sinIniciarSesion() = runTest {
        sesion.guardarTokens("viejo", "viejo")
        responder(201, """{"id":9,"nombre":"Ana","apellido":"Pérez","correo":"ana@correo.co","telefono":null}""")

        val r = repositorio.registrar(
            RegistroPeticion("Ana", "Pérez", "1020304050", "ana@correo.co", "clave-segura-1"),
        )

        assertEquals(9, (r as Resultado.Exito).datos.id)
        val peticion = servidor.takeRequest()
        assertEquals("/api/auth/registro/", peticion.path)
        assertEquals("POST", peticion.method)
        // Sin teléfono el campo no se envía, y el registro no lleva token
        assertEquals(
            """{"nombre":"Ana","apellido":"Pérez","cedula":"1020304050","correo":"ana@correo.co",""" +
                """"password":"clave-segura-1"}""",
            peticion.body.readUtf8(),
        )
        assertNull(peticion.getHeader("Authorization"))
        assertNull(sesion.usuario.first())
    }

    @Test
    fun registroConDatosRepetidos_devuelveLosErroresPorCampo() = runTest {
        responder(
            400,
            """{"cedula":["Ya existe un usuario con esta cédula."],""" +
                """"correo":["Ya existe un usuario con este correo."]}""",
        )

        val r = repositorio.registrar(
            RegistroPeticion("Ana", "Pérez", "1020304050", "ana@correo.co", "clave-segura-1", "3001234567"),
        )

        val campos = ((r as Resultado.Fallo).error as ErrorAuth.Validacion).campos
        assertEquals(listOf("Ya existe un usuario con esta cédula."), campos["cedula"])
        assertEquals(listOf("Ya existe un usuario con este correo."), campos["correo"])
        assertEquals(1, servidor.requestCount)
    }

    @Test
    fun loginCredencialesMalas_noIntentaRenovar() = runTest {
        responder(401, """{"detail":"No active account found with the given credentials"}""")

        val r = repositorio.login("ana@correo.co", "mala")

        assertTrue((r as Resultado.Fallo).error is ErrorAuth.CredencialesInvalidas)
        assertEquals(1, servidor.requestCount)
        assertNull(sesion.access())
    }

    @Test
    fun loginInactivo() = runTest {
        responder(403, """{"detail":"Tu cuenta está inactiva.","code":"usuario_inactivo","bloqueado_hasta":null}""")

        val error = (repositorio.login("a@b.co", "x") as Resultado.Fallo).error

        assertTrue(error is ErrorAuth.Inactivo)
        assertEquals("usuario_inactivo", error.code)
        assertEquals("Tu cuenta está inactiva.", error.detail)
    }

    @Test
    fun loginBloqueadoTemporal_traeLaFecha() = runTest {
        responder(
            403,
            """{"detail":"Tu cuenta está bloqueada hasta 2026-10-20 08:30.","code":"usuario_bloqueado",""" +
                """"bloqueado_hasta":"2026-10-20T08:30:00.123456-05:00"}""",
        )

        val error = (repositorio.login("a@b.co", "x") as Resultado.Fallo).error

        assertTrue(error is ErrorAuth.Bloqueado)
        assertEquals(
            OffsetDateTime.parse("2026-10-20T08:30:00.123456-05:00"),
            (error as ErrorAuth.Bloqueado).hasta,
        )
        assertEquals("usuario_bloqueado", error.code)
    }

    @Test
    fun loginBloqueadoPermanente() = runTest {
        responder(
            403,
            """{"detail":"Tu cuenta está bloqueada de forma permanente.",""" +
                """"code":"usuario_bloqueado_permanente","bloqueado_hasta":null}""",
        )

        val error = (repositorio.login("a@b.co", "x") as Resultado.Fallo).error

        assertTrue(error is ErrorAuth.BloqueadoPermanente)
        assertEquals("usuario_bloqueado_permanente", error.code)
    }

    @Test
    fun peticionConContrasenaPendiente() = runTest {
        sesion.guardarTokens("access-1", "refresh-1")
        // Con este code el backend no manda bloqueado_hasta
        responder(403, """{"detail":"Debes cambiar tu contraseña antes de continuar.","code":"debe_cambiar_contrasena"}""")

        val error = (repositorio.usuarioActual() as Resultado.Fallo).error

        assertTrue(error is ErrorAuth.DebeCambiarContrasena)
        assertEquals("debe_cambiar_contrasena", error.code)
        assertEquals("access-1", sesion.access())
    }

    // --- Renovación del token ---

    @Test
    fun refreshExitosoTras401_reintentaConElTokenNuevo() = runTest {
        sesion.guardarTokens("vencido", "refresh-1")
        responder(401, TOKEN_INVALIDO)
        responder(200, """{"access":"access-2"}""")
        responder(200, yo(debeCambiar = false))

        val r = repositorio.usuarioActual()

        assertTrue(r is Resultado.Exito)
        assertEquals("access-2", sesion.access())
        assertEquals("refresh-1", sesion.refresh())

        assertEquals("Bearer vencido", servidor.takeRequest().getHeader("Authorization"))
        val refresh = servidor.takeRequest()
        assertEquals("/api/auth/refresh/", refresh.path)
        assertEquals("""{"refresh":"refresh-1"}""", refresh.body.readUtf8())
        assertNull(refresh.getHeader("Authorization"))
        assertEquals("Bearer access-2", servidor.takeRequest().getHeader("Authorization"))
        assertNull(withTimeoutOrNull(100) { eventos.sesionCerrada.first() })
    }

    @Test
    fun refreshFallido_cierraLaSesion() = runTest {
        sesion.guardarTokens("vencido", "refresh-vencido")
        sesion.guardarUsuario(USUARIO)
        responder(401, TOKEN_INVALIDO)
        responder(401, TOKEN_INVALIDO)

        val r = repositorio.usuarioActual()

        assertTrue((r as Resultado.Fallo).error is ErrorAuth.SesionExpirada)
        assertEquals(2, servidor.requestCount)
        assertNull(sesion.access())
        assertNull(sesion.refresh())
        assertNull(sesion.usuario.first())
        val evento = withTimeout(1_000) { eventos.sesionCerrada.first() }
        assertEquals("token_not_valid", evento.error?.code)
    }

    @Test
    fun refreshRechazadoPorBloqueo_cierraLaSesionConElCode() = runTest {
        sesion.guardarTokens("vencido", "refresh-1")
        responder(401, TOKEN_INVALIDO)
        responder(
            403,
            """{"detail":"Tu cuenta está bloqueada hasta 2026-10-20 08:30.","code":"usuario_bloqueado",""" +
                """"bloqueado_hasta":"2026-10-20T08:30:00-05:00"}""",
        )

        repositorio.usuarioActual()

        assertNull(sesion.access())
        val evento = withTimeout(1_000) { eventos.sesionCerrada.first() }
        assertEquals("usuario_bloqueado", evento.error?.code)
        assertEquals("2026-10-20T08:30:00-05:00", evento.error?.bloqueadoHasta)
    }

    @Test
    fun soloRenuevaUnaVez() = runTest {
        sesion.guardarTokens("vencido", "refresh-1")
        responder(401, TOKEN_INVALIDO)
        responder(200, """{"access":"access-2"}""")
        responder(401, TOKEN_INVALIDO)

        val r = repositorio.usuarioActual()

        assertTrue((r as Resultado.Fallo).error is ErrorAuth.SesionExpirada)
        assertEquals(3, servidor.requestCount)
        assertNull(sesion.access())
        withTimeout(1_000) { eventos.sesionCerrada.first() }
    }

    // --- Cambio de contraseña y cierre ---

    @Test
    fun cambiarContrasena_guardaLosTokensNuevos() = runTest {
        sesion.guardarTokens("access-1", "refresh-1")
        sesion.guardarUsuario(USUARIO.copy(debeCambiarContrasena = true))
        responder(200, """{"access":"access-2","refresh":"refresh-2"}""")

        val r = repositorio.cambiarContrasena("temporal-1", "nueva-clave-9")

        assertTrue(r is Resultado.Exito)
        assertEquals("access-2", sesion.access())
        assertEquals("refresh-2", sesion.refresh())
        assertEquals(false, sesion.usuario.first()?.debeCambiarContrasena)
        val peticion = servidor.takeRequest()
        assertEquals("/api/auth/cambiar-contrasena/", peticion.path)
        assertEquals(
            """{"contrasena_actual":"temporal-1","contrasena_nueva":"nueva-clave-9"}""",
            peticion.body.readUtf8(),
        )
    }

    @Test
    fun cambiarContrasenaActualIncorrecta_devuelveElCampo() = runTest {
        sesion.guardarTokens("access-1", "refresh-1")
        responder(400, """{"contrasena_actual":["La contraseña actual no es correcta."]}""")

        val error = (repositorio.cambiarContrasena("mala", "nueva-clave-9") as Resultado.Fallo).error

        assertEquals(
            listOf("La contraseña actual no es correcta."),
            (error as ErrorAuth.Validacion).campos["contrasena_actual"],
        )
        assertEquals("access-1", sesion.access())
    }

    @Test
    fun cerrarSesion_borraTodo() = runTest {
        sesion.guardarTokens("access-1", "refresh-1")
        sesion.guardarUsuario(USUARIO)

        repositorio.cerrarSesion()

        assertNull(sesion.access())
        assertNull(sesion.usuario.first())
        assertEquals(false, sesion.haySesion.first())
    }

    // --- Avisos push (RN-26) ---

    @Test
    fun login_registraElCelularConElTokenDeLaSesion() = runTest {
        tokenCelular = "fcm-1"
        responder(200, LOGIN_OK)
        responder(200, yo(debeCambiar = false))
        servidor.enqueue(MockResponse().setResponseCode(204))

        val r = repositorio.login("ana@correo.co", "clave-segura-1")

        assertTrue(r is Resultado.Exito)
        servidor.takeRequest()
        servidor.takeRequest()
        val registro = servidor.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/notificaciones/dispositivos/", registro.path)
        assertEquals("POST", registro.method)
        assertEquals("""{"token":"fcm-1"}""", registro.body.readUtf8())
        assertEquals("Bearer access-1", registro.getHeader("Authorization"))
    }

    @Test
    fun login_siRegistrarElCelularFalla_entraIgual() = runTest {
        tokenCelular = "fcm-1"
        responder(200, LOGIN_OK)
        responder(200, yo(debeCambiar = false))
        responder(500, """{"detail":"Error"}""")

        val r = repositorio.login("ana@correo.co", "clave-segura-1")

        assertTrue(r is Resultado.Exito)
        servidor.takeRequest()
        servidor.takeRequest()
        assertEquals("/api/notificaciones/dispositivos/", servidor.takeRequest(5, TimeUnit.SECONDS)!!.path)
        assertEquals("access-1", sesion.access())
    }

    @Test
    fun login_sinTokenDeFirebase_noLlamaAlBackend() = runTest {
        responder(200, LOGIN_OK)
        responder(200, yo(debeCambiar = false))

        assertTrue(repositorio.login("ana@correo.co", "clave-segura-1") is Resultado.Exito)

        servidor.takeRequest()
        servidor.takeRequest()
        assertNull(servidor.takeRequest(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun loginDeAdministrador_noRegistraElCelular() = runTest {
        tokenCelular = "fcm-1"
        responder(200, LOGIN_OK)
        responder(200, yo(debeCambiar = false).replace("ciudadano", "administrador"))

        assertTrue(repositorio.login("admin@correo.co", "clave") is Resultado.Exito)

        servidor.takeRequest()
        servidor.takeRequest()
        assertNull(servidor.takeRequest(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun registrarDispositivo_conElTokenNuevoDeFirebase() = runTest {
        sesion.guardarTokens("access-1", "refresh-1")
        servidor.enqueue(MockResponse().setResponseCode(204))

        repositorio.registrarDispositivo("fcm-nuevo")

        val registro = servidor.takeRequest()
        assertEquals("/api/notificaciones/dispositivos/", registro.path)
        assertEquals("""{"token":"fcm-nuevo"}""", registro.body.readUtf8())
    }

    @Test
    fun registrarDispositivo_sinSesion_noLlama() = runTest {
        repositorio.registrarDispositivo("fcm-nuevo")

        assertEquals(0, servidor.requestCount)
    }

    // runBlocking: con runTest el límite de tiempo de cerrarSesion correría en tiempo virtual
    @Test
    fun cerrarSesion_quitaElCelularAntesDeBorrarLaSesion() = runBlocking {
        tokenCelular = "fcm-1"
        sesion.guardarTokens("access-1", "refresh-1")
        sesion.guardarUsuario(USUARIO)
        servidor.enqueue(MockResponse().setResponseCode(204))

        repositorio.cerrarSesion()

        val quitar = servidor.takeRequest()
        assertEquals("/api/notificaciones/dispositivos/quitar/", quitar.path)
        assertEquals("POST", quitar.method)
        assertEquals("""{"token":"fcm-1"}""", quitar.body.readUtf8())
        // Fue con la sesión todavía guardada
        assertEquals("Bearer access-1", quitar.getHeader("Authorization"))
        assertNull(sesion.access())
        assertNull(sesion.usuario.first())
    }

    @Test
    fun cerrarSesion_siQuitarElCelularFalla_borraIgual() = runBlocking {
        tokenCelular = "fcm-1"
        sesion.guardarTokens("access-1", "refresh-1")
        responder(500, """{"detail":"Error"}""")

        repositorio.cerrarSesion()

        assertEquals(1, servidor.requestCount)
        assertNull(sesion.access())
    }

    @Test
    fun cerrarSesion_sinConexion_borraIgual() = runBlocking {
        tokenCelular = "fcm-1"
        sesion.guardarTokens("access-1", "refresh-1")
        servidor.shutdown()

        repositorio.cerrarSesion()

        assertNull(sesion.access())
    }

    @Test
    fun cerrarSesion_siElBackendTarda_noEsperaMasDelLimite() = runTest {
        // En runTest el límite de 5 s pasa en tiempo virtual: la respuesta de 30 s no se espera
        tokenCelular = "fcm-1"
        sesion.guardarTokens("access-1", "refresh-1")
        servidor.enqueue(MockResponse().setResponseCode(204).setHeadersDelay(30, TimeUnit.SECONDS))

        repositorio.cerrarSesion()

        assertNull(sesion.access())
    }

    @Test
    fun sinConexion() = runTest {
        servidor.shutdown()

        val error = (repositorio.login("a@b.co", "x") as Resultado.Fallo).error

        assertTrue(error is ErrorAuth.SinConexion)
    }

    private fun responder(codigo: Int, cuerpo: String) {
        servidor.enqueue(
            MockResponse().setResponseCode(codigo)
                .setHeader("Content-Type", "application/json")
                .setBody(cuerpo)
        )
    }

    private fun yo(debeCambiar: Boolean) =
        """{"id":7,"nombre":"Ana","apellido":"Pérez","correo":"ana@correo.co","telefono":null,""" +
            """"rol":"ciudadano","estado":"activo","fecha_registro":"2026-10-01T10:00:00-05:00",""" +
            """"debe_cambiar_contrasena":$debeCambiar}"""

    private companion object {
        const val LOGIN_OK = """{"access":"access-1","refresh":"refresh-1","debe_cambiar_contrasena":false}"""
        const val TOKEN_INVALIDO =
            """{"detail":"Token is invalid or expired","code":"token_not_valid"}"""
        val USUARIO = UsuarioActual(
            id = 7, nombre = "Ana", apellido = "Pérez", correo = "ana@correo.co", rol = Rol.CIUDADANO,
            estado = EstadoUsuario.ACTIVO, fechaRegistro = "2026-10-01T10:00:00-05:00",
            debeCambiarContrasena = false,
        )
    }
}
