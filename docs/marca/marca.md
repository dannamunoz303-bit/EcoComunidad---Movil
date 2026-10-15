# Marca EcoComunidad (panel web del administrador)

Fuente de verdad del diseño del frontend. Si algo no está aquí, preguntar antes de inventarlo.

## Logo

- El logo es **el brote en la grieta**: dos losas de pavimento separadas por una grieta en zigzag, y de la grieta sale un brote de dos hojas. No tiene cara.
- Archivos (en esta carpeta):
  - `logo.svg`: versión principal, sobre fondos claros.
  - `logo-sobre-verde.svg`: sobre el verde oscuro `#1E5A3A`.
  - `logo-un-color.svg`: un solo color (`currentColor`), para impresión o sellos.
  - `favicon.svg`: versión con trazos más gruesos para la pestaña del navegador (20–32 px).
- Junto al nombre: el logo a la izquierda y el texto **EcoComunidad** en Bricolage Grotesque 700, con "Eco" en `#3B7A55` peso 500 y "Comunidad" en `#1E5A3A` peso 700.
- Sobre el verde oscuro `#1E5A3A` (con `logo-sobre-verde.svg`): "Eco" en verde-brote `#A8D9B8` peso 500 y "Comunidad" en blanco `#FFFFFF` peso 700.
- No deformar, no rotar, no cambiar colores fuera de las tres versiones.

## Animación del logo (solo en el login, una vez)

Duración total cerca de 1,8 s. Orden, usando los trazos de `logo.svg`:

1. Las dos losas se separan: la izquierda parte de `translateX(2px)` y la derecha de `translateX(-2px)`, hasta 0. 0,45 s, con 0,1 s de espera, `ease-out`.
2. El tallo se dibuja (`pathLength="1"`, `stroke-dasharray: 1`, `stroke-dashoffset` de 1 a 0). 0,55 s, empieza a los 0,45 s.
3. La hoja izquierda crece desde `scale(0)` hasta 1 con origen en `32px 35px`. 0,45 s, empieza a los 0,9 s, `cubic-bezier(.34,1.56,.64,1)`.
4. La hoja derecha igual, con origen en `32px 29px`, empieza a los 1,1 s.
5. El nombre aparece (opacidad 0 → 1 y sube 6 px). 0,4 s, empieza a los 1,45 s.
6. El formulario aparece igual, empieza a los 1,7 s.

Usar `transform-box: view-box` en las hojas y las losas. Con `prefers-reduced-motion: reduce` no hay animación: se muestra el logo terminado.

## Brotico (personaje)

- Es la misma planta con carita. Aparece **solo en momentos especiales**, nunca en todas las pantallas. En el resto de la app manda el logo.
- Estados (archivos en esta carpeta):

| Estado | Archivo | Dónde se usa | Ejemplo de texto |
| --- | --- | --- | --- |
| Saluda | `brotico-saluda.svg` | Bienvenida tras iniciar sesión | ¡Hola de nuevo! Tu barrio te está esperando. |
| Se desanima | `brotico-triste.svg` | Listas vacías o sin resultados | No encontramos reportes con esos filtros. Prueba con otros. |
| Florece | `brotico-florece.svg` | Un reporte queda resuelto | ¡Un lugar más limpio! Gracias al trabajo del equipo. |
| Piensa | `brotico-piensa.svg` | Cargas largas (más de 1 s) | Revisando los reportes del barrio… |

- Nunca usar Brotico en mensajes de error graves (sesión vencida, sin permisos): ahí va un mensaje sobrio.

## Colores

| Uso | Nombre | Hex |
| --- | --- | --- |
| Verde principal (botones, barra lateral, títulos) | verde-bosque | `#1E5A3A` |
| Verde hoja (acentos, gráficas) | verde-hoja | `#3E9B63` |
| Verde texto suave ("Eco", etiquetas) | verde-musgo | `#3B7A55` |
| Verde claro (detalles sobre verde oscuro) | verde-brote | `#A8D9B8` |
| Cuerpo de Brotico | verde-brotico | `#5BB37E` |
| Fondo de la app | fondo | `#F3F6F1` |
| Tarjetas | superficie | `#FFFFFF` |
| Bordes | borde | `#DCE5DF` |
| Texto principal | tinta | `#14211A` |
| Texto secundario | tinta-suave | `#4F5E55` |
| Pavimento del logo | pavimento | `#3A4740` |
| Alerta / atrasado | ambar | `#B7791F` |
| Error / rechazado | arcilla | `#B4432B` |

- Sin degradados de fondo, sin morados ni azules llamativos.
- Contraste mínimo 4,5:1 para texto.

## Tipografía

- Títulos y números grandes: **Bricolage Grotesque** (500 y 700).
- Texto, formularios y tablas: **Instrument Sans** (400, 500 y 600).
- Las dos desde Google Fonts.

## Reglas para que no se vea hecho con plantilla

- Datos reales de la API, nunca textos de relleno.
- Textos en español, escritos para EcoComunidad ("Reportes por revisar", no "Overview").
- Íconos de una sola familia (Lucide), nunca emojis.
- No todas las tarjetas iguales: una destacada en verde bosque y las demás claras.
- Esquinas redondeadas generosas (tarjetas 20–24 px, botones en forma de píldora).
- Estados vacíos, de carga y de error pensados para cada pantalla.
