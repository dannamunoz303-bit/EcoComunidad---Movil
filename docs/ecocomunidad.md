# EcoComunidad: base de datos, flujo y reglas de negocio

Autor: Danna Sofia Muñoz Ramos

> Documento para desarrolladores y asistentes de IA. Es la fuente de verdad del proyecto: modelo de datos (MySQL), estados, reglas que debe aplicar el backend, permisos por rol y flujos completos. Si algo no está aquí, preguntar antes de inventarlo.

## 1. Qué es el proyecto

EcoComunidad es una aplicación para que los ciudadanos reporten problemas ambientales de su comunidad (basura acumulada, escombros, contaminación, árboles caídos, daños en zonas verdes) con fotografías y ubicación, y hagan seguimiento hasta que se resuelvan. Hay tres roles:

- **Ciudadano:** se registra, crea reportes, sube fotos, consulta el estado, ve notificaciones, crea novedades (quejas) y puede recibir sanciones.
- **Gestor ambiental:** atiende las tareas que se le asignan, registra acciones y evidencias, actualiza el estado de su tarea, ve notificaciones y crea novedades (pedir ayuda o avisar incidentes).
- **Administrador:** gestiona usuarios (crea gestores), categorías y reportes; revisa y rechaza reportes; asigna tareas a gestores; sanciona ciudadanos; atiende novedades y hace seguimiento a los gestores.

Flujo base: el ciudadano reporta → el administrador revisa y asigna a uno o más gestores → el gestor registra acciones y termina su tarea → el reporte queda resuelto.

## 2. Glosario

- **Reporte:** el problema ambiental que registra el ciudadano.
- **Tarea:** la asignación de un reporte a UN gestor. Un reporte puede tener varias tareas (una por gestor) cuando se necesita más de una persona.
- **Acción (accion\_tarea):** cada paso que el gestor registra mientras atiende su tarea.
- **Novedad:** aviso ligado a un reporte (y opcionalmente a una tarea). El gestor la usa para pedir ayuda o avisar que pasó algo; el ciudadano para quejarse del gestor o de la situación.
- **Sanción:** medida del administrador contra un ciudadano (advertencia, bloqueo temporal, bloqueo permanente).
- **Notificación:** mensaje interno para un usuario cuando ocurre un evento.
- **Eliminación lógica:** no se borra la fila; se marca (reporte.eliminado = TRUE) o se desactiva (usuario.estado = inactivo). Nunca borrar físicamente reportes, tareas ni usuarios.

## 3. Convenciones

- MySQL 8.0.16 o superior (por los CHECK), utf8mb4. Nombres en español, snake\_case, tablas en singular.
- Valores de ENUM en minúscula con guion bajo (en\_proceso).
- Todas las claves foráneas a usuario (ciudadano\_id, gestor\_id, asignado\_por, admin\_id, etc.) apuntan a la misma tabla; la base de datos NO verifica que el rol sea el correcto, eso lo valida el backend (ver reglas).
- Contraseñas siempre con hash. La cédula solo la ve el administrador.
- Todos los listados de la API se ordenan del más reciente al más antiguo, excepto las fotos dentro de un reporte o una acción, que van en el orden en que se subieron.
- Zona horaria: America/Bogota. En la base las fechas se guardan en UTC y se muestran en hora de Colombia.

## 4. Tablas

### 4.1 rol

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | INT | PK autoincremental |
| nombre | VARCHAR(30) | UNIQUE. Valores: ciudadano, gestor, administrador |

### 4.2 usuario

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | INT | PK |
| nombre | VARCHAR(80) | NOT NULL |
| apellido | VARCHAR(80) | NOT NULL |
| cedula | VARCHAR(20) | UNIQUE. Obligatoria para ciudadano y gestor (validar en backend); opcional para administrador |
| correo | VARCHAR(120) | UNIQUE, NOT NULL. Se usa para iniciar sesión |
| contrasena | VARCHAR(255) | NOT NULL. Solo el hash |
| telefono | VARCHAR(20) | Opcional |
| rol\_id | INT | FK → rol |
| estado | ENUM | activo (defecto), inactivo, bloqueado |
| bloqueado\_hasta | DATETIME | NULL. Con estado bloqueado: fin del bloqueo. Estado bloqueado con NULL = bloqueo permanente |
| debe\_cambiar\_contrasena | BOOLEAN | Defecto FALSE. TRUE cuando un administrador crea el usuario o le restablece la contraseña; mientras sea TRUE solo puede cambiarla (RN-05) |
| fecha\_registro | DATETIME | Defecto: ahora |

### 4.3 categoria

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | INT | PK |
| nombre | VARCHAR(80) | UNIQUE, NOT NULL |
| descripcion | VARCHAR(255) | Opcional |
| activa | BOOLEAN | Defecto TRUE. Las categorías no se borran, se desactivan |

### 4.4 reporte

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | INT | PK |
| ciudadano\_id | INT | FK → usuario (quien reporta) |
| categoria\_id | INT | FK → categoria |
| titulo | VARCHAR(150) | NOT NULL |
| descripcion | TEXT | NOT NULL. Descripción del problema |
| direccion | VARCHAR(255) | NOT NULL. Dirección del lugar |
| descripcion\_ubicacion | VARCHAR(255) | Referencias para ubicar el lugar |
| latitud | DECIMAL(10,8) | NULL en la base de datos, pero el backend la exige al crear el reporte (RN-06). Viene del GPS o del punto marcado en el mapa |
| longitud | DECIMAL(11,8) | Igual que latitud |
| estado | ENUM | pendiente (defecto), aprobado, asignado, en\_proceso, resuelto, rechazado |
| motivo\_rechazo | VARCHAR(255) | Obligatorio cuando estado = rechazado |
| eliminado | BOOLEAN | Defecto FALSE. Eliminación lógica |
| fecha\_creacion | DATETIME | Defecto: ahora |
| fecha\_actualizacion | DATETIME | Se actualiza sola en cada cambio |
| fecha\_revision | DATETIME | Última revisión del administrador: aprobación, rechazo o reapertura (RN-08, RN-16, RN-17) |
| revisado\_por | INT | FK → usuario (administrador que lo revisó) |

### 4.5 tarea

Una fila por gestor asignado a un reporte.

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | INT | PK |
| reporte\_id | INT | FK → reporte |
| gestor\_id | INT | FK → usuario (debe ser rol gestor) |
| asignado\_por | INT | FK → usuario (debe ser administrador) |
| estado | ENUM | asignada (defecto), en\_proceso, terminada, cancelada |
| fecha\_asignacion | DATETIME | Defecto: ahora |
| fecha\_fin | DATETIME | Se llena al pasar a terminada |
| fecha\_alerta | DATETIME | NULL. Fecha de la última alerta tarea\_sin\_acciones (sección 11) |

### 4.6 accion\_tarea

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | INT | PK |
| tarea\_id | INT | FK → tarea |
| descripcion | TEXT | Qué hizo el gestor en este paso |
| fecha | DATETIME | Defecto: ahora |

### 4.7 foto

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | INT | PK |
| reporte\_id | INT | FK → reporte. Siempre lleno |
| accion\_id | INT | FK → accion\_tarea. NULL si la foto es del ciudadano |
| url | VARCHAR(255) | URL del archivo (Cloudinary en producción; carpeta media local en desarrollo) |
| tipo | ENUM | reporte (la sube el ciudadano) o evidencia (la sube el gestor) |
| subida\_por | INT | FK → usuario |
| fecha | DATETIME | Defecto: ahora |

Regla de integridad (CHECK): tipo = reporte exige accion\_id NULL; tipo = evidencia exige accion\_id NOT NULL.

### 4.8 novedad

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | INT | PK |
| reporte\_id | INT | FK → reporte. Obligatorio |
| tarea\_id | INT | FK → tarea. Obligatorio si la crea un gestor (su tarea activa); opcional si la crea un ciudadano (por ejemplo, la queja sobre un gestor concreto). Se valida en el backend |
| creado\_por | INT | FK → usuario (ciudadano o gestor) |
| tipo | ENUM | ayuda, queja, incidente |
| descripcion | TEXT | NOT NULL |
| estado | ENUM | abierta (defecto), atendida |
| respuesta | TEXT | Respuesta del administrador |
| fecha | DATETIME | Defecto: ahora |

### 4.9 sancion

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | INT | PK |
| ciudadano\_id | INT | FK → usuario (debe ser ciudadano) |
| admin\_id | INT | FK → usuario (debe ser administrador) |
| reporte\_id | INT | FK → reporte. NULL si la sanción es por otro motivo |
| tipo | ENUM | advertencia, bloqueo\_temporal, bloqueo\_permanente |
| motivo | VARCHAR(255) | NOT NULL. El administrador siempre lo especifica |
| fecha\_inicio | DATETIME | Defecto: ahora |
| fecha\_fin | DATETIME | NULL en advertencia y bloqueo permanente. En bloqueo temporal = inicio + 2 días |

### 4.10 notificacion

| Campo | Tipo | Notas |
| --- | --- | --- |
| id | INT | PK |
| usuario\_id | INT | FK → usuario (destinatario) |
| tipo | VARCHAR(40) | Código del evento (ver sección 9) |
| mensaje | VARCHAR(255) | Texto para mostrar |
| leida | BOOLEAN | Defecto FALSE |
| fecha | DATETIME | Defecto: ahora |
| reporte\_id | INT | FK → reporte. Opcional |

## 5. Relaciones

- rol 1 → N usuario.
- usuario (ciudadano) 1 → N reporte. categoria 1 → N reporte.
- reporte 1 → N tarea (una por gestor). usuario (gestor) 1 → N tarea.
- tarea 1 → N accion\_tarea.
- reporte 1 → N foto. accion\_tarea 1 → N foto (solo evidencias).
- reporte 1 → N novedad. tarea 1 → N novedad (opcional).
- usuario (ciudadano) 1 → N sancion. reporte 1 → N sancion (opcional).
- usuario 1 → N notificacion.

No existe tabla de seguimiento del gestor: se calcula con consultas sobre tarea (sección 11).

## 6. Estados y transiciones

### 6.1 Estado del reporte

| Estado | Significado |
| --- | --- |
| pendiente | Recién creado, sin revisar. El ciudadano aún puede editarlo o eliminarlo |
| aprobado | El administrador lo revisó y es ambiental. No tiene tarea activa |
| asignado | Tiene al menos una tarea y ninguna en proceso |
| en\_proceso | Al menos una tarea en proceso |
| resuelto | Sin tareas activas y con al menos una terminada después de la última aprobación o reapertura (RN-16) |
| rechazado | No es un tema ambiental. El administrador puede devolverlo a pendiente para revisarlo otra vez (RN-08) |

Transiciones permitidas:

- pendiente → aprobado o rechazado, y rechazado → pendiente para una nueva revisión (administrador, a mano).
- aprobado → asignado (al crear la primera tarea).
- asignado → en\_proceso → resuelto (automático según las tareas); también en\_proceso → asignado, cuando se cancela la única tarea en proceso y queda otra asignada.
- asignado o en\_proceso → resuelto o aprobado, al quedar sin tareas activas (según RN-16).
- resuelto → aprobado (reapertura por el administrador tras una queja).

### 6.2 Estado de la tarea

asignada → en\_proceso → terminada. Desde asignada o en\_proceso se puede pasar a cancelada (reasignación, gestor inactivado o reporte eliminado).

### 6.3 Estado de la novedad

abierta → atendida (cuando el administrador responde).

### 6.4 Estado del usuario

activo, inactivo (gestor o ciudadano dado de baja; no puede iniciar sesión) y bloqueado (por sanción).

## 7. Reglas de negocio (backend)

### Usuarios y sesión

- **RN-01.** Solo el ciudadano se registra por sí mismo (necesita cédula). Los gestores los crea el administrador. El primer administrador se crea por consola con un comando de Django (sección 14); nunca desde el registro ni desde la API. Los demás administradores los crea un administrador existente.
- **RN-02.** La cédula es única y obligatoria para ciudadano y gestor. Nunca se muestra a otros usuarios; solo el administrador la ve.
- **RN-03.** La contraseña se guarda siempre con hash y se valida con los 4 validadores de Django: mínimo 8 caracteres, no común, no solo números y no parecida a los datos del usuario (correo, nombre, apellido).
- **RN-04. Login:** si estado = inactivo, negar. Si estado = bloqueado y bloqueado\_hasta es NULL, negar (permanente). Si estado = bloqueado y bloqueado\_hasta ya pasó, poner estado = activo y bloqueado\_hasta = NULL, y dejar entrar. Si todavía no pasó, negar y mostrar la fecha.
- **RN-05. Inactivar usuarios (gestor o ciudadano).** Si es un gestor, sus tareas asignada o en\_proceso pasan a cancelada, se recalcula el estado de cada reporte afectado (RN-16) y se notifica al administrador para reasignar. Sus tareas terminadas quedan como historial. Si es un ciudadano (solo lo inactiva el administrador), no puede iniciar sesión pero sus reportes siguen su curso y quedan en el historial; su cédula sigue reservada porque la fila no se borra. Mientras esté inactivo, las sanciones que reciba se registran pero no cambian su estado (RN-21). Al reactivarlo se calcula su estado: vuelve a activo, o a bloqueado si todavía tiene un bloqueo vigente (RN-21). No se pueden inactivar administradores. El rol de un usuario no se cambia. El administrador no elige ni conoce contraseñas definitivas de otros usuarios. Cada usuario puede cambiar su propia contraseña escribiendo la actual. El administrador puede restablecer la contraseña de otro usuario: el sistema genera una temporal que se muestra una sola vez y el usuario debe cambiarla al iniciar sesión; lo mismo aplica a los usuarios que crea el administrador. Mientras tenga que cambiarla, solo puede consultar sus datos y cambiar la contraseña. Inactivar a alguien que ya está inactivo, o reactivar a alguien que no lo está, responde error.

### Reportes

- **RN-06. Crear:** solo ciudadano activo. Estado inicial pendiente. La categoría debe estar activa y la dirección es obligatoria. Debe traer al menos una foto tipo reporte y las coordenadas (latitud y longitud), que el ciudadano no escribe: las toma la app del GPS o del pin que él mueve en el mapa, y el backend las valida (latitud entre -90 y 90, longitud entre -180 y 180; fuera de rango se rechaza el reporte); el reporte y sus fotos se guardan en una sola transacción, y si falta alguna de esas cosas no se crea nada. Mientras el reporte exista debe conservar al menos una foto tipo reporte. Si las coordenadas traen más de 8 decimales, se redondean a 8 antes de validar el rango.
- **RN-07. Editar o eliminar:** solo el ciudadano dueño y solo si estado = pendiente. Eliminar es lógico (eliminado = TRUE).
- **RN-08. Revisar:** solo el administrador. De pendiente pasa a aprobado o rechazado. Rechazar exige motivo\_rechazo (el motivo es que no tiene que ver con temas ambientales) y notifica al ciudadano. Se guardan revisado\_por y fecha\_revision en cada revisión (aprobación, rechazo o reapertura). Si el administrador se equivocó al rechazar, puede devolver el reporte a pendiente: se limpia motivo\_rechazo, se notifica al ciudadano y el reporte vuelve a ser editable por él (RN-07) hasta la próxima revisión. Devolver a pendiente no cambia revisado\_por ni fecha\_revision. Un reporte eliminado no se puede revisar.
- **RN-09. Administrador:** puede editar cualquier reporte y eliminarlo (lógico). Al eliminarlo, sus tareas activas pasan a cancelada sin recalcular el estado del reporte (RN-16 no se aplica a esta cancelación). El ciudadano recibe solo reporte\_eliminado y cada gestor afectado recibe tarea\_cancelada.
- **RN-10. Visibilidad en la opción de ver todos los reportes:** el ciudadano y el gestor ven de los demás solo reportes en estado aprobado, asignado, en\_proceso o resuelto (nunca pendientes ni rechazados de otros, nunca eliminados). Los propios los ve en cualquier estado. El administrador ve todos. El gestor además tiene su lista de reportes asignados (por sus tareas). El nombre de quien creó un reporte lo ven solo el dueño, los gestores y el administrador; los demás ciudadanos ven el reporte sin ese nombre.

### Tareas

- **RN-11. Asignar:** solo el administrador. El reporte debe estar aprobado, asignado o en\_proceso (esto último para sumar un segundo gestor). El gestor debe tener rol gestor y estado activo. Un mismo gestor no puede tener dos tareas activas (asignada o en\_proceso) sobre el mismo reporte.
- **RN-12. Varios gestores:** un reporte puede tener varias tareas, una por gestor, cuando se requiere más ayuda o el gestor la pide. Reasignar = cancelar la tarea del gestor actual y crear una nueva para otro gestor. Reasignar al mismo gestor que ya tiene la tarea responde error.
- **RN-13. Estado de la tarea:** solo el gestor dueño la cambia: asignada → en\_proceso → terminada. Al terminar se llena fecha\_fin. cancelada la pone el sistema o el administrador. Para terminar, la tarea debe tener al menos una acción con foto de evidencia.
- **RN-14. Acciones:** solo el gestor dueño y solo mientras la tarea esté asignada o en\_proceso. Las acciones y evidencias de un reporte las ven solo el ciudadano dueño, los gestores con tarea en ese reporte y el administrador. El ciudadano dueño ve el nombre del gestor de cada tarea.
- **RN-15. Fotos:** el ciudadano sube fotos tipo reporte a su reporte y puede borrarlas, solo mientras esté pendiente y sin dejar el reporte sin al menos una foto tipo reporte. El gestor sube fotos tipo evidencia ligadas a una de sus acciones (accion\_id obligatorio). Todas las fotos deben ser JPG, PNG o WEBP, de máximo 5 MB; máximo 5 fotos tipo reporte por reporte. Evidencias: solo con la tarea activa, máximo 5 por acción y no se pueden borrar; foto.reporte\_id se toma de la tarea de la acción.
- **RN-16. Sincronizar el estado del reporte.** El estado del reporte se guarda y se actualiza en cada evento de tarea (crear, iniciar, terminar, cancelar), dentro de una transacción. NO se calcula al vuelo, porque al reabrir un reporte sus tareas viejas terminadas lo harían parecer resuelto. Después de cada evento, mirando las tareas activas (asignada o en\_proceso):
  1. Si alguna está en\_proceso → reporte en\_proceso.
  2. Si no, y alguna está asignada → reporte asignado.
  3. Si no queda ninguna activa y existe al menos una tarea terminada asignada desde la última revisión del administrador (tarea.fecha\_asignacion mayor o igual que reporte.fecha\_revision) → reporte resuelto.
  4. Si no queda ninguna activa y no existe esa tarea terminada → reporte aprobado. Cada cambio notifica al ciudadano. El administrador cambia el estado a mano solo en estos casos: pendiente → aprobado o rechazado, rechazado → pendiente (RN-08) y resuelto → aprobado (RN-17); asignado, en\_proceso y resuelto son siempre automáticos.
- **RN-17. Reapertura:** si el reporte está resuelto y el ciudadano crea una novedad de queja, el administrador puede devolverlo a aprobado y crear una tarea nueva. Al reabrir se actualiza fecha\_revision a la fecha actual, para que las tareas anteriores dejen de contar en RN-16. Las tareas anteriores quedan como historial. Reabrir exige al menos una queja del ciudadano dueño en ese reporte, posterior a la última fecha\_revision. Un reporte eliminado no se reabre. Al reabrir se guardan revisado\_por y fecha\_revision.

### Novedades

- **RN-18.** El ciudadano crea novedades sobre sus reportes (queja sobre el gestor o la situación; tipo queja o incidente) cuando el reporte está en estado asignado, en\_proceso o resuelto. El gestor las crea sobre reportes donde tenga una tarea activa (tipo ayuda o incidente). reporte\_id siempre obligatorio; tarea\_id es obligatorio cuando la crea un gestor (su tarea activa) y opcional cuando la crea un ciudadano; si viene, debe pertenecer a ese reporte y, si la crea un gestor, ser su propia tarea activa. Se notifica al administrador. El administrador responde (respuesta, estado = atendida) y se notifica a quien la creó. Si un gestor pide ayuda, el administrador puede asignar un segundo gestor (RN-11). Cada novedad la ven solo quien la creó y el administrador. El administrador responde una sola vez y la respuesta no se edita.

### Sanciones

- **RN-19.** Solo el administrador sanciona, solo a ciudadanos, siempre de forma manual y siempre con motivo. No hay sanciones automáticas. Si la sanción va ligada a un reporte, debe ser del mismo ciudadano. Las sanciones no se editan ni se borran.
- **RN-20. Escalera sugerida por reporte erróneo:** 1.ª vez advertencia; 2.ª vez bloqueo\_temporal de 2 días (fecha\_fin = fecha\_inicio + 2 días); 3.ª vez en adelante bloqueo\_permanente (fecha\_fin NULL). El backend muestra al administrador las sanciones previas para ayudarle a decidir. El administrador también puede sancionar por cualquier otro motivo, eligiendo el tipo y escribiendo la razón. La sugerencia cuenta solo las sanciones anteriores ligadas a un reporte.
- **RN-21. Efectos:** advertencia no cambia usuario.estado. bloqueo\_temporal pone estado = bloqueado y bloqueado\_hasta = fecha\_fin. bloqueo\_permanente pone estado = bloqueado y bloqueado\_hasta = NULL. La tabla sancion es el historial y la fuente de verdad; usuario solo refleja el estado actual. Si el ciudadano está inactivo, la sanción se registra en sancion pero usuario.estado sigue en inactivo; el estado se calcula al reactivarlo (RN-05). Una sanción nueva nunca acorta un bloqueo vigente (el permanente prevalece). El administrador puede levantar un bloqueo antes de tiempo: cierra todas las sanciones de bloqueo vigentes del ciudadano poniendo su fecha\_fin en la fecha actual y, si el ciudadano no está inactivo, deja usuario.estado = activo y bloqueado\_hasta = NULL (si está inactivo, su estado no cambia). El ciudadano recibe notificación y puede consultar sus sanciones. Al levantar un bloqueo se notifica bloqueo\_levantado. Levantar un bloqueo sin bloqueo vigente responde error. El ciudadano ve sus sanciones sin el nombre del administrador.

### Validaciones de rol (la base de datos no las hace)

- **RN-22.** El backend debe verificar el rol en: reporte.ciudadano\_id (ciudadano), reporte.revisado\_por, tarea.asignado\_por y sancion.admin\_id (administrador), tarea.gestor\_id (gestor activo) y sancion.ciudadano\_id (ciudadano).

## 8. Permisos por rol

### Ciudadano

| Función | Tablas | Reglas |
| --- | --- | --- |
| Registrarse / iniciar sesión | usuario | RN-01, RN-02, RN-04 |
| Crear reporte y subir fotos | reporte, foto | RN-06, RN-15 |
| Ver todos los reportes | reporte | RN-10 |
| Editar / eliminar su reporte | reporte | RN-07 |
| Consultar estado (y acciones del gestor) | reporte, tarea, accion\_tarea | RN-16 |
| Ver notificaciones | notificacion | Sección 9 |
| Crear novedad (queja) | novedad | RN-18 |
| Recibir y ver sanciones | sancion | RN-19 a RN-21 |
| Cambiar su propia contraseña | usuario | RN-03, RN-05 |

### Gestor ambiental

| Función | Tablas | Reglas |
| --- | --- | --- |
| Iniciar sesión | usuario | RN-04 |
| Ver reportes asignados | tarea, reporte | RN-11 |
| Registrar acciones | accion\_tarea | RN-14 |
| Actualizar estado de su tarea | tarea, reporte | RN-13, RN-16 |
| Subir fotos de evidencia | foto | RN-15 |
| Ver todos los reportes | reporte | RN-10 |
| Crear novedad (ayuda, incidente) | novedad | RN-18 |
| Ver notificaciones | notificacion | Sección 9 |
| Cambiar su propia contraseña | usuario | RN-03, RN-05 |

### Administrador

| Función | Tablas | Reglas |
| --- | --- | --- |
| Iniciar sesión | usuario | RN-04 |
| Crear, actualizar, inactivar gestores y usuarios | usuario | RN-01, RN-05 |
| Crear y editar categorías | categoria | Se desactivan, no se borran |
| Revisar, aprobar, rechazar reportes (y devolver un rechazado a pendiente) | reporte | RN-08 |
| Editar o eliminar reportes | reporte, tarea | RN-09, RN-17 |
| Asignar tareas (uno o más gestores) | tarea | RN-11, RN-12 |
| Editar tareas (reasignar, cancelar) | tarea | RN-16 |
| Atender novedades | novedad | RN-18 |
| Sancionar ciudadanos | sancion, usuario | RN-19 a RN-21 |
| Seguimiento a los gestores | tarea, accion\_tarea | Sección 11 |
| Ver notificaciones | notificacion | Sección 9 |
| Levantar un bloqueo antes de tiempo | sancion, usuario | RN-21 |
| Ver resumen del dashboard | reporte, novedad, tarea, usuario | Sección 11 |
| Cambiar su propia contraseña | usuario | RN-03, RN-05 |
| Restablecer la contraseña de otro usuario | usuario | RN-05 |

## 9. Notificaciones

Cada notificación es una fila por destinatario (si hay varios administradores, una por cada uno).

Cada usuario ve solo sus notificaciones (también el administrador). Puede marcarlas como leídas, una o todas, y consultar cuántas tiene sin leer. Las notificaciones no se borran.

| Evento | Código (tipo) | Destinatario |
| --- | --- | --- |
| Reporte creado | reporte\_nuevo | Administradores |
| Reporte rechazado | reporte\_rechazado | Ciudadano |
| Reporte asignado | reporte\_asignado | Ciudadano |
| Cambio de estado del reporte (en proceso, vuelta a asignado, resuelto, vuelta a aprobado, reapertura o rechazo revertido) | reporte\_estado | Ciudadano (y administradores al resolverse) |
| Tarea asignada | tarea\_asignada | Gestor |
| Tarea cancelada, reasignada o cancelada por eliminar el reporte | tarea\_cancelada | Gestor (y administradores si fue por baja del gestor) |
| Novedad creada | novedad\_nueva | Administradores |
| Novedad respondida | novedad\_respondida | Quien la creó |
| Sanción impuesta | sancion | Ciudadano |
| Bloqueo levantado antes de tiempo | bloqueo\_levantado | Ciudadano |
| Reporte aprobado | reporte\_aprobado | Ciudadano |
| Reporte eliminado por el administrador | reporte\_eliminado | Ciudadano |
| Tarea sin acciones (sección 11) | tarea\_sin\_acciones | Administradores |
| Contraseña restablecida | contrasena\_restablecida | El usuario |

## 10. Flujos completos

### Caso A: reporte con dos gestores y reapertura

1. Ana se registra (cédula única) e inicia sesión.
2. Ana crea el reporte → estado pendiente, fotos tipo reporte. Notificación a administradores.
3. El administrador revisa → aprobado (revisado\_por, fecha\_revision). Ana ya no puede editar ni eliminar.
4. El administrador asigna a Carlos → tarea 12 asignada; reporte asignado. Notifica a Carlos y a Ana.
5. Carlos inicia → tarea 12 en\_proceso; reporte en\_proceso. Registra acciones y sube fotos de evidencia.
6. Carlos pide ayuda → novedad tipo ayuda (reporte\_id y tarea\_id). El administrador responde y asigna a Luis → tarea 13 asignada sobre el mismo reporte. Luis inicia → tarea 13 en\_proceso.
7. Carlos termina → tarea 12 terminada; el reporte sigue en\_proceso porque la tarea 13 está en proceso (si Luis no hubiera iniciado, el reporte pasaría a asignado).
8. Luis termina → tarea 13 terminada; no queda tarea activa → reporte resuelto. Notificación a Ana y al administrador.
9. Ana ve que el problema sigue → crea novedad tipo queja.
10. El administrador le da la razón → devuelve el reporte a aprobado y crea una tarea nueva. Las tareas 12 y 13 quedan como historial.

### Caso B: sanciones

1. Un ciudadano reporta algo que no es ambiental → el administrador lo rechaza con motivo\_rechazo; notificación al ciudadano.
2. El administrador crea sanción tipo advertencia (reporte\_id y motivo); notificación.
3. Si repite → sanción bloqueo\_temporal con fecha\_fin a 2 días; usuario.estado = bloqueado y bloqueado\_hasta = fecha\_fin.
4. Pasados los 2 días, en su siguiente login el sistema lo reactiva (RN-04).
5. Si repite otra vez → bloqueo\_permanente; bloqueado\_hasta NULL.

## 11. Seguimiento a los gestores (consulta de ejemplo)

```sql
SELECT u.id, u.nombre, u.apellido,
  SUM(t.estado = 'asignada')   AS asignadas,
  SUM(t.estado = 'en_proceso') AS en_proceso,
  SUM(t.estado = 'terminada')  AS terminadas,
  SUM(t.estado = 'cancelada')  AS canceladas,
  AVG(CASE WHEN t.estado = 'terminada'
      THEN TIMESTAMPDIFF(HOUR, t.fecha_asignacion, t.fecha_fin) END) AS horas_promedio
FROM usuario u
LEFT JOIN tarea t ON t.gestor_id = u.id
WHERE u.rol_id = (SELECT id FROM rol WHERE nombre = 'gestor')
GROUP BY u.id, u.nombre, u.apellido;
```

Alerta por tareas sin acciones: si una tarea asignada o en\_proceso lleva 1 día sin acciones nuevas (contado desde la última acción, o desde la asignación si no tiene ninguna), se notifica tarea\_sin\_acciones a los administradores. Se envía una sola alerta por atraso; vuelve a enviarse solo si el gestor registra una acción y luego pasa otro día sin acciones. La fecha de la última alerta se guarda en tarea.fecha\_alerta. La revisión la hace el comando revisar\_tareas\_sin\_acciones, programado cada hora en Railway. El administrador consulta el seguimiento en /api/seguimiento/gestores/.

## 12. Script SQL (MySQL 8.0.16+)

```sql
CREATE DATABASE IF NOT EXISTS ecocomunidad CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ecocomunidad;

CREATE TABLE rol (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(30) NOT NULL UNIQUE
);
INSERT INTO rol (nombre) VALUES ('ciudadano'), ('gestor'), ('administrador');

CREATE TABLE usuario (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(80) NOT NULL,
  apellido VARCHAR(80) NOT NULL,
  cedula VARCHAR(20) UNIQUE,
  correo VARCHAR(120) NOT NULL UNIQUE,
  contrasena VARCHAR(255) NOT NULL,
  telefono VARCHAR(20),
  rol_id INT NOT NULL,
  estado ENUM('activo','inactivo','bloqueado') NOT NULL DEFAULT 'activo',
  bloqueado_hasta DATETIME NULL,
  debe_cambiar_contrasena BOOLEAN NOT NULL DEFAULT FALSE,
  fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (rol_id) REFERENCES rol(id)
);

CREATE TABLE categoria (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(80) NOT NULL UNIQUE,
  descripcion VARCHAR(255),
  activa BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE reporte (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ciudadano_id INT NOT NULL,
  categoria_id INT NOT NULL,
  titulo VARCHAR(150) NOT NULL,
  descripcion TEXT NOT NULL,
  direccion VARCHAR(255) NOT NULL,
  descripcion_ubicacion VARCHAR(255),
  latitud DECIMAL(10,8),
  longitud DECIMAL(11,8),
  estado ENUM('pendiente','aprobado','asignado','en_proceso','resuelto','rechazado') NOT NULL DEFAULT 'pendiente',
  motivo_rechazo VARCHAR(255),
  eliminado BOOLEAN NOT NULL DEFAULT FALSE,
  fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  fecha_revision DATETIME,
  revisado_por INT,
  FOREIGN KEY (ciudadano_id) REFERENCES usuario(id),
  FOREIGN KEY (categoria_id) REFERENCES categoria(id),
  FOREIGN KEY (revisado_por) REFERENCES usuario(id),
  INDEX idx_reporte_estado (estado)
);

CREATE TABLE tarea (
  id INT AUTO_INCREMENT PRIMARY KEY,
  reporte_id INT NOT NULL,
  gestor_id INT NOT NULL,
  asignado_por INT NOT NULL,
  estado ENUM('asignada','en_proceso','terminada','cancelada') NOT NULL DEFAULT 'asignada',
  fecha_asignacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_fin DATETIME,
  fecha_alerta DATETIME,
  FOREIGN KEY (reporte_id) REFERENCES reporte(id),
  FOREIGN KEY (gestor_id) REFERENCES usuario(id),
  FOREIGN KEY (asignado_por) REFERENCES usuario(id),
  INDEX idx_tarea_gestor (gestor_id, estado)
);

CREATE TABLE accion_tarea (
  id INT AUTO_INCREMENT PRIMARY KEY,
  tarea_id INT NOT NULL,
  descripcion TEXT NOT NULL,
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tarea_id) REFERENCES tarea(id)
);

CREATE TABLE foto (
  id INT AUTO_INCREMENT PRIMARY KEY,
  reporte_id INT NOT NULL,
  accion_id INT,
  url VARCHAR(255) NOT NULL,
  tipo ENUM('reporte','evidencia') NOT NULL,
  subida_por INT NOT NULL,
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (reporte_id) REFERENCES reporte(id),
  FOREIGN KEY (accion_id) REFERENCES accion_tarea(id),
  FOREIGN KEY (subida_por) REFERENCES usuario(id),
  CONSTRAINT chk_foto_tipo CHECK (
    (tipo = 'reporte' AND accion_id IS NULL) OR
    (tipo = 'evidencia' AND accion_id IS NOT NULL)
  )
);

CREATE TABLE novedad (
  id INT AUTO_INCREMENT PRIMARY KEY,
  reporte_id INT NOT NULL,
  tarea_id INT,
  creado_por INT NOT NULL,
  tipo ENUM('ayuda','queja','incidente') NOT NULL,
  descripcion TEXT NOT NULL,
  estado ENUM('abierta','atendida') NOT NULL DEFAULT 'abierta',
  respuesta TEXT,
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (reporte_id) REFERENCES reporte(id),
  FOREIGN KEY (tarea_id) REFERENCES tarea(id),
  FOREIGN KEY (creado_por) REFERENCES usuario(id)
);

CREATE TABLE sancion (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ciudadano_id INT NOT NULL,
  admin_id INT NOT NULL,
  reporte_id INT,
  tipo ENUM('advertencia','bloqueo_temporal','bloqueo_permanente') NOT NULL,
  motivo VARCHAR(255) NOT NULL,
  fecha_inicio DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_fin DATETIME,
  FOREIGN KEY (ciudadano_id) REFERENCES usuario(id),
  FOREIGN KEY (admin_id) REFERENCES usuario(id),
  FOREIGN KEY (reporte_id) REFERENCES reporte(id)
);

CREATE TABLE notificacion (
  id INT AUTO_INCREMENT PRIMARY KEY,
  usuario_id INT NOT NULL,
  tipo VARCHAR(40) NOT NULL,
  mensaje VARCHAR(255) NOT NULL,
  leida BOOLEAN NOT NULL DEFAULT FALSE,
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reporte_id INT,
  FOREIGN KEY (usuario_id) REFERENCES usuario(id),
  FOREIGN KEY (reporte_id) REFERENCES reporte(id),
  INDEX idx_notif_usuario (usuario_id, leida)
);
```

## 13. Decisiones finales

- Fotos del reporte: obligatorias, al menos una (RN-06). El ciudadano puede borrarlas mientras el reporte esté pendiente, sin dejarlo sin fotos (RN-15). Almacenamiento: Cloudinary en producción.
- Ubicación: dirección, descripción y coordenadas (latitud y longitud). El backend exige las coordenadas; la base de datos las deja con NULL para poder cambiar esa regla sin tocar el esquema.
- Primer administrador: se crea por consola con un comando de Django (sección 14), nunca desde la app.
- Estado del reporte: se guarda y se recalcula en cada evento de tarea. Con ninguna tarea activa queda resuelto si hay una tarea terminada después de la última aprobación o reapertura, y aprobado si no (RN-16).
- Sanciones: una nueva nunca acorta un bloqueo vigente y el administrador puede levantar bloqueos antes de tiempo (RN-21).
- Django: no se usa /admin; JWT con revisión del estado del usuario en cada petición (sección 14).

* Estado del reporte a mano: el administrador solo hace pendiente → aprobado o rechazado, rechazado → pendiente y resuelto → aprobado; el resto es automático (RN-16).
* Inactivar usuarios: la baja de un ciudadano no frena sus reportes, su cédula sigue reservada y al reactivarlo vuelve a bloqueado si tiene un bloqueo vigente (RN-05).

- Rechazos: el administrador puede devolver un reporte rechazado a pendiente para revisarlo otra vez (RN-08).

* Ciudadano inactivo: las sanciones que reciba se registran pero no cambian su estado hasta reactivarlo (RN-05, RN-21).
* Eliminar un reporte con tareas activas: se cancelan sin recalcular su estado, y se notifica reporte\_eliminado al ciudadano y tarea\_cancelada a los gestores (RN-09).

## 14. Implementación en Django (guía)

- El proyecto usa Django con un modelo de usuario personalizado `Usuario` que hereda de `AbstractBaseUser`, con `USERNAME_FIELD = 'correo'`. La contraseña vive en la columna `password` (es la `contrasena` de la tabla 4.2) y se guarda siempre con `set_password`.
- Las tablas se definen como modelos con los mismos nombres de campo de este documento. Los valores de ENUM se declaran como `choices` con los mismos textos (por ejemplo `en_proceso`). El script SQL de la sección 12 es la referencia; si se usan migraciones de Django no se ejecuta aparte.
- Los tres roles (ciudadano, gestor, administrador) se insertan con una migración de datos.
- Cualquier endpoint que cree administradores o gestores exige que quien llama sea administrador (RN-01).

### Crear el primer administrador (RN-01)

Se hace con un comando de consola. Si ya existe un administrador, el comando se niega a crear otro. La app `usuarios` y el modelo `Rol` se ajustan a los nombres reales del proyecto. El comando necesita los archivos vacíos `usuarios/management/__init__.py` y `usuarios/management/commands/__init__.py`.

```python
# usuarios/management/commands/crear_admin.py
from getpass import getpass

from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand, CommandError

from usuarios.models import Rol


class Command(BaseCommand):
    help = 'Crea el primer administrador de EcoComunidad'

    def add_arguments(self, parser):
        parser.add_argument('--correo', required=True)
        parser.add_argument('--nombre', required=True)
        parser.add_argument('--apellido', required=True)

    def handle(self, *args, **opciones):
        Usuario = get_user_model()
        if Usuario.objects.filter(rol__nombre='administrador').exists():
            raise CommandError('Ya existe un administrador. Los demás los crea un administrador desde la app.')

        password = getpass('Contraseña: ')
        if getpass('Repite la contraseña: ') != password:
            raise CommandError('Las contraseñas no coinciden.')

        rol, _ = Rol.objects.get_or_create(nombre='administrador')
        admin = Usuario(
            correo=opciones['correo'],
            nombre=opciones['nombre'],
            apellido=opciones['apellido'],
            rol=rol,
            estado='activo',
        )
        admin.set_password(password)
        admin.save()
        self.stdout.write(self.style.SUCCESS('Administrador creado.'))
```

Se ejecuta así:

```bash
python manage.py crear_admin --correo admin@ejemplo.com --nombre Admin --apellido Principal
```

### Base de datos alojada en Aiven

- La base de datos MySQL está alojada en Aiven (Aiven for MySQL). Aiven exige conexión SSL: se descarga el certificado CA (`ca.pem`) desde la consola de Aiven y el backend lo usa al conectarse.
- La base que Aiven trae creada se llama `defaultdb` y el usuario es `avnadmin`. Por eso el `CREATE DATABASE` y el `USE ecocomunidad` de la sección 12 se omiten: las tablas se crean directamente sobre `defaultdb` (o con las migraciones de Django).
- Aiven exige clave primaria en todas las tablas. El modelo ya lo cumple: todas tienen `id` como PK.
- Host, puerto, usuario, contraseña y ruta del certificado van en variables de entorno, nunca en el código ni en el repositorio.

Conexión en Django (`settings.py`), con `mysqlclient`:

```python
import os

DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.mysql',
        'NAME': os.environ.get('DB_NAME', 'defaultdb'),
        'USER': os.environ['DB_USER'],
        'PASSWORD': os.environ['DB_PASSWORD'],
        'HOST': os.environ['DB_HOST'],
        'PORT': os.environ['DB_PORT'],
        'OPTIONS': {
            'ssl': {'ca': os.environ['DB_SSL_CA']},
        },
    }
}
```

### Almacenamiento de fotos

- En producción las fotos se guardan en Cloudinary y en la tabla `foto` solo va la `url` que devuelve. En desarrollo local se usa la carpeta `media`.
- Primero se suben las imágenes; si algo falla al guardar en la base de datos, el reporte no se crea y las imágenes subidas se eliminan (RN-06).
- En producción se activa con STORAGE\_BACKEND = cloudinary y CLOUDINARY\_URL.

### Autenticación y bloqueos (JWT)

- La autenticación usa JWT de vida corta: access de 30 minutos y refresh de 1 día.
- En cada petición el backend revisa el estado del usuario en la base de datos: si es inactivo o tiene un bloqueo vigente, responde 403 aunque el token siga válido. El refresh también lo revisa. Se aplica la misma lógica de RN-04 (un bloqueo vencido se levanta).
- Cuando se niega por el estado del usuario (en el login, el refresh o cualquier petición), la respuesta 403 es {"detail": mensaje, "code": código, "bloqueado\_hasta": fecha ISO en hora de Colombia o null}. Códigos: usuario\_inactivo (estado inactivo, bloqueado\_hasta null), usuario\_bloqueado (bloqueo temporal vigente, bloqueado\_hasta con la fecha de fin) y usuario\_bloqueado\_permanente (bloqueo permanente, bloqueado\_hasta null). El panel web usa code, no el texto de detail.

### Ajustes por usar Django

- `AbstractBaseUser` agrega la columna `last_login` a `usuario`; se acepta como columna extra.
- Cada modelo fija su tabla con `Meta.db_table` (rol, usuario, categoria, reporte, tarea, accion\_tarea, foto, novedad, sancion, notificacion) y usa `AutoField` para que las claves sean INT.
- Los ENUM se declaran con `choices`; en MySQL quedan como VARCHAR, y los valores válidos se validan en el modelo y en los serializers.
- El CHECK de `foto` (tipo y accion\_id) va en `Meta.constraints` del modelo.
- No se usa `/admin` de Django: permitiría cambiar datos saltándose las reglas de negocio (RN-16, sanciones con motivo). El rol administrador de la app cumple esa función.
- Se aceptan las tablas de auth y contenttypes que crea Django. No se usan sessions ni messages.
- Los valores por defecto y fecha\_actualizacion los pone Django (default y auto\_now), no MySQL. Toda escritura pasa por el backend.

### Documentación de la API (Swagger)

Se usa drf-spectacular. Swagger UI en /api/docs/ y el esquema en /api/schema/, solo con DEBUG = True; en producción están apagados.

### Publicación en Railway

El backend se publica en Railway con un Dockerfile, en la región de Estados Unidos (oeste), cerca de Aiven. Al arrancar aplica las migraciones y levanta gunicorn. Un segundo servicio, con el mismo código, corre el comando revisar\_tareas\_sin\_acciones cada hora (sección 11). La configuración va en variables de entorno de Railway; el certificado de Aiven va en DB\_SSL\_CA\_CONTENT. En producción DEBUG está apagado, la API responde solo en JSON y CORS solo acepta las direcciones de CORS\_ALLOWED\_ORIGINS (el panel web del administrador). La app móvil no usa CORS.

### Resumen del dashboard

GET /api/dashboard/resumen/, solo para el administrador. Todo se calcula con consultas agregadas:

- reportes\_por\_estado: cuántos reportes hay en cada estado y el total, sin contar los eliminados.
- reportes\_por\_dia: reportes creados en cada uno de los últimos 7 días (hoy incluido), del más antiguo al más reciente, contados en hora de Colombia; los días sin reportes salen con 0. Sin eliminados.
- porcentaje\_resuelto: resueltos / (aprobado + asignado + en\_proceso + resuelto) × 100, redondeado a entero; 0 si no hay ninguno.
- novedades\_abiertas: novedades en estado abierta.
- tareas\_atrasadas: tareas activas con 1 día o más sin acciones, con la misma regla de la alerta (sección 11).
- gestores\_activos: usuarios con rol gestor y estado activo.
- ultimos\_pendientes: los 5 reportes pendientes más recientes, no eliminados, con id, titulo, categoria (id y nombre) y fecha\_creacion.

### Datos de prueba

Dos comandos para ver el panel con números en desarrollo o en una demostración:

- python manage.py cargar\_datos\_prueba --confirmar: pide una contraseña para todos los usuarios de prueba y crea 3 ciudadanos y 3 gestores con correos @prueba.ecocomunidad.co, las 5 categorías si faltan, unos 15 reportes en todos los estados (con fotos), tareas, acciones, evidencias, novedades y una sanción. Usa los mismos servicios de la app, así que se cumplen las reglas (por ejemplo RN-13 y RN-16); solo se ajustan fechas para repartir los reportes en los últimos 7 días y dejar una tarea atrasada. Usa el primer administrador activo y se niega si ya hay datos de prueba. Sin --confirmar no hace nada.
- python manage.py borrar\_datos\_prueba --confirmar: borra todo lo ligado a los usuarios @prueba.ecocomunidad.co (notificaciones, fotos y sus archivos, acciones, novedades, sanciones, tareas, reportes y los usuarios). No toca categorías ni otros usuarios. Sin --confirmar solo muestra cuánto borraría.
- Es la única excepción a la eliminación lógica (sección 2), y solo para datos de prueba.
