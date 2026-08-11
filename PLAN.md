# Tempus — Recurring Event/Habit Tracker (Android) — Implementation Plan

> **For the executing agent:** implement this plan task by task, strictly in order. Every task is bite-sized; commit after each task. Run every listed verification step. If a pinned dependency version fails to resolve, move to the latest stable of the same major (see Risks). Do not skip phases.

**Goal:** A native Android app (Kotlin + Jetpack Compose) where the user creates *trackers* — recurring events or habits, e.g. "Idas al baño" — optionally sets an expected frequency range (e.g. 3–5 times/week), logs occurrences daily with a one-tap "Sucedió hoy", and visualizes whether each event is on track: charts of actual vs. expected, a 35-day heat strip, and status tags on every screen.

**Architecture:** Single-module app, single Activity, Compose Navigation, MVVM (ViewModel → Repository → Room → Flow). 100% local data (SQLite via Room) — no network permission, no accounts, no sync. The core logic lives in a pure-Kotlin `StatsCalculator` (no Android imports) so frequency math is unit-testable on the JVM.

**Tech Stack (pinned):** Kotlin 2.0.21 · AGP 8.5.2 · Gradle 8.9 · compileSdk/targetSdk 35 · minSdk 26 · Compose BOM 2024.09.03 · Material 3 · Navigation Compose 2.8.1 · Room 2.6.1 + KSP 2.0.21-1.0.28 · kotlinx-coroutines 1.9.0 · JUnit 4.13.2. **Charts are hand-drawn with Compose Canvas** (zero chart-library dependency → no version drift, full style control).

---

## 1. Product decisions (taken — implement as specified)

1. **App name:** Tempus. Launcher label `Tempus`, package `com.rodneymarin.tempus`.
2. **UI language:** Spanish. All user-facing strings in `app/src/main/res/values/strings.xml` (default locale).
3. **Tracker** = named event + optional expected range `{min, max}` + period (DAY / WEEK / MONTH, default WEEK). `min` and `max` are independently optional: "al menos 3", "máximo 5", "3–5", or none.
4. **LogEntry** = one occurrence: `trackerId` + date (`epochDay` = days since Unix epoch) + optional time (`timeMinutes` = minutes since local midnight, nullable).
5. **Multiple logs per day are allowed** (two bathroom trips in one day = 2 logs). No dedup.
6. **Calendar periods:** WEEK = Monday–Sunday (ISO), MONTH = 1st–last day, DAY = today. Status compares the *projected pace* (count ÷ elapsed fraction of period) against the range — so a low count on Monday does not falsely flag "Bajo".
7. **Status tags:** `En track` (green) · `Bajo` (amber, projected < min) · `Alto` (red, projected > max) · gray chip "Sin rango" when no range set.
8. **FrequencyChart:** last 8 periods (14 for daily trackers), rounded bars = actual count, expected band = shaded region between min and max with dashed edges.
9. **HeatStrip:** last 35 days, one rounded square per day; fill intensity = occurrences that day (0/1/2+).
10. **Quick log:** "Sucedió hoy" on the dashboard card and detail screen logs instantly (timestamp = now) and shows snackbar "Registrado" with **Deshacer** (undo deletes the just-inserted row). A secondary "Registrar otro día…" bottom sheet picks any past date + optional time.
11. **No Hilt** — manual DI: `AppContainer` built in `TempusApp` (Application), ViewModels created with `viewModelFactory`.
12. **No INTERNET permission** in the manifest (local-only privacy stance). `android:allowBackup="true"`.
13. **Fixed Material 3 brand palette** (teal/green) with light + dark schemes; `dynamicColor = false` (deterministic visuals, stable QA screenshots).
14. **Delete tracker** cascades its logs (FK `ON DELETE CASCADE`) after an explicit confirmation dialog.

## 2. Data model (Room)

```
trackers                        log_entries
─────────                       ───────────
id          Long PK autoGen     id           Long PK autoGen
name        String              trackerId    Long FK → trackers.id ON DELETE CASCADE
emoji       String              epochDay     Long
minFrequency Int?               timeMinutes  Int?           (nullable = "sin hora")
maxFrequency Int?
period      String (enum name)  Index: (trackerId), (epochDay)
createdAt   Long
```

## 3. Screens & navigation

| Route | Screen | Contents |
|---|---|---|
| `/` | Dashboard | LazyColumn of tracker cards (emoji chip, name, "Esta semana: N · esperado X–Y", status chip, "+ Hoy" quick log); FAB → editor (new); empty-state CTA when no trackers |
| `tracker/edit?trackerId={id?}` | Editor | Name field (required, ≤40 chars), emoji grid picker, optional min/max + period segmented control, inline validation errors, Save/Cancel |
| `tracker/{trackerId}` | Detail | Header (emoji, name, expected summary, status chip), big "Sucedió hoy" button, "Registrar otro día…" sheet, FrequencyChart, HeatStrip, history list grouped by day (per-entry delete), overflow menu (edit / delete tracker) |

Single Activity + back stack. No bottom navigation. Navigation Compose with typed routes as string constants (see Phase 0).

## 4. Repository layout (final)

```
tempus/
├─ PLAN.md
├─ settings.gradle.kts · build.gradle.kts · gradle.properties
├─ gradle/libs.versions.toml · gradle/wrapper/gradle-wrapper.{jar,properties}
├─ gradlew · gradlew.bat
└─ app/
   ├─ build.gradle.kts · proguard-rules.pro
   └─ src/
      ├─ main/AndroidManifest.xml
      ├─ main/res/values/strings.xml · values/themes.xml · values/colors.xml (launcher bg)
      ├─ main/res/drawable/ic_launcher_foreground.xml · values/ic_launcher_background.xml
      ├─ main/res/mipmap-anydpi-v26/ic_launcher.xml · ic_launcher_round.xml
      ├─ main/java/com/rodneymarin/tempus/
      │  ├─ TempusApp.kt                (Application + AppContainer + viewModelFactory helpers)
      │  ├─ MainActivity.kt
      │  ├─ data/   TempusDatabase · Converters · Tracker · LogEntry · TrackerDao · LogEntryDao · TrackersRepository
      │  ├─ domain/ FrequencyPeriod · FrequencyRange · StatsCalculator
      │  ├─ ui/theme/   Color.kt · Type.kt · Theme.kt
      │  ├─ ui/navigation/ Routes.kt · TempusNavHost.kt
      │  ├─ ui/dashboard/ DashboardScreen.kt · DashboardViewModel.kt · TrackerCard.kt · StatusChip.kt
      │  ├─ ui/editor/   TrackerEditorScreen.kt · TrackerEditorViewModel.kt · EmojiPicker.kt
      │  └─ ui/detail/   TrackerDetailScreen.kt · TrackerDetailViewModel.kt · FrequencyChart.kt · HeatStrip.kt · LogHistory.kt · RegisterEventSheet.kt
      ├─ test/java/com/rodneymarin/tempus/domain/ StatsCalculatorTest.kt · FrequencyRangeTest.kt
      └─ androidTest/java/com/rodneymarin/tempus/data/ TrackerDaoTest.kt
```

## 5. Cross-cutting conventions

- Dates: `java.time.LocalDate` (minSdk 26 → no desugaring needed). `epochDay` = `toEpochDay()`; back with `LocalDate.ofEpochDay()`.
- Time: `timeMinutes` = `LocalTime.now()` → `(hour*60+minute)`. Format `"%02d:%02d"`.
- All DAO reads return `Flow`; ViewModels combine them with `stateIn(WhileSubscribed(5000))`.
- Spanish date formatting: `DateTimeFormatter`/`getDisplayName` with `Locale("es")` (es-VE not needed; es is enough).
- Semantics: every IconButton gets `contentDescription` from strings.xml.
- Every task ends with a git commit: `git add -A && git commit -m "task: <slug>"` (init repo in Phase 0).

## Phase 0 — Project scaffold

> Prereq: the machine must have the Android SDK (Android Studio installed, or `ANDROID_HOME` set). The plan builds the project by hand (no Android Studio wizard), which is fully reproducible from the terminal.

### Task 0.1 — Init repo + Gradle wrapper bootstrap

**Files:** `.gitignore`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`

**Step 1:** `git init` in `tempus/`. Write `.gitignore`:
```
.gradle/  build/  local.properties  .idea/  *.iml  .DS_Store  .kotlin/
```

**Step 2:** Create `gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

**Step 3:** Obtain the wrapper jar + scripts. Preferred: download from the official Gradle repo at tag `v8.9.0`:
```bash
curl -L -o gradle/wrapper/gradle-wrapper.jar https://github.com/gradle/gradle/raw/v8.9.0/gradle/wrapper/gradle-wrapper.jar
curl -L -o gradlew https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradlew
curl -L -o gradlew.bat https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradlew.bat
```
(Make `gradlew` executable on Unix shells: `chmod +x gradlew`.)

**Fallback** (if downloads fail): create the wrapper with a locally installed Gradle (`gradle wrapper --gradle-version 8.9`), or ask the user to scaffold via Android Studio "New Project" and hand the resulting wrapper files over.

**Verify:** `./gradlew --version` prints Gradle 8.9 (first run downloads the distribution; be patient).

### Task 0.2 — Root build files

**Create:** `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Tempus"
include(":app")
```

`build.gradle.kts` (root):
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.5.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
coreKtx = "1.13.1"
lifecycle = "2.8.6"
activityCompose = "1.9.2"
composeBom = "2024.09.03"
navigationCompose = "2.8.1"
room = "2.6.1"
coroutines = "1.9.0"
junit = "4.13.2"
androidxJunit = "1.2.1"
espresso = "3.6.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxJunit" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

**Verify:** `./gradlew help` succeeds (no module yet, so expect the root project only).

### Task 0.3 — app module build file

**Create:** `app/build.gradle.kts`, `app/proguard-rules.pro` (empty comment file)

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.rodneymarin.tempus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rodneymarin.tempus"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

### Task 0.4 — Manifest, resources, launcher icon

**Create:** `app/src/main/AndroidManifest.xml` (note: **no** INTERNET permission):
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".TempusApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Tempus">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Tempus">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**Create** `app/src/main/res/values/strings.xml` (Spanish — add every new string here as the app grows; never hard-code visible text in composables):
```xml
<resources>
    <string name="app_name">Tempus</string>
    <string name="dashboard_title">Mis registros</string>
    <string name="empty_state_title">Aún no tienes registros</string>
    <string name="empty_state_body">Crea el primer evento o hábito que quieras seguir, por ejemplo «Idas al baño», y regístralo cada vez que ocurra.</string>
    <string name="empty_state_action">Crear registro</string>
    <string name="new_tracker">Nuevo registro</string>
    <string name="edit_tracker">Editar registro</string>
    <string name="field_name">Nombre</string>
    <string name="field_name_error">Ponle un nombre al registro</string>
    <string name="field_emoji">Ícono</string>
    <string name="section_expected">Rango esperado (opcional)</string>
    <string name="field_min">Mínimo</string>
    <string name="field_max">Máximo</string>
    <string name="field_period">¿Cada cuánto?</string>
    <string name="period_day">Por día</string>
    <string name="period_week">Por semana</string>
    <string name="period_month">Por mes</string>
    <string name="range_help">Ejemplo: 3–5 veces por semana</string>
    <string name="range_error_min_max">El mínimo no puede ser mayor que el máximo</string>
    <string name="range_error_invalid">Ingresa números enteros mayores a 0</string>
    <string name="save">Guardar</string>
    <string name="cancel">Cancelar</string>
    <string name="delete">Eliminar</string>
    <string name="edit">Editar</string>
    <string name="log_today">Sucedió hoy</string>
    <string name="log_registered">Registrado</string>
    <string name="undo">Deshacer</string>
    <string name="log_another_day">Registrar otro día…</string>
    <string name="date_label">Fecha</string>
    <string name="time_label">Hora (opcional)</string>
    <string name="add_time">Añadir hora</string>
    <string name="no_time">Sin hora</string>
    <string name="register">Registrar</string>
    <string name="status_on_track">En track</string>
    <string name="status_low">Bajo</string>
    <string name="status_high">Alto</string>
    <string name="status_no_range">Sin rango</string>
    <string name="expected_summary">Esperado: %1$s</string>
    <string name="this_period_count">Esta semana: %1$d</string>
    <string name="this_period_count_range">Esta semana: %1$d · esperado %2$s</string>
    <string name="move_to_low">Por debajo del rango</string>
    <string name="move_to_high">Por encima del rango</string>
    <string name="move_to_on_track">Dentro del rango esperado</string>
    <string name="chart_title">Frecuencia</string>
    <string name="heat_title">Últimos 30 días</string>
    <string name="history_title">Historial</string>
    <string name="delete_log_confirm">¿Eliminar este registro?</string>
    <string name="delete_tracker_confirm">¿Eliminar «%1$s» y todo su historial?</string>
    <string name="delete_confirm_action">Eliminar</string>
</resources>
```

**Create** `app/src/main/res/values/themes.xml` (pure-Compose host theme, no action bar):
```xml
<resources>
    <style name="Theme.Tempus" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

**Launcher icon:** create a simple adaptive clock glyph. Files:
- `res/values/ic_launcher_background.xml` → `<color name="ic_launcher_background">#006A5C</color>`
- `res/drawable/ic_launcher_foreground.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M54,28a26,26 0 1,0 0.01,0z"
        android:strokeColor="#FFFFFF" android:strokeWidth="4" android:fillColor="#00000000"/>
    <path android:pathData="M54,38 L54,54 L66,62" android:strokeColor="#FFFFFF"
        android:strokeWidth="4" android:strokeLineCap="round" android:fillColor="#00000000"/>
</vector>
```
- `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`:
```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

### Task 0.5 — App skeleton compiles and installs

**Create:** `TempusApp.kt`, `MainActivity.kt`, `ui/theme/Color.kt`, `ui/theme/Type.kt`, `ui/theme/Theme.kt`, and a temporary `ui/navigation/TempusNavHost.kt` rendering a placeholder screen with the app title.

`ui/theme/Color.kt` (brand + status palette):
```kotlin
package com.rodneymarin.tempus.ui.theme

import androidx.compose.ui.graphics.Color

val TealPrimary = Color(0xFF006A5C)
val TealContainer = Color(0xFF74F8E0)
val TealDark = Color(0xFF4DD9C5)
val StatusGreen = Color(0xFF2E7D32)
val StatusAmber = Color(0xFFF57C00)
val StatusRed = Color(0xFFC62828)
```

`ui/theme/Theme.kt`:
```kotlin
package com.rodneymarin.tempus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    primaryContainer = TealContainer,
)
private val DarkColors = darkColorScheme(
    primary = TealDark,
    primaryContainer = TealContainer,
)

@Composable
fun TempusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
```
(`Type.kt` is an empty object file — default M3 typography is used.)

`TempusApp.kt`:
```kotlin
package com.rodneymarin.tempus

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rodneymarin.tempus.data.TempusDatabase
import com.rodneymarin.tempus.data.TrackersRepository

class TempusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val database: TempusDatabase = RoomBuilder.build(context)
    val repository: TrackersRepository = TrackersRepository(database)
}

object RoomBuilder { // defined in Phase 1; placeholder returning database
    fun build(context: Context): TempusDatabase = TODO()
}

val AppViewModelProvider = viewModelFactory {
    initializer { }
}
```
> Note: `RoomBuilder`/`AppViewModelProvider` are completed in Phase 1/3; keep them compiling by stubbing until then.

`MainActivity.kt`:
```kotlin
package com.rodneymarin.tempus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rodneymarin.tempus.ui.navigation.TempusNavHost
import com.rodneymarin.tempus.ui.theme.TempusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TempusTheme {
                TempusNavHost()
            }
        }
    }
}
```

**Verify:**
1. `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**.
2. With a device/emulator connected (`adb devices` shows one): `./gradlew :app:installDebug`, launch, screenshot shows the placeholder screen.
3. Commit.

## Phase 1 — Data layer (Room)

### Task 1.1 — Domain enum + entities + converters

**Create:** `domain/FrequencyPeriod.kt`, `data/Tracker.kt`, `data/LogEntry.kt`, `data/Converters.kt`

`domain/FrequencyPeriod.kt`:
```kotlin
package com.rodneymarin.tempus.domain

enum class FrequencyPeriod { DAY, WEEK, MONTH }
```

`data/Tracker.kt`:
```kotlin
package com.rodneymarin.tempus.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rodneymarin.tempus.domain.FrequencyPeriod

@Entity(tableName = "trackers")
data class Tracker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "✅",
    val minFrequency: Int? = null,
    val maxFrequency: Int? = null,
    val period: FrequencyPeriod = FrequencyPeriod.WEEK,
    val createdAt: Long = System.currentTimeMillis(),
)
```

`data/LogEntry.kt`:
```kotlin
package com.rodneymarin.tempus.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "log_entries",
    foreignKeys = [ForeignKey(
        entity = Tracker::class,
        parentColumns = ["id"],
        childColumns = ["trackerId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("trackerId"), Index("epochDay")],
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackerId: Long,
    val epochDay: Long,
    val timeMinutes: Int? = null,
)
```

`data/Converters.kt`:
```kotlin
package com.rodneymarin.tempus.data

import androidx.room.TypeConverter
import com.rodneymarin.tempus.domain.FrequencyPeriod

class Converters {
    @TypeConverter fun periodToString(p: FrequencyPeriod): String = p.name
    @TypeConverter fun stringToPeriod(s: String): FrequencyPeriod = FrequencyPeriod.valueOf(s)
}
```

### Task 1.2 — DAOs

**Create:** `data/TrackerDao.kt`, `data/LogEntryDao.kt`

`data/TrackerDao.kt`:
```kotlin
package com.rodneymarin.tempus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {
    @Query("SELECT * FROM trackers ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<Tracker>>

    @Query("SELECT * FROM trackers WHERE id = :id")
    fun observeById(id: Long): Flow<Tracker?>

    @Insert
    suspend fun insert(tracker: Tracker): Long

    @Update
    suspend fun update(tracker: Tracker)

    @Query("DELETE FROM trackers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

`data/LogEntryDao.kt`:
```kotlin
package com.rodneymarin.tempus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries WHERE trackerId = :trackerId ORDER BY epochDay DESC, timeMinutes DESC")
    fun observeByTracker(trackerId: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries")
    fun observeAll(): Flow<List<LogEntry>>

    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

### Task 1.3 — Database + RoomBuilder + Repository

**Create:** `data/TempusDatabase.kt`, `data/TrackersRepository.kt`; **modify:** `TempusApp.kt` (fill `RoomBuilder`)

`data/TempusDatabase.kt`:
```kotlin
package com.rodneymarin.tempus.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context

@Database(entities = [Tracker::class, LogEntry::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class TempusDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao
    abstract fun logEntryDao(): LogEntryDao
}

object RoomBuilder {
    fun build(context: Context): TempusDatabase =
        Room.databaseBuilder(context, TempusDatabase::class.java, "tempus.db").build()
}
```
(Delete the stub `RoomBuilder` from `TempusApp.kt`; the real one lives here.)

`data/TrackersRepository.kt`:
```kotlin
package com.rodneymarin.tempus.data

import com.rodneymarin.tempus.domain.FrequencyPeriod
import kotlinx.coroutines.flow.Flow

class TrackersRepository(private val db: TempusDatabase) {
    private val trackerDao = db.trackerDao()
    private val logDao = db.logEntryDao()

    val trackers: Flow<List<Tracker>> = trackerDao.observeAll()
    val allLogs: Flow<List<LogEntry>> = logDao.observeAll()
    fun logsFor(trackerId: Long): Flow<List<LogEntry>> = logDao.observeByTracker(trackerId)

    suspend fun createTracker(
        name: String, emoji: String,
        min: Int?, max: Int?, period: FrequencyPeriod,
    ): Long = trackerDao.insert(
        Tracker(name = name.trim(), emoji = emoji, minFrequency = min, maxFrequency = max, period = period)
    )

    suspend fun updateTracker(tracker: Tracker) = trackerDao.update(tracker)

    suspend fun deleteTracker(id: Long) = trackerDao.deleteById(id)

    suspend fun logEvent(trackerId: Long, epochDay: Long, timeMinutes: Int?): Long =
        logDao.insert(LogEntry(trackerId = trackerId, epochDay = epochDay, timeMinutes = timeMinutes))

    suspend fun deleteLog(id: Long) = logDao.deleteById(id)
}
```

### Task 1.4 — Instrumented DAO test (optional but recommended; needs device/emulator)

**Create:** `androidTest/java/com/rodneymarin/tempus/data/TrackerDaoTest.kt`

```kotlin
package com.rodneymarin.tempus.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rodneymarin.tempus.domain.FrequencyPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerDaoTest {
    private lateinit var db: TempusDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), TempusDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun teardown() = db.close()

    @Test fun insertAndObserveTracker() = runBlocking {
        val id = db.trackerDao().insert(
            Tracker(name = "Idas al baño", minFrequency = 3, maxFrequency = 5, period = FrequencyPeriod.WEEK)
        )
        val t = db.trackerDao().observeById(id).first()!!
        assertEquals("Idas al baño", t.name)
        assertEquals(3, t.minFrequency)
    }

    @Test fun cascadeDeleteRemovesLogs() = runBlocking {
        val trackerId = db.trackerDao().insert(Tracker(name = "A"))
        db.logEntryDao().insert(LogEntry(trackerId = trackerId, epochDay = 20000))
        db.logEntryDao().insert(LogEntry(trackerId = trackerId, epochDay = 20001))
        db.trackerDao().deleteById(trackerId)
        assertTrue(db.logEntryDao().observeByTracker(trackerId).first().isEmpty())
    }

    @Test fun logsOrderedNewestFirst() = runBlocking {
        val trackerId = db.trackerDao().insert(Tracker(name = "B"))
        db.logEntryDao().insert(LogEntry(trackerId = trackerId, epochDay = 100, timeMinutes = 60))
        db.logEntryDao().insert(LogEntry(trackerId = trackerId, epochDay = 200, timeMinutes = null))
        val logs = db.logEntryDao().observeByTracker(trackerId).first()
        assertEquals(200, logs[0].epochDay)
        assertEquals(100, logs[1].epochDay)
    }
}
```

**Verify:** `./gradlew :app:connectedDebugAndroidTest` → all 3 tests pass. If no device is available, defer this task to the end of Phase 6 and note it.

## Phase 2 — Stats engine (pure Kotlin, TDD)

> This is the mathematical heart of the app. It has **no Android/Room imports** — only `LogEntry` field access (plain data class) — so it runs as a plain JVM unit test. Follow strict TDD: write the test, watch it fail, implement, watch it pass.

### Task 2.1 — FrequencyRange parsing + validation (TDD)

**Create:** `domain/FrequencyRange.kt`, `test/.../domain/FrequencyRangeTest.kt`

**Step 1 — write failing test** `FrequencyRangeTest.kt`:
```kotlin
package com.rodneymarin.tempus.domain

import com.rodneymarin.tempus.domain.FrequencyRange.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyRangeTest {

    private fun valid(min: Int?, max: Int?) =
        FrequencyRange.Result.Valid(min, max)

    @Test fun bothEmpty_isValidNullRange() {
        assertEquals(valid(null, null), FrequencyRange.parse("", ""))
        assertEquals(valid(null, null), FrequencyRange.parse("  ", "  "))
    }

    @Test fun onlyMin_isValid() {
        assertEquals(valid(3, null), FrequencyRange.parse("3", ""))
    }

    @Test fun onlyMax_isValid() {
        assertEquals(valid(null, 5), FrequencyRange.parse("", "5"))
    }

    @Test fun minAndMax_isValid() {
        assertEquals(valid(3, 5), FrequencyRange.parse("3", "5"))
    }

    @Test fun minGreaterThanMax_invalid() {
        assertTrue(FrequencyRange.parse("5", "3") is Result.MinGreaterThanMax)
    }

    @Test fun zeroOrNegative_invalid() {
        assertTrue(FrequencyRange.parse("0", "") is Result.InvalidNumber)
        assertTrue(FrequencyRange.parse("", "-1") is Result.InvalidNumber)
    }

    @Test fun nonNumeric_invalid() {
        assertTrue(FrequencyRange.parse("abc", "") is Result.InvalidNumber)
    }
}
```

**Step 2 — run:** `./gradlew :app:testDebugUnitTest --tests "*FrequencyRangeTest*"` → **FAIL** (class not found).

**Step 3 — implement** `domain/FrequencyRange.kt`:
```kotlin
package com.rodneymarin.tempus.domain

object FrequencyRange {

    sealed interface Result {
        data class Valid(val min: Int?, val max: Int?) : Result
        data object InvalidNumber : Result
        data object MinGreaterThanMax : Result
    }

    /** Empty text → null bound. Bounds must be integers ≥ 1; min ≤ max when both present. */
    fun parse(minText: String, maxText: String): Result {
        val min = minText.trim().ifEmpty { null }?.toIntOrNull()
        val max = maxText.trim().ifEmpty { null }?.toIntOrNull()
        if (minText.trim().isNotEmpty() && min == null) return Result.InvalidNumber
        if (maxText.trim().isNotEmpty() && max == null) return Result.InvalidNumber
        if (min != null && min < 1) return Result.InvalidNumber
        if (max != null && max < 1) return Result.InvalidNumber
        if (min != null && max != null && min > max) return Result.MinGreaterThanMax
        return Result.Valid(min, max)
    }

    /** Human summary, e.g. "3–5", "al menos 3", "máximo 5", "—" (no range). */
    fun summary(min: Int?, max: Int?): String = when {
        min != null && max != null -> "$min–$max"
        min != null -> "al menos $min"
        max != null -> "máximo $max"
        else -> "—"
    }
}
```

**Step 4 — run again:** all 7 tests **PASS**. Commit.

### Task 2.2 — StatsCalculator period math + status (TDD)

**Create:** `domain/StatsCalculator.kt`, `test/.../domain/StatsCalculatorTest.kt`

**Step 1 — write failing test** `StatsCalculatorTest.kt`. Fixed "today" for determinism: **Monday 2026-08-10** (ISO week Mon 10 – Sun 16; month = Aug 1–31).
```kotlin
package com.rodneymarin.tempus.domain

import com.rodneymarin.tempus.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsCalculatorTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 10) // Monday
    private fun log(epochDay: Long) = LogEntry(id = 0, trackerId = 0, epochDay = epochDay)
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    // ---- period boundaries ----

    @Test fun weekStart_isMonday() {
        assertEquals(LocalDate.of(2026, 8, 10), StatsCalculator.periodStart(day(2026, 8, 10), FrequencyPeriod.WEEK))
        assertEquals(LocalDate.of(2026, 8, 10), StatsCalculator.periodStart(day(2026, 8, 14), FrequencyPeriod.WEEK))
    }

    @Test fun weekEnd_isSunday() {
        assertEquals(LocalDate.of(2026, 8, 16), StatsCalculator.periodEnd(day(2026, 8, 12), FrequencyPeriod.WEEK))
    }

    @Test fun monthBoundaries() {
        assertEquals(LocalDate.of(2026, 8, 1), StatsCalculator.periodStart(today, FrequencyPeriod.MONTH))
        assertEquals(LocalDate.of(2026, 8, 31), StatsCalculator.periodEnd(today, FrequencyPeriod.MONTH))
    }

    @Test fun dayBoundaries_equalToday() {
        assertEquals(today, StatsCalculator.periodStart(today, FrequencyPeriod.DAY))
        assertEquals(today, StatsCalculator.periodEnd(today, FrequencyPeriod.DAY))
    }

    // ---- status (projected pace) ----

    private fun tracker(min: Int?, max: Int?, period: FrequencyPeriod = FrequencyPeriod.WEEK) =
        Triple(min, max, period)

    private fun status(logs: List<LogEntry>, t: Triple<Int?, Int?, FrequencyPeriod>): StatsCalculator.TrackStatus =
        StatsCalculator.statusFor(logs, t.first, t.second, t.third, today)

    @Test fun mondayTwoLogs_ofThreeToFivePerWeek_isHigh() {
        // 2 logs on day 1/7 → projected 14 → exceeds 5
        val t = tracker(3, 5)
        val s = status(listOf(log(day(2026, 8, 10)), log(day(2026, 8, 10))), t)
        assertEquals(StatsCalculator.Status.HIGH, s.status)
        assertEquals(2, s.count)
        assertEquals(14, s.projected)
    }

    @Test fun mondayNoLogs_isLow() {
        val t = tracker(3, 5)
        val s = status(emptyList(), t)
        assertEquals(StatsCalculator.Status.LOW, s.status)
        assertEquals(0, s.projected)
    }

    @Test fun fridayThreeLogs_ofThreeToFive_isOnTrack() {
        // Friday 2026-08-14: elapsed 5/7 → projected 3/(5/7)=4.2 → 4 ∈ [3,5]
        val t = tracker(3, 5)
        val s = status(listOf(log(day(2026, 8, 14)), log(day(2026, 8, 14)), log(day(2026, 8, 14))), t)
        assertEquals(StatsCalculator.Status.ON_TRACK, s.status)
        assertEquals(4, s.projected)
    }

    @Test fun maxOnly_threeLogsFriday_isHigh() {
        val t = tracker(null, 2)
        val s = status(listOf(log(day(2026, 8, 14)), log(day(2026, 8, 14)), log(day(2026, 8, 14))), t)
        assertEquals(StatsCalculator.Status.HIGH, s.status)
    }

    @Test fun minOnly_oneLogFriday_isLow() {
        val t = tracker(3, null)
        val s = status(listOf(log(day(2026, 8, 14))), t)
        assertEquals(StatsCalculator.Status.LOW, s.status)
    }

    @Test fun noRange_alwaysNoRange() {
        val t = tracker(null, null)
        assertEquals(StatsCalculator.Status.NO_RANGE, status(emptyList(), t).status)
        assertEquals(StatsCalculator.Status.NO_RANGE, status(listOf(log(day(2026, 8, 10))), t).status)
    }

    // ---- history ----

    @Test fun lastPeriods_weekly_returnsEightPointsWithCounts() {
        val logs = listOf(
            log(day(2026, 8, 10)),                       // current week
            log(day(2026, 8, 3)), log(day(2026, 8, 4)),  // previous week
            log(day(2026, 8, 1)),                        // two weeks back
        )
        val points = StatsCalculator.lastPeriods(logs, FrequencyPeriod.WEEK, today)
        assertEquals(8, points.size)
        assertEquals(1, points.last().count)
        assertEquals(2, points[points.size - 2].count)
        assertEquals(1, points[points.size - 3].count)
        assertEquals(0, points.first().count)
        assertEquals(LocalDate.of(2026, 8, 10).toEpochDay(), points.last().startEpochDay)
        assertEquals(LocalDate.of(2026, 8, 16).toEpochDay(), points.last().endEpochDay)
    }

    @Test fun dailyCounts_returns35DaysWithPerDayCounts() {
        val logs = listOf(log(day(2026, 8, 10)), log(day(2026, 8, 10)), log(day(2026, 8, 9)))
        val counts = StatsCalculator.dailyCounts(logs, today = today)
        assertEquals(35, counts.size)
        assertEquals(2, counts[LocalDate.of(2026, 8, 10)])
        assertEquals(1, counts[LocalDate.of(2026, 8, 9)])
        assertEquals(0, counts[LocalDate.of(2026, 8, 6)])
        assertEquals(LocalDate.of(2026, 7, 7), counts.keys.first()) // oldest day
    }

    @Test fun dayLabel_todayIsHoy() {
        assertEquals("Hoy", StatsCalculator.labelShort(today, today, FrequencyPeriod.DAY, today))
    }
}
```

**Step 2 — run:** `./gradlew :app:testDebugUnitTest --tests "*StatsCalculatorTest*"` → **FAIL** (class not found).

**Step 3 — implement** `domain/StatsCalculator.kt`:
```kotlin
package com.rodneymarin.tempus.domain

import com.rodneymarin.tempus.data.LogEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure frequency math: calendar period boundaries, counts, projected-pace status.
 * No Android dependencies — unit-testable on the JVM.
 */
object StatsCalculator {

    fun periodStart(day: LocalDate, period: FrequencyPeriod): LocalDate = when (period) {
        FrequencyPeriod.DAY -> day
        FrequencyPeriod.WEEK -> day.with(DayOfWeek.MONDAY)
        FrequencyPeriod.MONTH -> day.withDayOfMonth(1)
    }

    fun periodEnd(day: LocalDate, period: FrequencyPeriod): LocalDate = when (period) {
        FrequencyPeriod.DAY -> day
        FrequencyPeriod.WEEK -> day.with(DayOfWeek.SUNDAY)
        FrequencyPeriod.MONTH -> day.with(TemporalAdjusters.lastDayOfMonth())
    }

    fun countInRange(logs: List<LogEntry>, startEpochDay: Long, endEpochDay: Long): Int =
        logs.count { it.epochDay in startEpochDay..endEpochDay }

    enum class Status { ON_TRACK, LOW, HIGH, NO_RANGE }

    data class TrackStatus(val count: Int, val projected: Int, val status: Status)

    /**
     * Status of the CURRENT calendar period. Compares the projected end-of-period
     * count (count ÷ elapsed fraction) against the range, so early-period counts
     * are judged by pace, not by raw total.
     */
    fun statusFor(
        logs: List<LogEntry>,
        min: Int?,
        max: Int?,
        period: FrequencyPeriod,
        today: LocalDate = LocalDate.now(),
    ): TrackStatus {
        val start = periodStart(today, period).toEpochDay()
        val end = periodEnd(today, period).toEpochDay()
        val count = countInRange(logs, start, end)
        val totalDays = (end - start + 1).toDouble()
        val elapsedDays = ((today.toEpochDay() - start).coerceAtLeast(0) + 1).toDouble()
        val progress = (elapsedDays / totalDays).coerceIn(0.0, 1.0)
        val projected = if (progress > 0) (count / progress).roundToInt() else count
        val status = when {
            min == null && max == null -> Status.NO_RANGE
            max != null && projected > max -> Status.HIGH
            min != null && projected < min -> Status.LOW
            else -> Status.ON_TRACK
        }
        return TrackStatus(count = count, projected = projected, status = status)
    }

    data class PeriodPoint(
        val label: String,
        val startEpochDay: Long,
        val endEpochDay: Long,
        val count: Int,
    )

    /** Last N calendar periods (8; 14 for daily), oldest → newest. */
    fun lastPeriods(
        logs: List<LogEntry>,
        period: FrequencyPeriod,
        today: LocalDate = LocalDate.now(),
    ): List<PeriodPoint> {
        val n = if (period == FrequencyPeriod.DAY) 14 else 8
        val currentStart = periodStart(today, period)
        return (n - 1 downTo 0).map { offset ->
            val anchor = when (period) {
                FrequencyPeriod.DAY -> today.minusDays(offset.toLong())
                FrequencyPeriod.WEEK -> currentStart.minusWeeks(offset.toLong())
                FrequencyPeriod.MONTH -> currentStart.minusMonths(offset.toLong())
            }
            val start = periodStart(anchor, period)
            val end = periodEnd(anchor, period)
            PeriodPoint(
                label = labelShort(start, end, period, today),
                startEpochDay = start.toEpochDay(),
                endEpochDay = end.toEpochDay(),
                count = countInRange(logs, start.toEpochDay(), end.toEpochDay()),
            )
        }
    }

    fun labelShort(
        start: LocalDate,
        end: LocalDate,
        period: FrequencyPeriod,
        today: LocalDate,
        locale: Locale = Locale("es"),
    ): String = when (period) {
        FrequencyPeriod.DAY ->
            if (start == today) "Hoy"
            else start.format(DateTimeFormatter.ofPattern("d MMM", locale))
        FrequencyPeriod.WEEK -> {
            val s = start.format(DateTimeFormatter.ofPattern("d MMM", locale))
            val e = end.format(DateTimeFormatter.ofPattern("d MMM", locale))
            "$s – $e"
        }
        FrequencyPeriod.MONTH ->
            start.month.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase(locale) }
    }

    /** Per-day counts for the last [days] days, oldest → newest. */
    fun dailyCounts(
        logs: List<LogEntry>,
        days: Int = 35,
        today: LocalDate = LocalDate.now(),
    ): Map<LocalDate, Int> =
        (days - 1 downTo 0).associate { offset ->
            val day = today.minusDays(offset.toLong())
            day to countInRange(logs, day.toEpochDay(), day.toEpochDay())
        }
}
```

**Step 4 — run:** `./gradlew :app:testDebugUnitTest` → **all 15 tests PASS**. Commit.

> `TrackStatus.count` for the "Esta semana: N" subtitle; `projected` is only used internally/for tooltips.

## Phase 3 — Dashboard (list, status chips, quick log)

### Task 3.1 — Navigation scaffold + ViewModel factory

**Create:** `ui/navigation/Routes.kt`, `ui/navigation/TempusNavHost.kt`; **modify:** `TempusApp.kt` (factory helper)

`ui/navigation/Routes.kt`:
```kotlin
package com.rodneymarin.tempus.ui.navigation

object Routes {
    const val DASHBOARD = "dashboard"
    const val EDITOR = "tracker/edit?trackerId={trackerId}"
    const val DETAIL = "tracker/{trackerId}"

    fun editor(trackerId: Long? = null): String =
        if (trackerId == null) "tracker/edit" else "tracker/edit?trackerId=$trackerId"
    fun detail(trackerId: Long): String = "tracker/$trackerId"
}
```

Add to `TempusApp.kt` (after `AppContainer`):
```kotlin
fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TempusApp).container
```
(imports: `androidx.lifecycle.ViewModelProvider`, `androidx.lifecycle.viewmodel.CreationExtras`). Remove the placeholder `AppViewModelProvider` from Phase 0 — each ViewModel exposes its own `Factory` (pattern below).

`ui/navigation/TempusNavHost.kt` — full navigation with placeholder screens for editor/detail (replaced in Phases 4–5):
```kotlin
package com.rodneymarin.tempus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rodneymarin.tempus.ui.dashboard.DashboardScreen
import com.rodneymarin.tempus.ui.detail.TrackerDetailScreen
import com.rodneymarin.tempus.ui.editor.TrackerEditorScreen

@Composable
fun TempusNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenTracker = { id -> navController.navigate(Routes.detail(id)) },
                onCreateTracker = { navController.navigate(Routes.editor()) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("trackerId") { type = NavType.LongType }),
        ) { entry ->
            val trackerId = entry.arguments?.getLong("trackerId") ?: return@composable
            TrackerDetailScreen(
                trackerId = trackerId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.editor(trackerId)) },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("trackerId") {
                type = NavType.LongType
                defaultValue = -1L
            }),
        ) { entry ->
            val trackerId = entry.arguments?.getLong("trackerId")?.takeIf { it > 0 }
            TrackerEditorScreen(
                trackerId = trackerId,
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
```
Create empty placeholder composables `DashboardScreen`/`TrackerEditorScreen`/`TrackerDetailScreen` (a `Scaffold` with a `Text`) so this compiles; they are implemented in the following tasks. **Verify:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. Commit.

### Task 3.2 — DashboardViewModel

**Create:** `ui/dashboard/DashboardViewModel.kt`

```kotlin
package com.rodneymarin.tempus.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rodneymarin.tempus.TempusApp
import com.rodneymarin.tempus.appContainer
import com.rodneymarin.tempus.data.Tracker
import com.rodneymarin.tempus.data.TrackersRepository
import com.rodneymarin.tempus.domain.FrequencyRange
import com.rodneymarin.tempus.domain.StatsCalculator
import com.rodneymarin.tempus.domain.StatsCalculator.Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class TrackerRow(
    val tracker: Tracker,
    val countInPeriod: Int,
    val expectedSummary: String,
    val status: Status,
)

/** Emitted after a quick log so the UI can show "Registrado — Deshacer". */
data class LogConfirmation(val trackerId: Long, val insertedId: Long)

class DashboardViewModel(private val repo: TrackersRepository) : ViewModel() {

    val rows: StateFlow<List<TrackerRow>> =
        combine(repo.trackers, repo.allLogs) { trackers, logs ->
            val today = LocalDate.now()
            trackers.map { t ->
                val trackerLogs = logs.filter { it.trackerId == t.id }
                val status = StatsCalculator.statusFor(
                    trackerLogs, t.minFrequency, t.maxFrequency, t.period, today,
                )
                TrackerRow(
                    tracker = t,
                    countInPeriod = status.count,
                    expectedSummary = FrequencyRange.summary(t.minFrequency, t.maxFrequency),
                    status = status.status,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lastLog = MutableStateFlow<LogConfirmation?>(null)
    val lastLog: StateFlow<LogConfirmation?> = _lastLog

    /** Debounce: ignore taps while the previous insert is still being undone. */
    fun logToday(trackerId: Long) {
        viewModelScope.launch {
            val now = LocalTime.now()
            val id = repo.logEvent(
                trackerId = trackerId,
                epochDay = LocalDate.now().toEpochDay(),
                timeMinutes = now.hour * 60 + now.minute,
            )
            _lastLog.value = LogConfirmation(trackerId, id)
        }
    }

    fun undoLastLog() {
        _lastLog.value?.let { conf ->
            viewModelScope.launch { repo.deleteLog(conf.insertedId) }
        }
        _lastLog.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TempusApp
                DashboardViewModel(app.container.repository)
            }
        }
    }
}
```
> Note: `combine` re-runs on any data change, so `LocalDate.now()` is re-read then; a tracker list whose data hasn't changed overnight is a non-issue for v1 (documented in Risks).

### Task 3.3 — StatusChip + TrackerCard

**Create:** `ui/dashboard/StatusChip.kt`, `ui/dashboard/TrackerCard.kt`

`StatusChip.kt`:
```kotlin
package com.rodneymarin.tempus.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.domain.StatsCalculator.Status
import com.rodneymarin.tempus.ui.theme.StatusAmber
import com.rodneymarin.tempus.ui.theme.StatusGreen
import com.rodneymarin.tempus.ui.theme.StatusRed

@Composable
fun StatusChip(status: Status, modifier: Modifier = Modifier) {
    val labelRes: Int
    val color: Color
    when (status) {
        Status.ON_TRACK -> { labelRes = R.string.status_on_track; color = StatusGreen }
        Status.LOW -> { labelRes = R.string.status_low; color = StatusAmber }
        Status.HIGH -> { labelRes = R.string.status_high; color = StatusRed }
        Status.NO_RANGE -> { labelRes = R.string.status_no_range; color = MaterialTheme.colorScheme.outline }
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium)
        }
    }
}
```

`TrackerCard.kt`:
```kotlin
package com.rodneymarin.tempus.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.data.Tracker
import com.rodneymarin.tempus.domain.FrequencyPeriod

@Composable
fun TrackerCard(
    row: TrackerRow,
    onOpen: () -> Unit,
    onLogToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(row.tracker.emoji, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.tracker.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    periodSummary(row.tracker, row.countInPeriod, row.expectedSummary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(row.status)
                IconButton(onClick = onLogToday, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.log_today),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun periodSummary(tracker: Tracker, count: Int, expected: String): String {
    val prefix = when (tracker.period) {
        FrequencyPeriod.DAY -> stringResource(R.string.period_prefix_day)
        FrequencyPeriod.WEEK -> stringResource(R.string.period_prefix_week)
        FrequencyPeriod.MONTH -> stringResource(R.string.period_prefix_month)
    }
    val hasRange = tracker.minFrequency != null || tracker.maxFrequency != null
    return if (hasRange) {
        stringResource(R.string.this_period_count_range, count, expected)
            .replaceFirst("%1\$s", prefix) // see note
    } else {
        stringResource(R.string.this_period_count, count).replaceFirst("%1\$s", prefix)
    }
}
```
> Note: `this_period_count` and `this_period_count_range` already contain "Esta semana:" — change them to a bare `%1$d` (count) and `%1$d · esperado %2$s` (count + expected) and let `periodSummary` supply the prefix. Simpler and cleaner: update the two strings to `"%1$s %2$d"` and `"%1$s %2$d · esperado %3$s"` with prefix first, and pass `prefix` directly. Implement the latter — no `replaceFirst` hackery.

**Update `strings.xml`** — add:
```xml
<string name="period_prefix_day">Hoy:</string>
<string name="period_prefix_week">Esta semana:</string>
<string name="period_prefix_month">Este mes:</string>
```
and change the two summary strings to:
```xml
<string name="this_period_count">%1$s %2$d</string>
<string name="this_period_count_range">%1$s %2$d · esperado %3$s</string>
```
Adjust `TrackerCard` to `stringResource(R.string.this_period_count_range, prefix, count, expected)`.

### Task 3.4 — DashboardScreen (list, empty state, FAB, snackbar undo)

**Create:** `ui/dashboard/DashboardScreen.kt`

```kotlin
package com.rodneymarin.tempus.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodneymarin.tempus.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenTracker: (Long) -> Unit,
    onCreateTracker: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val lastLog by viewModel.lastLog.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(lastLog) {
        if (lastLog != null) {
            val result = snackbarHostState.showSnackbar(
                message = stringResource(R.string.log_registered),
                actionLabel = stringResource(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.undoLastLog()
            } else {
                viewModel.undoLastLog() // clear pending state either way
            }
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.dashboard_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateTracker) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_tracker))
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(onCreateTracker, Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(rows, key = { it.tracker.id }) { row ->
                    TrackerCard(
                        row = row,
                        onOpen = { onOpenTracker(row.tracker.id) },
                        onLogToday = { viewModel.logToday(row.tracker.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp),
    ) {
        Text("⏳", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_state_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_state_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCreate) { Text(stringResource(R.string.empty_state_action)) }
    }
}
```

**Verify on device:** `./gradlew :app:installDebug`; launch → empty state; create nothing yet (editor is placeholder — skip FAB for now). **Commit.**

## Phase 4 — Tracker editor (create / edit)

### Task 4.1 — EditorViewModel with validation

**Create:** `ui/editor/TrackerEditorViewModel.kt`

```kotlin
package com.rodneymarin.tempus.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rodneymarin.tempus.TempusApp
import com.rodneymarin.tempus.appContainer
import com.rodneymarin.tempus.data.Tracker
import com.rodneymarin.tempus.data.TrackersRepository
import com.rodneymarin.tempus.domain.FrequencyPeriod
import com.rodneymarin.tempus.domain.FrequencyRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class EditorUiState(
    val name: String = "",
    val emoji: String = EMOJI_PRESETS.first(),
    val minText: String = "",
    val maxText: String = "",
    val period: FrequencyPeriod = FrequencyPeriod.WEEK,
    val nameError: Boolean = false,
    val rangeError: Int? = null,   // string res of the range error, null = ok
    val loading: Boolean = true,   // true while loading an existing tracker
)

class TrackerEditorViewModel(
    private val repo: TrackersRepository,
    private val trackerId: Long?,
) : ViewModel() {

    private val _ui = MutableStateFlow(EditorUiState())
    val ui: StateFlow<EditorUiState> = _ui.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done

    init {
        if (trackerId != null) loadTracker(trackerId)
        else _ui.value = _ui.value.copy(loading = false)
    }

    private fun loadTracker(id: Long) {
        viewModelScope.launch {
            val t = repo.trackers.first().find { it.id == id }
            if (t != null) {
                _ui.value = EditorUiState(
                    name = t.name,
                    emoji = t.emoji,
                    minText = t.minFrequency?.toString() ?: "",
                    maxText = t.maxFrequency?.toString() ?: "",
                    period = t.period,
                    loading = false,
                )
            } else {
                _done.value = true // tracker vanished; go back
            }
        }
    }

    fun onNameChange(v: String) = _ui.update { it.copy(name = v, nameError = false) }
    fun onEmojiChange(v: String) = _ui.update { it.copy(emoji = v) }
    fun onMinChange(v: String) = _ui.update { it.copy(minText = v.filter(Char::isDigit), rangeError = null) }
    fun onMaxChange(v: String) = _ui.update { it.copy(maxText = v.filter(Char::isDigit), rangeError = null) }
    fun onPeriodChange(v: FrequencyPeriod) = _ui.update { it.copy(period = v) }

    fun save() {
        val s = _ui.value
        if (s.name.isBlank()) {
            _ui.update { it.copy(nameError = true) }
            return
        }
        when (val r = FrequencyRange.parse(s.minText, s.maxText)) {
            is FrequencyRange.Result.Valid -> {
                viewModelScope.launch {
                    if (trackerId == null) {
                        repo.createTracker(s.name, s.emoji, r.min, r.max, s.period)
                    } else {
                        val existing = repo.trackers.first().find { it.id == trackerId } ?: return@launch
                        repo.updateTracker(
                            existing.copy(
                                name = s.name.trim(), emoji = s.emoji,
                                minFrequency = r.min, maxFrequency = r.max, period = s.period,
                            )
                        )
                    }
                    _done.value = true
                }
            }
            is FrequencyRange.Result.InvalidNumber ->
                _ui.update { it.copy(rangeError = com.rodneymarin.tempus.R.string.range_error_invalid) }
            is FrequencyRange.Result.MinGreaterThanMax ->
                _ui.update { it.copy(rangeError = com.rodneymarin.tempus.R.string.range_error_min_max) }
        }
    }

    companion object {
        val EMOJI_PRESETS = listOf(
            "💧", "🚽", "💊", "🏃", "💪", "😴", "🥗", "🍎",
            "☕", "🚭", "🧘", "🦷", "🥤", "🚿", "🧹", "📵",
            "🎮", "📚", "🧠", "❤️", "🌙", "🚶", "🏋️", "💰",
        )

        fun Factory(trackerId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TempusApp
                TrackerEditorViewModel(app.container.repository, trackerId)
            }
        }
    }
}

private fun MutableStateFlow<EditorUiState>.update(transform: (EditorUiState) -> EditorUiState) {
    value = transform(value)
}
```
> `EmojiPicker` reads `TrackerEditorViewModel.EMOJI_PRESETS` (kept in the VM file to avoid a third file; move to `EmojiPicker.kt` if preferred).

### Task 4.2 — EmojiPicker + editor screen

**Create:** `ui/editor/EmojiPicker.kt`, `ui/editor/TrackerEditorScreen.kt`

`ui/editor/EmojiPicker.kt`:
```kotlin
package com.rodneymarin.tempus.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EmojiPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<String> = TrackerEditorViewModel.EMOJI_PRESETS,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        presets.forEach { emoji ->
            val isSelected = emoji == selected
            Surface(
                onClick = { onSelect(emoji) },
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
```
(imports `androidx.compose.foundation.layout.FlowRow` — available in Compose 1.7+/BOM 2024.09+; if unresolved, use `Row` with two wrapped rows or `FlowRow` from `androidx.compose.foundation.layout.experimental`.)

`ui/editor/TrackerEditorScreen.kt`:
```kotlin
package com.rodneymarin.tempus.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.domain.FrequencyPeriod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerEditorScreen(
    trackerId: Long?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: TrackerEditorViewModel = viewModel(factory = TrackerEditorViewModel.Factory(trackerId)),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()

    LaunchedEffect(done) { if (done) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (trackerId == null) R.string.new_tracker else R.string.edit_tracker)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = ui.name.isNotBlank() && !ui.loading,
                    ) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            OutlinedTextField(
                value = ui.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                isError = ui.nameError,
                supportingText = if (ui.nameError) {
                    { Text(stringResource(R.string.field_name_error)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.field_emoji),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                EmojiPicker(selected = ui.emoji, onSelect = viewModel::onEmojiChange)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.section_expected),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = ui.minText,
                        onValueChange = viewModel::onMinChange,
                        label = { Text(stringResource(R.string.field_min)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = ui.rangeError != null,
                        modifier = Modifier.width(110.dp),
                    )
                    OutlinedTextField(
                        value = ui.maxText,
                        onValueChange = viewModel::onMaxChange,
                        label = { Text(stringResource(R.string.field_max)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = ui.rangeError != null,
                        modifier = Modifier.width(110.dp),
                    )
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    FrequencyPeriod.entries.forEachIndexed { index, period ->
                        SegmentedButton(
                            selected = ui.period == period,
                            onClick = { viewModel.onPeriodChange(period) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = FrequencyPeriod.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (period) {
                                        FrequencyPeriod.DAY -> R.string.period_day
                                        FrequencyPeriod.WEEK -> R.string.period_week
                                        FrequencyPeriod.MONTH -> R.string.period_month
                                    }
                                )
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(
                        ui.rangeError ?: R.string.range_help,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ui.rangeError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
```

**Verify on device:** `./gradlew :app:installDebug`. Flow: FAB → editor → type "Idas al baño", pick 🚽, min 3, max 5, period Semana → Guardar → back on dashboard with the new card (subtitle "Esta semana: 0 · esperado 3–5", chip "Bajo"). Also test validation: blank name blocks Save; min 5 max 3 shows range error; edit mode: open detail → (edit placeholder not wired yet) — at least confirm create path. **Commit.**

## Phase 5 — Tracker detail (log, chart, heat strip, history)

### Task 5.1 — DetailViewModel

**Create:** `ui/detail/TrackerDetailViewModel.kt`

```kotlin
package com.rodneymarin.tempus.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rodneymarin.tempus.TempusApp
import com.rodneymarin.tempus.appContainer
import com.rodneymarin.tempus.data.LogEntry
import com.rodneymarin.tempus.data.Tracker
import com.rodneymarin.tempus.data.TrackersRepository
import com.rodneymarin.tempus.domain.StatsCalculator
import com.rodneymarin.tempus.domain.StatsCalculator.PeriodPoint
import com.rodneymarin.tempus.domain.StatsCalculator.TrackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class DayGroup(val date: LocalDate, val entries: List<LogEntry>)

data class DetailUiState(
    val tracker: Tracker? = null,
    val status: TrackStatus? = null,
    val periodPoints: List<PeriodPoint> = emptyList(),
    val dailyCounts: Map<LocalDate, Int> = emptyMap(),
    val history: List<DayGroup> = emptyList(),
)

data class LogConfirmation(val insertedId: Long)

class TrackerDetailViewModel(
    private val repo: TrackersRepository,
    private val trackerId: Long,
) : ViewModel() {

    val ui: StateFlow<DetailUiState> =
        combine(repo.logsFor(trackerId), repo.trackers) { logs, trackers ->
            val tracker = trackers.find { it.id == trackerId }
            val today = LocalDate.now()
            DetailUiState(
                tracker = tracker,
                status = tracker?.let {
                    StatsCalculator.statusFor(logs, it.minFrequency, it.maxFrequency, it.period, today)
                },
                periodPoints = tracker?.let {
                    StatsCalculator.lastPeriods(logs, it.period, today)
                } ?: emptyList(),
                dailyCounts = StatsCalculator.dailyCounts(logs, today = today),
                history = logs
                    .groupBy { LocalDate.ofEpochDay(it.epochDay) }
                    .map { (date, entries) -> DayGroup(date, entries.sortedByDescending { it.timeMinutes }) }
                    .sortedByDescending { it.date },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailUiState())

    private val _lastLog = MutableStateFlow<LogConfirmation?>(null)
    val lastLog: StateFlow<LogConfirmation?> = _lastLog.asStateFlow()

    private val _exited = MutableStateFlow(false)
    val exited: StateFlow<Boolean> = _exited.asStateFlow()

    fun logToday() {
        val now = LocalTime.now()
        logOn(LocalDate.now(), now.hour * 60 + now.minute)
    }

    /** Register an occurrence on [date]; [timeMinutes] null = "sin hora". */
    fun logOn(date: LocalDate, timeMinutes: Int?) {
        viewModelScope.launch {
            val id = repo.logEvent(trackerId, date.toEpochDay(), timeMinutes)
            _lastLog.value = LogConfirmation(id)
        }
    }

    fun undoLastLog() {
        _lastLog.value?.let { conf -> viewModelScope.launch { repo.deleteLog(conf.insertedId) } }
        _lastLog.value = null
    }

    fun deleteLog(id: Long) = viewModelScope.launch { repo.deleteLog(id) }

    fun deleteTracker() {
        viewModelScope.launch {
            repo.deleteTracker(trackerId)
            _exited.value = true
        }
    }

    companion object {
        fun Factory(trackerId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TempusApp
                TrackerDetailViewModel(app.container.repository, trackerId)
            }
        }
    }
}
```
> Note: `combine` recomputes `LocalDate.now()` per data emission (same accepted limitation as Dashboard).

### Task 5.2 — RegisterEventSheet (past date + optional time)

**Create:** `ui/detail/RegisterEventSheet.kt`

```kotlin
package com.rodneymarin.tempus.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rodneymarin.tempus.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterEventSheet(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, Int?) -> Unit,
) {
    val today = LocalDate.now()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(today) }
    val now = java.time.LocalTime.now()
    val timeState = rememberTimePickerState(
        initialHour = now.hour,
        initialMinute = now.minute,
        is24Hour = true,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
    ) {
        Text(stringResource(R.string.log_another_day), style = MaterialTheme.typography.titleMedium)

        // Date picker trigger
        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", java.util.Locale("es"))))
        }

        // Optional time
        FilterChip(
            selected = showTime,
            onClick = { showTime = !showTime },
            label = { Text(stringResource(R.string.add_time)) },
        )
        if (showTime) {
            TimePicker(state = timeState)
        }

        Button(
            onClick = { onConfirm(selectedDate, if (showTime) timeState.hour * 60 + timeState.minute else null) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.register)) }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDate
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        utcTimeMillis <= today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    override fun isSelectableYear(year: Int): Boolean = year <= today.year
                },
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButtonConfirm {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
private fun TextButtonConfirm(onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(stringResource(R.string.save))
    }
}
```
> ⚠️ Pitfall documented: M3 `DatePicker` works in UTC millis. Convert with `Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()` — never the device zone, or the selected day shifts.

**Verify:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (sheet not yet reachable; compile check only). Commit.

### Task 5.3 — FrequencyChart (custom Canvas: bars + expected band)

**Create:** `ui/detail/FrequencyChart.kt`

```kotlin
package com.rodneymarin.tempus.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.rodneymarin.tempus.domain.StatsCalculator.PeriodPoint

/**
 * Rounded bars = actual count per period; shaded band = expected [min, max]
 * with dashed boundary lines. Draw order: grid → band → bounds → bars → labels.
 * Zero-dependency by design (no chart library).
 */
@Composable
fun FrequencyChart(
    points: List<PeriodPoint>,
    min: Int?,
    max: Int?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val bandColor = MaterialTheme.colorScheme.tertiaryContainer
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        if (points.isEmpty()) return@Canvas

        val maxVal = maxOf(points.maxOf { it.count }, max ?: 0, min ?: 0, 1)
        val leftPad = 4.dp.toPx()
        val rightPad = 4.dp.toPx()
        val topPad = 8.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val chartTop = topPad
        val chartBottom = size.height - bottomPad
        val chartH = chartBottom - chartTop
        val chartW = size.width - leftPad - rightPad
        val slot = chartW / points.size
        val barW = minOf(slot * 0.55f, 26.dp.toPx())

        fun yFor(v: Int): Float = chartBottom - (v.toFloat() / maxVal) * chartH

        // gridlines (0, ¼, ½, ¾, max)
        repeat(5) { i ->
            val v = maxVal * i / 4
            drawLine(
                gridColor, Offset(leftPad, yFor(v)),
                Offset(size.width - rightPad, yFor(v)), 1.dp.toPx(),
            )
        }

        // expected band + dashed bound lines
        if (min != null && max != null) {
            val yMin = yFor(min).coerceIn(chartTop, chartBottom)
            val yMax = yFor(max).coerceIn(chartTop, chartBottom)
            drawRect(
                color = bandColor.copy(alpha = 0.35f),
                topLeft = Offset(leftPad, yMin),
                size = Size(chartW, (yMax - yMin).coerceAtLeast(0f)),
            )
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        if (min != null) {
            val y = yFor(min).coerceIn(chartTop, chartBottom)
            drawLine(bandColor, Offset(leftPad, y), Offset(size.width - rightPad, y), 2.dp.toPx(), pathEffect = dash)
        }
        if (max != null) {
            val y = yFor(max).coerceIn(chartTop, chartBottom)
            drawLine(bandColor, Offset(leftPad, y), Offset(size.width - rightPad, y), 2.dp.toPx(), pathEffect = dash)
        }

        // bars + x labels
        points.forEachIndexed { i, p ->
            val cx = leftPad + slot * i + slot / 2
            val barTop = yFor(p.count)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(cx - barW / 2, barTop),
                size = Size(barW, (chartBottom - barTop).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
            val layout = textMeasurer.measure(p.label, labelStyle)
            val x = (cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, chartBottom + 4.dp.toPx()))
        }
    }
}
```

### Task 5.4 — HeatStrip + LogHistory

**Create:** `ui/detail/HeatStrip.kt`, `ui/detail/LogHistory.kt`

`ui/detail/HeatStrip.kt`:
```kotlin
package com.rodneymarin.tempus.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** 35 day-squares, oldest → newest; fill intensity = occurrences that day (0/1/2+). */
@Composable
fun HeatStrip(
    counts: Map<LocalDate, Int>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            (34 downTo 0).forEach { offset ->
                val day = today.minusDays(offset.toLong())
                val count = counts[day] ?: 0
                val color = when {
                    count == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    count == 1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color),
                )
            }
        }
        // legend
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Menos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(6.dp))
            Text("Más", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

`ui/detail/LogHistory.kt`:
```kotlin
package com.rodneymarin.tempus.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.data.LogEntry
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LogHistory(
    groups: List<DayGroup>,
    onDelete: (LogEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { group ->
            val header = group.date
                .format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es")))
                .replaceFirstChar { it.uppercase() }
            Text(
                header,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            group.entries.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                ) {
                    Text(
                        entry.timeMinutes?.let { "%02d:%02d".format(it / 60, it % 60) }
                            ?: stringResource(R.string.no_time),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
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

### Task 5.5 — TrackerDetailScreen (full wiring + delete confirmations)

**Create:** `ui/detail/TrackerDetailScreen.kt`

**Update `strings.xml`** — add:
```xml
<string name="unit_day">por día</string>
<string name="unit_week">por semana</string>
<string name="unit_month">por mes</string>
<string name="history_empty">Sin registros aún</string>
```

**Screen** (structure + key parts; wire exactly like this):
```kotlin
package com.rodneymarin.tempus.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rodneymarin.tempus.R
import com.rodneymarin.tempus.data.LogEntry
import com.rodneymarin.tempus.domain.FrequencyPeriod
import com.rodneymarin.tempus.domain.FrequencyRange
import com.rodneymarin.tempus.domain.StatsCalculator.Status
import com.rodneymarin.tempus.ui.dashboard.StatusChip
import com.rodneymarin.tempus.ui.theme.StatusAmber
import com.rodneymarin.tempus.ui.theme.StatusGreen
import com.rodneymarin.tempus.ui.theme.StatusRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailScreen(
    trackerId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: TrackerDetailViewModel = viewModel(factory = TrackerDetailViewModel.Factory(trackerId)),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val lastLog by viewModel.lastLog.collectAsStateWithLifecycle()
    val exited by viewModel.exited.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSheet by remember { mutableStateOf(false) }
    var logToDelete by remember { mutableStateOf<LogEntry?>(null) }
    var confirmDeleteTracker by remember { mutableStateOf(false) }

    LaunchedEffect(exited) { if (exited) onBack() }

    LaunchedEffect(lastLog) {
        if (lastLog != null) {
            val result = snackbarHostState.showSnackbar(
                message = stringResource(R.string.log_registered),
                actionLabel = stringResource(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            viewModel.undoLastLog() // clears pending state; deletes only if action pressed
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLastLog()
        }
    }
```
> ⚠️ Correction to the snackbar block above: call `undoLastLog()` **once**, and only when `ActionPerformed`:
> ```kotlin
> if (result == SnackbarResult.ActionPerformed) viewModel.undoLastLog()
> else viewModel.clearLastLog()   // add fun clearLastLog() { _lastLog.value = null }
> ```
> Implement `clearLastLog()` in the ViewModel and use the corrected block.

```kotlin
    val tracker = ui.tracker
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tracker?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit, enabled = tracker != null) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = { confirmDeleteTracker = true }, enabled = tracker != null) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (tracker == null) return@Scaffold

        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tracker.emoji, style = MaterialTheme.typography.displaySmall)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tracker.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(
                            R.string.expected_summary,
                            "${FrequencyRange.summary(tracker.minFrequency, tracker.maxFrequency)} ${
                                unitLabel(tracker.period)
                            }",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Status row
            ui.status?.let { st ->
                StatusChip(st.status)
                Text(
                    when (st.status) {
                        Status.ON_TRACK -> stringResource(R.string.move_to_on_track)
                        Status.LOW -> stringResource(R.string.move_to_low)
                        Status.HIGH -> stringResource(R.string.move_to_high)
                        Status.NO_RANGE -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = statusHintColor(st.status),
                )
            }

            // Actions
            Button(
                onClick = viewModel::logToday,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.log_today))
            }
            OutlinedButton(
                onClick = { showSheet = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.log_another_day)) }

            // Chart
            SectionTitle(stringResource(R.string.chart_title))
            FrequencyChart(
                points = ui.periodPoints,
                min = tracker.minFrequency,
                max = tracker.maxFrequency,
            )

            // Heat strip
            SectionTitle(stringResource(R.string.heat_title))
            HeatStrip(counts = ui.dailyCounts, today = java.time.LocalDate.now())

            // History
            SectionTitle(stringResource(R.string.history_title))
            if (ui.history.isEmpty()) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LogHistory(groups = ui.history, onDelete = { logToDelete = it })
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            RegisterEventSheet(
                onDismiss = { showSheet = false },
                onConfirm = { date, timeMinutes ->
                    viewModel.logOn(date, timeMinutes)
                    showSheet = false
                },
            )
        }
    }

    logToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { logToDelete = null },
            title = { Text(stringResource(R.string.delete_log_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLog(entry.id)
                    logToDelete = null
                }) { Text(stringResource(R.string.delete_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { logToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (confirmDeleteTracker) {
        AlertDialog(
            onDismissRequest = { confirmDeleteTracker = false },
            title = { Text(stringResource(R.string.delete_tracker_confirm, tracker?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTracker() }) {
                    Text(stringResource(R.string.delete_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTracker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun unitLabel(period: FrequencyPeriod): String = stringResource(
    when (period) {
        FrequencyPeriod.DAY -> R.string.unit_day
        FrequencyPeriod.WEEK -> R.string.unit_week
        FrequencyPeriod.MONTH -> R.string.unit_month
    }
)

@Composable
private fun statusHintColor(status: Status): Color = when (status) {
    Status.ON_TRACK -> StatusGreen
    Status.LOW -> StatusAmber
    Status.HIGH -> StatusRed
    Status.NO_RANGE -> MaterialTheme.colorScheme.onSurfaceVariant
}
```

**Verify on device:** full loop —
1. Tap "Idas al baño" card → detail: header, "Esperado: 3–5 por semana", chip "Bajo", empty chart (0 bars visible on all periods), empty heat strip, "Sin registros aún".
2. Tap **Sucedió hoy** twice → snackbar "Registrado — Deshacer" each time; history shows 2 rows "Hoy" with times; chip flips to "Alto" (2 logs on Monday of a 3–5/week tracker); heat strip last square filled (level 2).
3. Tap **Deshacer** → one row disappears, chip flips back.
4. **Registrar otro día…** → pick last Friday via date picker, add time 08:15 → appears in history under "Viernes…"; chart's previous-week bar = 1.
5. Delete one history row via trash icon → confirm → gone.
6. Top bar ✏️ → editor pre-filled → change name → Guardar → detail shows new name.
7. Top bar 🗑 → confirm → back on dashboard, card gone.
8. Kill & relaunch app → all data persists.
**Commit.**

## Phase 6 — Polish, QA, release

### Task 6.1 — Snackbar/undo consistency + dark theme pass

**Files:** `ui/dashboard/DashboardScreen.kt`, `ui/detail/TrackerDetailScreen.kt`, `ui/detail/TrackerDetailViewModel.kt`

1. In `TrackerDetailViewModel` add `fun clearLastLog() { _lastLog.value = null }` and make the detail snackbar block:
```kotlin
if (result == SnackbarResult.ActionPerformed) viewModel.undoLastLog()
else viewModel.clearLastLog()
```
Apply the same pattern in `DashboardScreen` (its current `undoLastLog()` in the else-branch happens to behave like `clearLastLog` since no insert is pending — still, unify both screens on `clearLastLog()` for clarity).
2. Verify both themes: run the app with system dark mode ON — all surfaces, chips, chart band, heat strip and dialogs must be readable (contrast check). Fix any hard-coded light colors.
3. Rotation & background: rotate the device on dashboard/detail/editor — state must survive (ViewModel + `rememberSaveable` for sheet state is enough; confirm no crash).
4. Back stack: editor opened from dashboard FAB and from detail edit both return to the right screen; detail back → dashboard.

### Task 6.2 — Quality gates

Run, in order, and fix everything they report:
1. `./gradlew :app:testDebugUnitTest` → **22 tests pass** (15 stats + 7 range).
2. `./gradlew :app:lintDebug` → no errors; fix warnings that indicate crashes (e.g. missing contentDescription, locale issues). Warnings without crash risk may remain but must be documented in the final commit message.
3. `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
4. (If device available) `./gradlew :app:connectedDebugAndroidTest` → 3 DAO tests pass. If not, note it and run them at the first opportunity.

### Task 6.3 — Manual QA checklist (device, screenshot each step)

| # | Action | Expected |
|---|---|---|
| 1 | Fresh install | Empty state with CTA |
| 2 | Create "Idas al baño" 🚽, 3–5, por semana | Card on dashboard: "Esta semana: 0 · esperado 3–5", chip "Bajo" |
| 3 | Create 2nd tracker "Café" ☕, sin rango | Card shows "Esta semana: 0", chip "Sin rango" |
| 4 | "+ Hoy" on coffee card ×2 | Snackbar with Deshacer; counts update; chip stays "Sin rango" |
| 5 | Deshacer one | Count back to 1 |
| 6 | Create tracker with min only (3, empty), then max only (empty, 2), verify validation on min>max and non-numeric input | Error texts appear, Save blocked |
| 7 | Edit "Café" → add range 1–2/día | Card subtitle updates to "Hoy: … · esperado 1–2 por día" |
| 8 | Detail of "Idas al baño": log today ×3, register last Thursday with 09:30, register Sunday without time | History grouped by day; times shown correctly; chart bars: last week = 1 (Thursday), this week = 3; heat strip: 2 filled cells |
| 9 | Delete one log (confirm dialog) | Row gone, chart/count updated |
| 10 | Delete "Café" (confirm) | Returns to dashboard; card gone; relaunch app → still gone |
| 11 | Dark mode on | All screens readable |
| 12 | Rotate while on detail with sheet open | No crash; sheet state sane |

### Task 6.4 — README + release APK

**Create:** `README.md` — what the app is, stack, how to build (`./gradlew :app:assembleDebug`), how to install (`./gradlew :app:installDebug`), how to run tests, where data lives (local SQLite, no network).

**Optional (ask the user first):** `./gradlew :app:assembleRelease` to produce the unsigned release APK at `app/build/outputs/apk/release/`.

Final commit: `git add -A && git commit -m "feat: tempus v1.0 complete"`.

---

## 6. Acceptance criteria (Definition of Done — the user's example)

Using the user's own scenario: tracker **"Idas al baño"**, expected **3–5 por semana**.

- [ ] User can create the tracker with a name, emoji, and expected range 3–5/week (range optional).
- [ ] Tapping "Sucedió hoy" (dashboard card or detail) instantly logs an occurrence with the current time, shows "Registrado — Deshacer", and Undo works.
- [ ] User can log any past date with optional time via "Registrar otro día…"; future dates are blocked.
- [ ] Dashboard card shows "Esta semana: N · esperado 3–5" plus a status tag: En track / Bajo / Alto / Sin rango, based on projected weekly pace.
- [ ] Detail screen shows: header + expected summary, status tag + hint, big "Sucedió hoy", frequency chart with bars for the last 8 weeks and the 3–5 band, 35-day heat strip, grouped history with per-entry delete, edit and delete-tracker (cascade) with confirmation.
- [ ] After a few weeks of use, the chart clearly shows actual vs. expected (band) per week.
- [ ] All data survives app restarts; app has zero network permissions; works fully offline.
- [ ] `./gradlew :app:testDebugUnitTest` passes (22 tests); `lintDebug` has no errors; app installs and passes the manual QA checklist.

## 7. Risks, tradeoffs & open questions

| Risk / decision | Mitigation |
|---|---|
| Pinned versions (AGP 8.5.2, Kotlin 2.0.21, KSP 2.0.21-1.0.28, BOM 2024.09.03, Room 2.6.1) may not all resolve on a different SDK setup | If sync fails: bump to latest stable of same major. Compatibility rules: Kotlin 2.0.x ↔ KSP `2.0.21-1.0.28`; AGP 8.5 requires Gradle ≥ 8.7; compileSdk 35 requires AGP ≥ 8.4. Do not mix Kotlin 1.9-style compose compiler plugin — Kotlin 2.x uses `org.jetbrains.kotlin.plugin.compose`. |
| Gradle wrapper bootstrap (Task 0.1) | Download from official Gradle repo; fallback to local `gradle wrapper` or Android Studio scaffold. |
| No `INTERNET` permission means no crash-reporting/analytics — by design (privacy). |
| Status projection is pace-based: an unusually productive Monday (2 logs of a 3–5/week habit) shows "Alto" early in the week. Documented, tested behavior; the chart shows the real history. |
| "Today" is recomputed on data change; the dashboard does not tick over at midnight while idle. Harmless (first interaction refreshes). Acceptable for v1. |
| Timezone: `epochDay` is device-local; changing device timezone may shift past days by one. Accepted for a local personal app. |
| Custom Canvas chart instead of a chart library: full control, zero dependency risk; if richer interactions are ever needed (tooltips, zoom), swap in Vico 2.x/MPAndroidChart behind the same `FrequencyChart` composable. |
| `FlowRow` import: stable in recent Compose; if unresolved, fall back to `androidx.compose.foundation.layout.experimental.FlowRow`. |
| Instrumented DAO tests need a device/emulator — defer to first device connection if none present. |
| **Open question for the user (optional, non-blocking):** export/backup of the SQLite DB? Not in scope for v1; `allowBackup=true` covers Google Auto Backup. |
| **Open question (optional):** notifications/reminders ("¿Sucedió hoy?") — deliberately out of scope; app is a manual tracker. |

---

**Done.** The app is complete when every acceptance checkbox is ticked and the final commit is pushed.