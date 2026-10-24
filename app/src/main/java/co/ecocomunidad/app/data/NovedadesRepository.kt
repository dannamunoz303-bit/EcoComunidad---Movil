package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.NuevaNovedad
import co.ecocomunidad.app.data.modelo.Novedad

/**
 * Novedades del ciudadano y del gestor (RN-18). Cada novedad la ven solo quien la creó y el administrador:
 * eso lo decide el backend. La implementación real es [NovedadesRepositoryRemoto].
 */
interface NovedadesRepository {

    /** Las novedades que creó el usuario con sesión. Sin paginación. */
    suspend fun listarMias(): Resultado<List<Novedad>>

    /** Los 400 llegan por campo en [ErrorAuth.Validacion] (por ejemplo, "reporte" si ya no se permite). */
    suspend fun crear(nueva: NuevaNovedad): Resultado<Novedad>
}
