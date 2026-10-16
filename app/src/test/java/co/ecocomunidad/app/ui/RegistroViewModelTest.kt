package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.modelo.RegistroPeticion
import co.ecocomunidad.app.ui.RepositorioFalso.Companion.fallo
import co.ecocomunidad.app.ui.navegacion.Ruta
import co.ecocomunidad.app.ui.registro.CampoRegistro
import co.ecocomunidad.app.ui.registro.RegistroViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class RegistroViewModelTest {

    @get:Rule
    val main = ReglaMain()

    private val repositorio = RepositorioFalso()

    private fun viewModelLleno(
        telefono: String = "",
        contrasena: String = "clave-segura-1",
        confirmar: String = contrasena,
    ) = RegistroViewModel(repositorio).apply {
        cambiar(CampoRegistro.NOMBRE, " Ana ")
        cambiar(CampoRegistro.APELLIDO, "Pérez")
        cambiar(CampoRegistro.CEDULA, "1020304050")
        cambiar(CampoRegistro.CORREO, " ana@correo.co ")
        cambiar(CampoRegistro.TELEFONO, telefono)
        cambiar(CampoRegistro.CONTRASENA, contrasena)
        cambiar(CampoRegistro.CONFIRMAR, confirmar)
    }

    @Test
    fun camposVacios_noLlamaAlBackend() = runTest(main.despachador) {
        val vm = RegistroViewModel(repositorio)

        vm.registrar()
        advanceUntilIdle()

        val errores = vm.estado.value.errores
        assertEquals("Escribe tu nombre.", errores[CampoRegistro.NOMBRE])
        assertEquals("Escribe tu apellido.", errores[CampoRegistro.APELLIDO])
        assertEquals("Escribe tu cédula.", errores[CampoRegistro.CEDULA])
        assertEquals("Escribe tu correo.", errores[CampoRegistro.CORREO])
        assertEquals("Escribe una contraseña.", errores[CampoRegistro.CONTRASENA])
        assertEquals("Repite la contraseña.", errores[CampoRegistro.CONFIRMAR])
        // El teléfono es opcional en el backend
        assertNull(errores[CampoRegistro.TELEFONO])
        assertTrue(repositorio.registros.isEmpty())

        vm.cambiar(CampoRegistro.NOMBRE, "A")
        assertNull(vm.estado.value.errores[CampoRegistro.NOMBRE])
    }

    @Test
    fun confirmacionDistinta_noLlamaAlBackend() = runTest(main.despachador) {
        val vm = viewModelLleno(confirmar = "otra-clave")

        vm.registrar()
        advanceUntilIdle()

        assertEquals("Las contraseñas no coinciden.", vm.estado.value.errores[CampoRegistro.CONFIRMAR])
        assertTrue(repositorio.registros.isEmpty())
    }

    @Test
    fun exito_enviaLosCamposDelBackendYVuelveAlLoginConElCorreo() = runTest(main.despachador) {
        val vm = viewModelLleno(telefono = " 3001234567 ")

        vm.registrar()
        advanceUntilIdle()

        assertEquals(
            listOf(
                RegistroPeticion(
                    nombre = "Ana",
                    apellido = "Pérez",
                    cedula = "1020304050",
                    correo = "ana@correo.co",
                    password = "clave-segura-1",
                    telefono = "3001234567",
                ),
            ),
            repositorio.registros,
        )
        assertEquals(Ruta.Login(correo = "ana@correo.co", cuentaCreada = true), vm.estado.value.destino)
        // No inicia sesión automáticamente
        assertTrue(repositorio.logins.isEmpty())

        vm.destinoAtendido()
        assertNull(vm.estado.value.destino)
    }

    @Test
    fun sinTelefono_noSeEnvia() = runTest(main.despachador) {
        val vm = viewModelLleno(telefono = "  ")

        vm.registrar()
        advanceUntilIdle()

        assertNull(repositorio.registros.single().telefono)
    }

    @Test
    fun erroresDelBackend_vanDebajoDeSuCampo() = runTest(main.despachador) {
        repositorio.resultadoRegistro = fallo(
            ErrorAuth.Validacion(
                mapOf(
                    "cedula" to listOf("Ya existe un usuario con esta cédula."),
                    "correo" to listOf("Ya existe un usuario con este correo."),
                    "password" to listOf(
                        "La contraseña es demasiado similar a correo.",
                        "Esta contraseña es demasiado común.",
                    ),
                ),
            ),
        )
        val vm = viewModelLleno()

        vm.registrar()
        advanceUntilIdle()

        with(vm.estado.value) {
            assertEquals("Ya existe un usuario con esta cédula.", errores[CampoRegistro.CEDULA])
            assertEquals("Ya existe un usuario con este correo.", errores[CampoRegistro.CORREO])
            assertEquals(
                "La contraseña es demasiado similar a correo.\nEsta contraseña es demasiado común.",
                errores[CampoRegistro.CONTRASENA],
            )
            assertNull(aviso)
            assertNull(destino)
            assertFalse(cargando)
        }
    }

    @Test
    fun erroresDelBackendSinCampo_vanAlAviso() = runTest(main.despachador) {
        repositorio.resultadoRegistro = fallo(
            ErrorAuth.Validacion(mapOf("non_field_errors" to listOf("No se pudo crear la cuenta."))),
        )
        val vm = viewModelLleno()

        vm.registrar()
        advanceUntilIdle()

        assertEquals("No se pudo crear la cuenta.", vm.estado.value.aviso)
        assertTrue(vm.estado.value.errores.isEmpty())
    }

    @Test
    fun sinConexion_muestraAviso() = runTest(main.despachador) {
        repositorio.resultadoRegistro = fallo(ErrorAuth.SinConexion(IOException()))
        val vm = viewModelLleno()

        vm.registrar()
        advanceUntilIdle()

        assertEquals(
            "No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.",
            vm.estado.value.aviso,
        )
        assertFalse(vm.estado.value.cargando)
    }

    @Test
    fun mientrasCarga_noEnviaDosVecesYTrasUnSegundoMuestraBrotico() = runTest(main.despachador) {
        val vm = viewModelLleno()
        val espera = CompletableDeferred<Unit>()
        repositorio.esperaRegistro = espera

        vm.registrar()
        vm.registrar()
        advanceTimeBy(1_001)

        assertTrue(vm.estado.value.cargando)
        assertTrue(vm.estado.value.cargaLarga)
        assertEquals(1, repositorio.registros.size)

        espera.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.estado.value.cargaLarga)
    }
}
