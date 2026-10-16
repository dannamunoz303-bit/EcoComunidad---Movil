package co.ecocomunidad.app.ui.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import co.ecocomunidad.app.R
import co.ecocomunidad.app.ui.theme.Arcilla
import co.ecocomunidad.app.ui.theme.Borde
import co.ecocomunidad.app.ui.theme.Superficie
import co.ecocomunidad.app.ui.theme.Tinta
import co.ecocomunidad.app.ui.theme.TintaSuave
import co.ecocomunidad.app.ui.theme.VerdeBosque

private val formaCampo = RoundedCornerShape(16.dp)
private val formaPildora = RoundedCornerShape(percent = 50)

/** Tarjeta clara: superficie blanca, borde de marca, sin sombra. */
@Composable
fun TarjetaClara(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Superficie,
        border = BorderStroke(1.dp, Borde),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = contenido,
        )
    }
}

@Composable
fun CampoTexto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    error: String?,
    modifier: Modifier = Modifier,
    tipoTeclado: KeyboardType = KeyboardType.Text,
    accion: ImeAction = ImeAction.Next,
    alAccion: () -> Unit = {},
    habilitado: Boolean = true,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        enabled = habilitado,
        keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado, imeAction = accion),
        keyboardActions = KeyboardActions(onAny = { alAccion() }),
        shape = formaCampo,
        colors = coloresCampo(),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun CampoContrasena(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    error: String?,
    modifier: Modifier = Modifier,
    accion: ImeAction = ImeAction.Next,
    alAccion: () -> Unit = {},
    habilitado: Boolean = true,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        enabled = habilitado,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = accion),
        keyboardActions = KeyboardActions(onAny = { alAccion() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painterResource(if (visible) R.drawable.ic_ojo_tachado else R.drawable.ic_ojo),
                    contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                    tint = TintaSuave,
                )
            }
        },
        shape = formaCampo,
        colors = coloresCampo(),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun coloresCampo() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VerdeBosque,
    unfocusedBorderColor = Borde,
    focusedLabelColor = VerdeBosque,
    cursorColor = VerdeBosque,
    errorBorderColor = Arcilla,
    errorLabelColor = Arcilla,
    errorSupportingTextColor = Arcilla,
    focusedContainerColor = Superficie,
    unfocusedContainerColor = Superficie,
    errorContainerColor = Superficie,
)

/** Botón principal en píldora verde bosque. Mientras [cargando], queda deshabilitado con un indicador. */
@Composable
fun BotonPrincipal(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    cargando: Boolean = false,
) {
    Button(
        onClick = alPulsar,
        enabled = !cargando,
        shape = formaPildora,
        colors = ButtonDefaults.buttonColors(containerColor = VerdeBosque, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        if (cargando) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = TintaSuave,
            )
        } else {
            Text(texto, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Mensaje sobrio con el ícono de alerta (errores y motivos de cierre, sin Brotico). */
@Composable
fun Aviso(texto: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Arcilla.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painterResource(R.drawable.ic_alerta),
            contentDescription = null,
            tint = Arcilla,
            modifier = Modifier.size(20.dp),
        )
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Tinta)
    }
}
