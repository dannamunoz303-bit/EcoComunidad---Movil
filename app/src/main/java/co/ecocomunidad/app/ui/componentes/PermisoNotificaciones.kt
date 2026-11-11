package co.ecocomunidad.app.ui.componentes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import co.ecocomunidad.app.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Permiso de notificaciones de Android 13 o superior (RN-26). Como con la ubicación en Crear reporte,
 * primero se explica para qué sirve y luego el sistema lo pide. Se pregunta una sola vez: si dice
 * "Ahora no" o lo niega, no se insiste (los avisos siguen en la campana y puede darlo en Ajustes).
 *
 * Se crea como propiedad del fragment, porque registerForActivityResult debe llamarse antes de que inicie.
 */
class PermisoNotificaciones(private val fragment: Fragment) {

    // El resultado no cambia nada en la pantalla: con o sin permiso, la app funciona igual
    private val pedir = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun pedirSiHaceFalta() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val contexto = fragment.requireContext()
        val concedido = ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (concedido) return
        val preferencias = contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)
        if (preferencias.getBoolean(YA_SE_PREGUNTO, false)) return
        preferencias.edit { putBoolean(YA_SE_PREGUNTO, true) }

        MaterialAlertDialogBuilder(contexto)
            .setTitle(R.string.permiso_notificaciones_titulo)
            .setMessage(R.string.permiso_notificaciones_explicacion)
            .setPositiveButton(R.string.permiso_permitir) { _, _ -> pedir.launch(Manifest.permission.POST_NOTIFICATIONS) }
            .setNegativeButton(R.string.permiso_ahora_no, null)
            .show()
    }

    private companion object {
        const val PREFERENCIAS = "avisos"
        const val YA_SE_PREGUNTO = "permiso_notificaciones_preguntado"
    }
}
