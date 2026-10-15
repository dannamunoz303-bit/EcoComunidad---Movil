package co.ecocomunidad.app.data.sesion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import co.ecocomunidad.app.data.modelo.EstadoUsuario
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.data.modelo.UsuarioActual
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Una sola instancia por proceso, como exige DataStore. */
val Context.sesionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sesion")

/** Datos básicos del usuario que se guardan con la sesión. */
data class UsuarioSesion(
    val id: Int,
    val nombre: String,
    val apellido: String,
    val correo: String,
    val rol: Rol,
    val estado: EstadoUsuario,
    val debeCambiarContrasena: Boolean,
)

class SesionStore(private val dataStore: DataStore<Preferences>) {

    val haySesion: Flow<Boolean> = dataStore.data.map { it[ACCESS] != null }

    val usuario: Flow<UsuarioSesion?> = dataStore.data.map { it.aUsuario() }

    suspend fun access(): String? = dataStore.data.first()[ACCESS]

    suspend fun refresh(): String? = dataStore.data.first()[REFRESH]

    suspend fun guardarTokens(access: String, refresh: String) {
        dataStore.edit {
            it[ACCESS] = access
            it[REFRESH] = refresh
        }
    }

    suspend fun guardarAccess(access: String) {
        dataStore.edit { it[ACCESS] = access }
    }

    suspend fun guardarUsuario(usuario: UsuarioActual) {
        dataStore.edit {
            it[ID] = usuario.id
            it[NOMBRE] = usuario.nombre
            it[APELLIDO] = usuario.apellido
            it[CORREO] = usuario.correo
            it[ROL] = usuario.rol.name
            it[ESTADO] = usuario.estado.name
            it[DEBE_CAMBIAR_CONTRASENA] = usuario.debeCambiarContrasena
        }
    }

    suspend fun marcarContrasenaCambiada() {
        dataStore.edit { it[DEBE_CAMBIAR_CONTRASENA] = false }
    }

    suspend fun borrar() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.aUsuario(): UsuarioSesion? {
        val id = this[ID] ?: return null
        return UsuarioSesion(
            id = id,
            nombre = this[NOMBRE].orEmpty(),
            apellido = this[APELLIDO].orEmpty(),
            correo = this[CORREO].orEmpty(),
            rol = Rol.valueOf(this[ROL] ?: return null),
            estado = EstadoUsuario.valueOf(this[ESTADO] ?: return null),
            debeCambiarContrasena = this[DEBE_CAMBIAR_CONTRASENA] ?: false,
        )
    }

    private companion object {
        val ACCESS = stringPreferencesKey("access")
        val REFRESH = stringPreferencesKey("refresh")
        val ID = intPreferencesKey("id")
        val NOMBRE = stringPreferencesKey("nombre")
        val APELLIDO = stringPreferencesKey("apellido")
        val CORREO = stringPreferencesKey("correo")
        val ROL = stringPreferencesKey("rol")
        val ESTADO = stringPreferencesKey("estado")
        val DEBE_CAMBIAR_CONTRASENA = booleanPreferencesKey("debe_cambiar_contrasena")
    }
}
