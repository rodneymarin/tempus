# Diseño: 1 evento por día + toque en calendario

Fecha: 2026-08-20

## Objetivo

Cambiar la app a un modelo de **1 evento por día** como unidad mínima de registro, y permitir
registrar/eliminar el evento del día tocando la celda del calendario de los últimos 30 días.

## Contexto

Hoy la app permite registrar varios eventos en el mismo día (tabla `log_entries` sin
unicidad, historial agrupado por día con varias entradas). El flujo de registro ya existe:

- `TrackerDetailViewModel.logOn(date, timeMinutes)` con `timeMinutes = null` registra "sin hora".
- El calendario (`MonthCalendarStrip`) muestra los días con evento pero no es interactivo.

## Decisiones acordadas

1. Máximo 1 evento por día por tracker, impuesto a nivel de base de datos.
2. El campo de hora opcional se mantiene (el evento diario puede llevar hora o no).
3. Toque en día sin evento: confirmar y registrar sin hora.
4. Toque en día con evento: avisar y ofrecer eliminar el evento de ese día.
5. Botón "Sucedió hoy": deshabilitado cuando hoy ya tiene evento; label "Ya se registró hoy".
6. Registro manual: si el día ya tiene evento, permite reemplazar su hora con aviso detallado.

## Cambios

### 1. Modelo de datos y migración

- `LogEntry` (`app/src/main/java/com/rodneymarin/tempus/data/LogEntry.kt`):
  índice único `Index(value = ["trackerId", "epochDay"], unique = true)`.
- `TempusDatabase`: versión 1 → 2, `exportSchema` sin cambios.
- Migración `MIGRATION_1_2` en `RoomBuilder`:
  1. Deduplicar datos existentes conservando el evento con el `id` más alto por
     `(trackerId, epochDay)`:
     `DELETE FROM log_entries WHERE id NOT IN (SELECT MAX(id) FROM log_entries GROUP BY trackerId, epochDay)`
  2. Crear índice único:
     `CREATE UNIQUE INDEX index_log_entries_trackerId_epochDay ON log_entries(trackerId, epochDay)`
- Registrar la migración en `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)`.

### 2. Capa de datos

- `LogEntryDao`:
  - `insert` con `@Insert(onConflict = OnConflictStrategy.REPLACE)`: registrar un día ya
    existente reemplaza la fila (id nuevo).
  - Nueva query `deleteByDay(trackerId, epochDay)`:
    `DELETE FROM log_entries WHERE trackerId = :trackerId AND epochDay = :epochDay`.
- `TrackersRepository`:
  - `logEvent(trackerId, epochDay, timeMinutes)` ya usa `insert` (ahora REPLACE).
  - Nuevo `deleteLogsForDay(trackerId, epochDay)`.

### 3. Pantalla de detalle

- `MonthCalendarStrip`: nuevo parámetro `onDayClick: (LocalDate) -> Unit`; las celdas dentro
  del rango se hacen clicables (`Modifier.clickable`).
- `TrackerDetailScreen`:
  - Día sin evento → `AlertDialog` "¿Registrar evento el día {día} de {mes} de {año}?"
    → confirmar → `viewModel.logOn(date, null)` (sin hora). El snackbar "Registrado —
    Deshacer" existente sigue funcionando.
  - Día con evento → `AlertDialog` "Este día ya tiene un evento registrado. ¿Deseas
    eliminarlo?" → confirmar → `viewModel.deleteDay(date)`.
  - Botón "Sucedió hoy": `enabled = !todayHasEvent`; label "Ya se registró hoy" al deshabilitarse.
- `TrackerDetailViewModel`:
  - Derivar `todayHasEvent` desde `dailyCounts` (o desde los logs).
  - Nueva función `deleteDay(date)` que llama a `repo.deleteLogsForDay`.

### 4. Historial

- `ui.history` pasa de `List<DayGroup>` (varias entradas por día) a `List<LogEntry>`
  ordenada por fecha descendente.
- `LogHistory` muestra una card por día: fecha + hora (o "Sin hora") + botón eliminar.
- Se elimina el agrupamiento multi-entrada (`DayGroup` con lista).

### 5. Registro manual

- `RegisterEventSheet` recibe el conjunto de días con evento (o un lookup).
- Si el día seleccionado ya tiene evento, se muestra aviso: "Ya hay un evento registrado
  el día X. Al confirmar se reemplazará su hora por [la hora seleccionada / sin hora]".
  El texto del aviso se actualiza según el estado actual del selector de hora
  (hora elegida si está activo, "sin hora" si no). El botón Registrar permanece activo
  (reemplaza).

### 6. Dashboard

- `DashboardViewModel.logToday(trackerId)` está sin uso (no hay botón en el dashboard).
  Se actualiza a la nueva semántica (reemplazo) para no dejar rastro de duplicados.

### 7. Pruebas

- `TrackerDaoTest`:
  - Registrar dos eventos en el mismo día → queda 1 fila con la hora del segundo.
  - `deleteLogsForDay` elimina solo el evento de ese día.
- Unit tests existentes (`StatsCalculator`, `LastEventInfo`, `FrequencyRange`) sin cambios.

## Strings nuevos

- "Ya se registró hoy" (label del botón deshabilitado).
- "¿Registrar evento el día %1$s?" (confirmación de toque).
- "Este día ya tiene un evento registrado. ¿Deseas eliminarlo?" (confirmación de borrado).
- Aviso de reemplazo de hora en el registro manual.

## Fuera de alcance

- No se cambia el cálculo de estadísticas ni el heatmap (cuenta por día ya funciona con 0/1).
- No se introduce deduplicación visual ni edición de fecha de un evento existente.