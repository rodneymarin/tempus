# "1 evento por día" + toque en calendario — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforzar 1 evento por día por tracker (a nivel de DB) y permitir registrar/eliminar el evento del día tocando una celda del calendario de 30 días.

**Architecture:** Índice único `(trackerId, epochDay)` + migración Room 1→2 que deduplica datos existentes; `insert` con `REPLACE` para que registrar un día ya existente reemplace la hora; UI de detalle con diálogos de confirmación al tocar el calendario, botón "Sucedió hoy" deshabilitado cuando ya hay registro, historial de 1 card por día y aviso de reemplazo en el registro manual.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room 2.6.1 (KSP), Gradle. Tests JVM (`testDebugUnitTest`) e instrumentados (`connectedDebugAndroidTest`, emulador disponible en `emulator-5554`).

**Spec:** `docs/superpowers/specs/2026-08-20-one-event-per-day-design.md`

## Global Constraints

- No commitear archivos de WIP ajenos a la tarea. El árbol de trabajo tiene cambios sin commitear del usuario (`.gitignore`, `DashboardViewModel.kt`, `TrackerCard.kt`, borrado de `FrequencyChart.kt`/`HeatStrip.kt`, `LogHistory.kt`, `MonthCalendarStrip.kt`, `TrackerDetailScreen.kt`, `TempusNavHost.kt`, `strings.xml`, y `app/src/test/java/com/rodneymarin/tempus/ui/` sin trackear). Cada commit de este plan incluye SOLO los archivos listados en su paso de commit.
- El evento diario conserva hora opcional (`timeMinutes: Int?`), `null` = sin hora.
- La hora/`timeMinutes` no se elimina del modelo.
- `strings.xml` está en UTF-8 correcto; al añadir strings usar acentos reales (á, é, í, ó, ú, ñ) igual que los existentes.
- `exportSchema` pasa a `true`; los JSON de schema (`schemas/...`) se commitean.
- Comandos de Gradle en Windows/PowerShell: `.\gradlew.bat`.
- No se cambia `StatsCalculator` ni el heatmap (cuenta por día ya soporta 0/1).

---

### Task 1: Configurar exportación de esquema Room (línea base v1)

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/rodneymarin/tempus/data/TempusDatabase.kt`
- Create (build): `schemas/com.rodneymarin.tempus.data.TempusDatabase/1.json`

**Interfaces:**
- Consumes: nada.
- Produces: `TempusDatabase` con `exportSchema = true` (versión sigue en 1); el build genera `schemas/.../1.json` que el test de migración (Task 3) lee vía `MigrationTestHelper`.

- [ ] **Step 1: Habilitar exportSchema (sin cambiar la versión)**

En `TempusDatabase.kt:9`:
```kotlin
@Database(entities = [Tracker::class, LogEntry::class], version = 1, exportSchema = true)
```

En `app/build.gradle.kts`, dentro del bloque `android { }` (después de `buildFeatures { compose = true }`):
```kotlin
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
```
Y después del bloque `android { }`, añadir el bloque ksp (los plugins ya incluyen KSP):
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

- [ ] **Step 2: Generar el esquema v1**

Run: `.\gradlew.bat assembleDebug`
Expected: se crea `schemas/com.rodneymarin.tempus.data.TempusDatabase/1.json` y el build compila.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/rodneymarin/tempus/data/TempusDatabase.kt schemas/
git commit -m "chore: export room schema baseline (v1)"
```

---

### Task 2: Capa de datos — índice único, migración, REPLACE y deleteByDay

**Files:**
- Modify: `app/src/main/java/com/rodneymarin/tempus/data/LogEntry.kt`
- Modify: `app/src/main/java/com/rodneymarin/tempus/data/TempusDatabase.kt`
- Create: `app/src/main/java/com/rodneymarin/tempus/data/Migrations.kt`
- Modify: `app/src/main/java/com/rodneymarin/tempus/data/LogEntryDao.kt`
- Modify: `app/src/main/java/com/rodneymarin/tempus/data/TrackersRepository.kt`
- Create (build): `schemas/com.rodneymarin.tempus.data.TempusDatabase/2.json`

**Interfaces:**
- Consumes: `LogEntry`, `TempusDatabase`, `LogEntryDao`, `TrackersRepository` de Task 1.
- Produces:
  - `val MIGRATION_1_2: Migration` (en `Migrations.kt`)
  - `LogEntryDao.insert(entry: LogEntry): Long` con `OnConflictStrategy.REPLACE`
  - `LogEntryDao.deleteByDay(trackerId: Long, epochDay: Long)`
  - `TrackersRepository.deleteLogsForDay(trackerId: Long, epochDay: Long)`
  - `TrackersRepository.logEvent(trackerId, epochDay, timeMinutes)` (sin cambios de firma; hereda REPLACE)

- [ ] **Step 1: Índice único en LogEntry**

En `LogEntry.kt`, cambiar la línea del `indices`:
```kotlin
    indices = [
        Index("trackerId"),
        Index("epochDay"),
        Index(value = ["trackerId", "epochDay"], unique = true),
    ],
```
(import `androidx.room.Index` ya existe.)

- [ ] **Step 2: Bump versión + migración**

En `TempusDatabase.kt`:
```kotlin
@Database(entities = [Tracker::class, LogEntry::class], version = 2, exportSchema = true)
```

Crear `app/src/main/java/com/rodneymarin/tempus/data/Migrations.kt`:
```kotlin
package com.rodneymarin.tempus.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: el modelo pasa a 1 evento por día. Deduplica filas existentes
 * (conserva el id más alto por tracker+día) y crea el índice único.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "DELETE FROM log_entries WHERE id NOT IN " +
                "(SELECT MAX(id) FROM log_entries GROUP BY trackerId, epochDay)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_log_entries_trackerId_epochDay " +
                "ON log_entries(trackerId, epochDay)"
        )
    }
}
```

En `TempusDatabase.kt`, `RoomBuilder.build`:
```kotlin
    fun build(context: Context): TempusDatabase =
        Room.databaseBuilder(context, TempusDatabase::class.java, "tempus.db")
            .addMigrations(MIGRATION_1_2)
            .build()
```

- [ ] **Step 3: DAO REPLACE + deleteByDay**

En `LogEntryDao.kt`:
```kotlin
import androidx.room.OnConflictStrategy
```
```kotlin
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LogEntry): Long

    @Query("DELETE FROM log_entries WHERE trackerId = :trackerId AND epochDay = :epochDay")
    suspend fun deleteByDay(trackerId: Long, epochDay: Long)
```

- [ ] **Step 4: Repository.deleteLogsForDay**

En `TrackersRepository.kt`:
```kotlin
    suspend fun logEvent(trackerId: Long, epochDay: Long, timeMinutes: Int?): Long =
        logDao.insert(LogEntry(trackerId = trackerId, epochDay = epochDay, timeMinutes = timeMinutes))

    suspend fun deleteLogsForDay(trackerId: Long, epochDay: Long) = logDao.deleteByDay(trackerId, epochDay)
```
Nota: `DashboardViewModel.logToday` ya usa `repo.logEvent`, así que hereda la semántica de reemplazo sin cambios (era código sin uso).

- [ ] **Step 5: Compilar y generar esquema v2**

Run: `.\gradlew.bat assembleDebug`
Expected: compila y se crea `schemas/com.rodneymarin.tempus.data.TempusDatabase/2.json`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/rodneymarin/tempus/data/ schemas/
git commit -m "feat: unique index per tracker+day, v1→v2 migration, REPLACE insert"
```

---

### Task 3: Tests — dedupe del DAO y migración

**Files:**
- Modify: `app/src/androidTest/java/com/rodneymarin/tempus/data/TrackerDaoTest.kt`
- Create: `app/src/androidTest/java/com/rodneymarin/tempus/data/MigrationTest.kt`

**Interfaces:**
- Consumes: `LogEntryDao`, `TrackerDao`, `MIGRATION_1_2`, esquemas v1/v2 de las Tasks 1-2.

- [ ] **Step 1: Test de reemplazo (1 evento por día) en TrackerDaoTest**

Añadir en `TrackerDaoTest.kt`:
```kotlin
    @Test fun insertSameDayReplaces() = runBlocking {
        val trackerId = db.trackerDao().insert(Tracker(name = "C"))
        db.logEntryDao().insert(LogEntry(trackerId = trackerId, epochDay = 300, timeMinutes = 60))
        val secondId = db.logEntryDao().insert(LogEntry(trackerId = trackerId, epochDay = 300, timeMinutes = 480))
        val logs = db.logEntryDao().observeByTracker(trackerId).first()
        assertEquals(1, logs.size)
        assertEquals(480, logs[0].timeMinutes)
        assertEquals(secondId, logs[0].id)
    }

    @Test fun deleteByDayRemovesOnlyThatDay() = runBlocking {
        val trackerId = db.trackerDao().insert(Tracker(name = "D"))
        db.logEntryDao().insert(LogEntry(trackerId = trackerId, epochDay = 400, timeMinutes = null))
        db.logEntryDao().insert(LogEntry(trackerId = trackerId, epochDay = 401, timeMinutes = null))
        db.logEntryDao().deleteByDay(trackerId, 400)
        val logs = db.logEntryDao().observeByTracker(trackerId).first()
        assertEquals(listOf(401L), logs.map { it.epochDay })
    }
```

- [ ] **Step 2: Test de migración**

Crear `app/src/androidTest/java/com/rodneymarin/tempus/data/MigrationTest.kt`:
```kotlin
package com.rodneymarin.tempus.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TempusDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun migrate1To2_deduplicatesAndEnforcesUniqueIndex() {
        helper.createDatabase("migration-test", 1).use { db ->
            db.execSQL(
                "INSERT INTO trackers (id, name, emoji, minFrequency, maxFrequency, period, createdAt) " +
                    "VALUES (1, 'A', 'x', NULL, NULL, 'WEEK', 0)"
            )
            db.execSQL(
                "INSERT INTO log_entries (id, trackerId, epochDay, timeMinutes) VALUES (1, 1, 20000, NULL)"
            )
            db.execSQL(
                "INSERT INTO log_entries (id, trackerId, epochDay, timeMinutes) VALUES (2, 1, 20000, 480)"
            )
            db.execSQL(
                "INSERT INTO log_entries (id, trackerId, epochDay, timeMinutes) VALUES (3, 1, 20001, NULL)"
            )
        }

        val db = helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2)

        db.query("SELECT id, epochDay, timeMinutes FROM log_entries").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(2, c.getLong(0))            // se conservó el id más alto del día 20000
            assertEquals(20000, c.getLong(1))
            assertEquals(480, c.getLong(2))
            c.moveToNext()
            assertEquals(3, c.getLong(0))
            assertEquals(20001, c.getLong(1))
        }
        db.close()

        // El índice único rechaza un segundo evento en el mismo día.
        helper.createDatabase("migration-test", 2).use { db ->
            var thrown = false
            try {
                db.execSQL(
                    "INSERT INTO log_entries (id, trackerId, epochDay, timeMinutes) VALUES (4, 1, 20000, NULL)"
                )
            } catch (e: Exception) {
                thrown = true
            }
            org.junit.Assert.assertTrue(thrown)
        }
    }
}
```

- [ ] **Step 3: Ejecutar tests instrumentados**

Run: `.\gradlew.bat connectedDebugAndroidTest`
Expected: PASS en `TrackerDaoTest` (los 4 tests) y `MigrationTest`. Si el emulador no responde, reportar y continuar; los tests quedan escritos para correr luego.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/rodneymarin/tempus/data/
git commit -m "test: one-event-per-day replace, deleteByDay, and 1->2 migration"
```

---

### Task 4: Toque en el calendario + diálogos + "Sucedió hoy" deshabilitado

**Files:**
- Modify: `app/src/main/java/com/rodneymarin/tempus/ui/detail/MonthCalendarStrip.kt`
- Modify: `app/src/main/java/com/rodneymarin/tempus/ui/detail/TrackerDetailViewModel.kt`
- Modify: `app/src/main/java/com/rodneymarin/tempus/ui/detail/TrackerDetailScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `TrackerDetailViewModel.logOn(date, timeMinutes)` (existe), `TrackerDetailViewModel.ui.dailyCounts`.
- Produces:
  - `MonthCalendarStrip(daysWithEvent, today, onDayClick: (LocalDate) -> Unit, modifier)` — celdas en rango clickeables.
  - `TrackerDetailViewModel.deleteDay(date: LocalDate)`.
  - Strings: `log_today_done`, `register_day_confirm`, `delete_day_confirm`.

- [ ] **Step 1: Strings nuevos**

En `strings.xml`, tras `log_today`:
```xml
    <string name="log_today_done">Ya se registró hoy</string>
    <string name="register_day_confirm">¿Registrar evento el día %1$s?</string>
    <string name="delete_day_confirm">Este día ya tiene un evento registrado. ¿Deseas eliminarlo?</string>
```

- [ ] **Step 2: MonthCalendarStrip clickeable**

En `MonthCalendarStrip.kt`, añadir import:
```kotlin
import androidx.compose.foundation.clickable
```
Cambiar la firma:
```kotlin
fun MonthCalendarStrip(
    daysWithEvent: Set<LocalDate>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
```
En la celda (el `Box`), encadenar `clickable` después de `clip` y antes de `background`, solo si `isInRange`:
```kotlin
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .then(if (isInRange) Modifier.clickable { onDayClick(cellDate) } else Modifier)
                                        .background(bgColor)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
```

- [ ] **Step 3: deleteDay en el ViewModel**

En `TrackerDetailViewModel.kt`, tras `deleteLog`:
```kotlin
    fun deleteDay(date: LocalDate) = viewModelScope.launch {
        repo.deleteLogsForDay(trackerId, date.toEpochDay())
    }
```

- [ ] **Step 4: Pantalla de detalle**

En `TrackerDetailScreen.kt`:
- Import `java.time.format.DateTimeFormatter` y `java.util.Locale`.
- Estado para los diálogos (junto a `logToDelete`):
```kotlin
    var registerDay by remember { mutableStateOf<LocalDate?>(null) }
    var deleteDay by remember { mutableStateOf<LocalDate?>(null) }
```
- Tras `val today = LocalDate.now()`:
```kotlin
    val daysWithEvent = ui.dailyCounts.filterValues { it > 0 }.keys
    val todayHasEvent = (ui.dailyCounts[today] ?: 0) > 0
```
- Botón "Sucedió hoy":
```kotlin
                Button(
                    onClick = viewModel::logToday,
                    enabled = !todayHasEvent,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(if (todayHasEvent) R.string.log_today_done else R.string.log_today))
                }
```
- Calendario:
```kotlin
            MonthCalendarStrip(
                daysWithEvent = daysWithEvent,
                today = today,
                onDayClick = { day ->
                    if (day in daysWithEvent) deleteDay = day else registerDay = day
                },
            )
```
- Diálogos (tras el bloque de `logToDelete`, antes de `confirmDeleteTracker`):
```kotlin
    registerDay?.let { day ->
        AlertDialog(
            onDismissRequest = { registerDay = null },
            title = { Text(stringResource(R.string.register_day_confirm, day.format(
                DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es"))
            ))) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.logOn(day, null)
                    registerDay = null
                }) { Text(stringResource(R.string.register)) }
            },
            dismissButton = {
                TextButton(onClick = { registerDay = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    deleteDay?.let { day ->
        AlertDialog(
            onDismissRequest = { deleteDay = null },
            title = { Text(stringResource(R.string.delete_day_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDay(day)
                    deleteDay = null
                }) { Text(stringResource(R.string.delete_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDay = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
```

- [ ] **Step 5: Compilar**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: compila sin errores.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/rodneymarin/tempus/ui/detail/MonthCalendarStrip.kt app/src/main/java/com/rodneymarin/tempus/ui/detail/TrackerDetailViewModel.kt app/src/main/java/com/rodneymarin/tempus/ui/detail/TrackerDetailScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat: tap day in calendar to register (no time) or delete; disable log-today when done"
```

---

### Task 5: Historial — 1 card por día

**Files:**
- Modify: `app/src/main/java/com/rodneymarin/tempus/ui/detail/TrackerDetailViewModel.kt`
- Modify: `app/src/main/java/com/rodneymarin/tempus/ui/detail/LogHistory.kt`
- Modify: `app/src/main/java/com/rodneymarin/tempus/ui/detail/TrackerDetailScreen.kt`

**Interfaces:**
- Consumes: `ui.history: List<LogEntry>` (producido aquí).
- Produces:
  - `DetailUiState.history: List<LogEntry>` (descendente por `epochDay`), se elimina `DayGroup`.
  - `LogHistory(entries: List<LogEntry>, onDelete: (LogEntry) -> Unit, modifier)`.

- [ ] **Step 1: ViewModel — history como lista de LogEntry**

En `TrackerDetailViewModel.kt`:
- Eliminar la data class `DayGroup`:
```kotlin
data class DayGroup(val date: LocalDate, val entries: List<LogEntry>)
```
- Cambiar el campo:
```kotlin
    val history: List<LogEntry> = emptyList(),
```
- En el `combine`, reemplazar el bloque de history:
```kotlin
                history = logs.sortedByDescending { it.epochDay },
```

- [ ] **Step 2: LogHistory simplificado**

En `LogHistory.kt`, reemplazar el cuerpo:
```kotlin
@Composable
fun LogHistory(
    entries: List<LogEntry>,
    onDelete: (LogEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.forEach { entry ->
            val date = LocalDate.ofEpochDay(entry.epochDay)
            val header = date
                .format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es")))
                .replaceFirstChar { it.uppercase() }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(header, style = MaterialTheme.typography.titleSmall)
                        Text(
                            entry.timeMinutes?.let { minutes ->
                                LocalTime.of(minutes / 60, minutes % 60)
                                    .format(DateTimeFormatter.ofPattern("h:mm a", Locale("es")))
                            } ?: stringResource(R.string.no_time),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(entry) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
```
Imports necesarios en `LogHistory.kt`: añadir `import java.time.LocalDate`. Se eliminan los imports de `DayGroup` si existían (no los había) y los de `Group`.

- [ ] **Step 3: Actualizar la llamada en TrackerDetailScreen**

En `TrackerDetailScreen.kt`:
```kotlin
                LogHistory(entries = ui.history, onDelete = { logToDelete = it })
```

- [ ] **Step 4: Compilar**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: compila sin errores.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rodneymarin/tempus/ui/detail/TrackerDetailViewModel.kt app/src/main/java/com/rodneymarin/tempus/ui/detail/LogHistory.kt app/src/main/java/com/rodneymarin/tempus/ui/detail/TrackerDetailScreen.kt
git commit -m "refactor: history shows one card per day (1 event per day)"
```

---

### Task 6: Registro manual — aviso de reemplazo de hora

**Files:**
- Modify: `app/src/main/java/com/rodneymarin/tempus/ui/detail/RegisterEventSheet.kt`
- Modify: `app/src/main/java/com/rodneymarin/tempus/ui/detail/TrackerDetailScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `RegisterEventSheet.onConfirm(date, timeMinutes)` (existe), `ui.dailyCounts`.
- Produces:
  - `RegisterEventSheet(onDismiss, onConfirm, daysWithEvent: Set<LocalDate>)` — muestra aviso si `selectedDate in daysWithEvent`.
  - Strings: `sheet_has_event_warning`, `sheet_replaces_with_time`, `sheet_replaces_no_time`.

- [ ] **Step 1: Strings nuevos**

En `strings.xml`, tras `register`:
```xml
    <string name="sheet_has_event_warning">Ya hay un evento registrado este día. Al confirmar se reemplazará su hora por %1$s.</string>
    <string name="sheet_replaces_with_time">la hora seleccionada</string>
    <string name="sheet_replaces_no_time">sin hora</string>
```

- [ ] **Step 2: Aviso en RegisterEventSheet**

En `RegisterEventSheet.kt`:
```kotlin
fun RegisterEventSheet(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, Int?) -> Unit,
    daysWithEvent: Set<LocalDate>,
) {
```
Tras el `FilterChip` de hora y antes del `Button` Registrar:
```kotlin
        if (selectedDate in daysWithEvent) {
            val replacement = stringResource(
                if (showTime) R.string.sheet_replaces_with_time else R.string.sheet_replaces_no_time
            )
            Text(
                stringResource(R.string.sheet_has_event_warning, replacement),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
```

- [ ] **Step 3: Pasar el set desde TrackerDetailScreen**

En `TrackerDetailScreen.kt`, en la llamada a la hoja:
```kotlin
            RegisterEventSheet(
                onDismiss = { showSheet = false },
                onConfirm = { date, timeMinutes ->
                    viewModel.logOn(date, timeMinutes)
                    showSheet = false
                },
                daysWithEvent = daysWithEvent,
            )
```

- [ ] **Step 4: Compilar**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: compila sin errores.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rodneymarin/tempus/ui/detail/RegisterEventSheet.kt app/src/main/java/com/rodneymarin/tempus/ui/detail/TrackerDetailScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat: manual register warns and replaces hour when day already has event"
```

---

### Task 7: Verificación completa

**Files:**
- Ninguno (solo verificación).

- [ ] **Step 1: Unit tests JVM**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS (incluye `StatsCalculatorTest`, `FrequencyRangeTest`, `LastEventInfoTest`).

- [ ] **Step 2: Tests instrumentados**

Run: `.\gradlew.bat connectedDebugAndroidTest`
Expected: PASS en `TrackerDaoTest` y `MigrationTest` (con el emulador `emulator-5554`).

- [ ] **Step 3: Build de release del debug APK**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Smoke test manual (opcional)**

Instalar el APK en el emulador, abrir un tracker y comprobar: tocar un día vacío registra sin hora (snackbar "Registrado"), tocar un día con evento ofrece eliminar, "Sucedió hoy" se deshabilita con "Ya se registró hoy", y el registro manual avisa y reemplaza.

---

## Self-Review (verificado al escribir)

1. **Cobertura del spec:** 1 ✓ (Task 2, índice único+migración), 2 ✓ (Task 2, REPLACE+deleteLogsForDay), 3 ✓ (Tasks 4-6, toque en calendario, diálogos, botón deshabilitado), 4 ✓ (Task 5, historial 1 card/día, `DayGroup` eliminado), 5 ✓ (Task 6, aviso de reemplazo), 6 ✓ (Task 2 nota: `DashboardViewModel.logToday` hereda REPLACE, sin cambios), 7 ✓ (Tasks 3 y 7, tests).
2. **Placeholders:** sin "TBD/TODO"; todos los pasos tienen código concreto.
3. **Consistencia de tipos:** `deleteLogsForDay(trackerId: Long, epochDay: Long)` en repo y `deleteDay(date: LocalDate)` en VM (convierte con `date.toEpochDay()`); `LogHistory(entries: List<LogEntry>, ...)` coincide con `ui.history: List<LogEntry>`; `daysWithEvent: Set<LocalDate>` en hoja y calendario; `MIGRATION_1_2` y `index_log_entries_trackerId_epochDay` usan el nombre por defecto de Room (coincide con lo que valida el `MigrationTestHelper`).