# Pantallas por rehacer con Views

Las pantallas se hicieron primero en Jetpack Compose y se borraron al cambiar a Views con XML. Aquí queda lo que hacía cada una para rehacerlas igual. La lógica no se perdió: los ViewModels, las rutas, los mensajes y sus pruebas siguen en `ui/`.

Lo que sigue vigente y se reutiliza tal cual:

| Pieza | Archivo | Qué hace |
| --- | --- | --- |
| Pantalla inicial y cierres | `ui/navegacion/AppViewModel.kt` | `inicio` (null mientras lee la sesión) y `cierres` (motivo para volver al login) |
| Destinos | `ui/navegacion/Rutas.kt` | `Ruta.Login(motivo)`, `Registro`, `CambiarContrasena`, `InicioCiudadano`, `InicioGestor` y `destinoPara(rol, debeCambiarContrasena)` |
| Textos de error | `ui/navegacion/Mensajes.kt` | `mensajeDeError`, `mensajeDeCierre`, `formatearFechaColombia`, `Mensajes.PANEL_WEB`, `Mensajes.SESION_TERMINADA` |
| Login | `ui/login/LoginViewModel.kt` | Estado `LoginEstado`, `entrar()`, `destinoAtendido()` |
| Cambio de contraseña | `ui/contrasena/CambiarContrasenaViewModel.kt` | Estado `CambiarContrasenaEstado`, `guardar()`, `cerrarSesion()`, `destinoAtendido()` |
| Inicio | `ui/inicio/InicioViewModel.kt` | `usuario` (StateFlow) y `cerrarSesion(alTerminar)` |
| Contenedor | `EcoComunidadApp.kt` | `AppContainer` con `authRepository` y `eventosSesion` |

Drawables que ya existen: `logo`, `brotico_saluda`, `brotico_piensa` e íconos Lucide `ic_ojo`, `ic_ojo_tachado`, `ic_salir`, `ic_alerta` (se tiñen; vienen en negro).

## Navegación (antes `NavegacionApp.kt`)

- Mientras `AppViewModel.inicio` es null: pantalla de arranque con fondo `fondo` y el `logo` centrado a 96 dp (descripción "EcoComunidad").
- Luego arranca en la ruta que diga `inicio`.
- Escucha `AppViewModel.cierres`: con cada motivo navega a `Login(motivo)` **limpiando toda la pila** (`popUpTo` del grafo, inclusivo, y `launchSingleTop`).
- Toda navegación entre Login, CambiarContrasena e Inicio limpia la pila; solo Registro se apila sobre Login (atrás vuelve al login).
- Cada pantalla tiene su propio ViewModel creado con `authRepository` del contenedor. El de Login recibe el `motivo` de la ruta.
- Las pantallas observan `estado.destino`: si no es null, llaman a `destinoAtendido()` y navegan.

## Login (antes `LoginPantalla.kt`)

Disposición, de arriba abajo, con desplazamiento vertical, ajuste al teclado y márgenes de 20 dp:

1. Logo animado de 96 dp.
2. Nombre de marca: "Eco" en `verde_musgo` peso 500 y "Comunidad" en `verde_bosque` peso 700, Bricolage Grotesque.
3. Tarjeta clara (ancho máximo 480 dp, blanca, esquinas de 24 dp, borde 1 dp `borde`, sin sombra, relleno 24 dp, separación 16 dp) con:
   - Título "Inicia sesión" en `verde_bosque`.
   - Aviso (si `estado.aviso` no es null): ver componente Aviso.
   - Campo "Correo" (teclado de correo, acción Siguiente, error `estado.errorCorreo`).
   - Campo "Contraseña" con botón de ojo (acción Listo: quita el foco y llama a `entrar()`; error `estado.errorContrasena`).
   - Botón "Entrar" (quita el foco y llama a `entrar()`).
   - Si `estado.cargaLarga`: fila con **Brotico piensa** (56 dp, decorativo) y el texto "Revisando tus datos…" en `tinta_suave`.
4. Debajo de la tarjeta: "¿No tienes cuenta?" en `tinta_suave` y el botón de texto "Regístrate" en `verde_bosque`, que abre Registro.

Estados:

- Mientras `estado.cargando`: campos y "Regístrate" deshabilitados; el botón deshabilitado muestra un indicador circular pequeño (18 dp) en lugar del texto.
- Los errores van sin Brotico (marca.md: nunca en errores graves).

Mensajes que produce el ViewModel (no hay que reescribirlos, salen de `Mensajes.kt`):

| Caso | Texto |
| --- | --- |
| Correo vacío | Escribe tu correo. |
| Contraseña vacía | Escribe tu contraseña. |
| usuario_inactivo | Tu cuenta está inactiva y no puede iniciar sesión. |
| usuario_bloqueado | Tu cuenta está bloqueada hasta el 20 de octubre de 2026 a las 8:30 a. m. (fecha de `bloqueado_hasta` en hora de Colombia) |
| usuario_bloqueado sin fecha | Tu cuenta está bloqueada por ahora. |
| usuario_bloqueado_permanente | Tu cuenta está bloqueada de forma permanente. |
| Credenciales incorrectas (401) | El correo o la contraseña no son correctos. |
| Sin conexión | No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo. |
| Otro error del servidor | El servidor no respondió bien. Inténtalo en unos minutos. |
| Rol administrador | El administrador usa el panel web. (se cierra la sesión y no entra) |
| Sesión vencida | Tu sesión terminó. Inicia sesión de nuevo. |

### Animación del logo (marca.md, solo en el login)

Se dibujaba el logo por partes, con los trazos de `docs/marca/logo.svg` (viewBox 64):

| Paso | Inicio | Duración | Curva | Qué pasa |
| --- | --- | --- | --- | --- |
| Losas | 0,1 s | 0,45 s | ease-out `(0, 0, .58, 1)` | La izquierda va de translateX(2) a 0 y la derecha de -2 a 0 |
| Tallo | 0,45 s | 0,55 s | ease `(.25, .1, .25, 1)` | Se dibuja el trazo (grosor 4, punta redonda) de 0 a su largo total |
| Hoja izquierda | 0,9 s | 0,45 s | `(.34, 1.56, .64, 1)` | Escala de 0 a 1 con origen en (32, 35) |
| Hoja derecha | 1,1 s | 0,45 s | `(.34, 1.56, .64, 1)` | Escala de 0 a 1 con origen en (32, 29) |
| Nombre | 1,45 s | 0,4 s | ease | Opacidad 0 → 1 y sube 6 dp |
| Formulario | 1,7 s | 0,4 s | ease | Opacidad 0 → 1 y sube 6 dp |

- Fin total: 2,1 s.
- Se ve **una sola vez por apertura de la app** (bandera en memoria). Si se vuelve al login, el logo aparece terminado.
- Si la escala de animaciones del sistema es 0 (`Settings.Global.ANIMATOR_DURATION_SCALE`), se muestra el logo terminado sin animación.
- Trazos usados:
  - Losa izquierda: `M8 44 H30 L33 49 L29 53 L32 58 H8 Q4 58 4 54 V48 Q4 44 8 44 Z` (`pavimento`)
  - Losa derecha: `M34 44 H56 Q60 44 60 48 V54 Q60 58 56 58 H36 L33 53 L37 49 Z` (`pavimento`)
  - Tallo: `M33 47 C33 39 31 35 32 27` (`verde_bosque`, trazo)
  - Hoja izquierda: `M32 35 C24 35 18 30 18 22 C26 22 32 27 32 35 Z` (`verde_hoja`)
  - Hoja derecha: `M32 29 C32 21 38 15 46 15 C46 23 40 29 32 29 Z` (`verde_bosque`)
- Con Views se puede hacer con una `View` propia que dibuje estos trazos en `onDraw` (o con `AnimatedVectorDrawable` y `trimPathEnd` para el tallo).

## Cambiar contraseña (antes `CambiarContrasenaPantalla.kt`)

Obligatoria cuando `debe_cambiar_contrasena` es true (RN-05). Atrás sale de la app (es la única pantalla de la pila).

1. Barra superior: logo de 36 dp y el nombre de marca en tamaño título, separados 8 dp.
2. Tarjeta clara (ancho máximo 480 dp) con:
   - Título "Cambia tu contraseña" en `verde_bosque`.
   - Texto "Tu contraseña es temporal. Antes de continuar, escribe una nueva que solo tú conozcas." en `tinta_suave`.
   - Aviso (si `estado.aviso`).
   - Campos con ojo: "Contraseña actual" (`errorActual`), "Contraseña nueva" (`errorNueva`), "Repite la contraseña nueva" (`errorConfirmar`, acción Listo llama a `guardar()`).
   - Botón "Guardar contraseña".
3. Debajo: botón de texto "Cerrar sesión" en `verde_bosque` (llama a `cerrarSesion()` y vuelve al login).

- Mientras `estado.cargando`: todo deshabilitado y el botón con indicador.
- Validación local solo de vacíos y de que coincidan: "Escribe tu contraseña actual.", "Escribe la contraseña nueva.", "Repite la contraseña nueva.", "Las contraseñas nuevas no coinciden."
- Los mensajes del backend (RN-03) se muestran tal cual debajo de su campo, uno por línea; los que no son de un campo van en el aviso.
- Al terminar va al inicio del rol.

## Inicio de ciudadano y de gestor (antes `InicioPantalla.kt`)

Pantalla temporal, la misma para los dos roles; solo cambia la etiqueta ("Ciudadano" o "Gestor ambiental").

1. Barra superior (ancho máximo 560 dp): logo con nombre a la izquierda; a la derecha botón de texto con el ícono `ic_salir` (18 dp, `verde_bosque`) y "Cerrar sesión". Al pulsarlo: `cerrarSesion { ir a Login() }`.
2. Tarjeta **destacada en verde bosque** (esquinas de 24 dp, relleno 24 dp, contenido centrado, separación 8 dp):
   - **Brotico saluda** de 140 dp (descripción "Brotico saludando").
   - "¡Hola de nuevo, {nombre}!" (o "¡Hola de nuevo!" sin nombre) en blanco, título grande.
   - "Tu barrio te está esperando." en `verde_brote`.
   - Etiqueta en píldora con el rol: fondo `verde_brote`, texto `verde_bosque`.

## Registro (antes `RegistroPantalla.kt`)

Pantalla vacía: "Registro (próximo paso)" en `verde_bosque` y el botón de texto "Volver a iniciar sesión", que regresa al login.

## Componentes comunes (antes `ui/componentes/`)

- **Campo de texto**: contorno con esquinas de 16 dp, fondo blanco, borde `borde` y `verde_bosque` con foco, `arcilla` con error; etiqueta flotante; error como texto de ayuda debajo; una sola línea.
- **Campo de contraseña**: igual, con botón de ojo al final (`ic_ojo` / `ic_ojo_tachado` en `tinta_suave`, descripciones "Mostrar contraseña" / "Ocultar contraseña"). Con Material Components: `TextInputLayout` con `endIconMode="password_toggle"` y esos íconos.
- **Botón principal**: píldora `verde_bosque`, texto blanco, alto 52 dp, ancho completo.
- **Aviso**: fila con `ic_alerta` (20 dp, `arcilla`) y el texto en `tinta`, fondo `arcilla` al 8 %, esquinas de 16 dp, relleno 14 × 12 dp. Se anuncia a lectores de pantalla (región en vivo, cortés).
- **Tarjeta clara**: blanca, esquinas de 24 dp, borde 1 dp `borde`, sin sombra.
