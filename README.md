# Tempus ⏳

Tempus es un rastreador personal de hábitos y eventos, 100 % offline. Registra cuántas veces ocurre algo (por ejemplo, "idas al baño", "café", "ejercicio") y compara tu ritmo real contra una meta que tú defines.

## Stack

- **Kotlin 2.0.21** con **Compose UI** (Material3)
- **Room 2.6.1** + **KSP** para persistencia local (SQLite)
- **Navigation Compose** para navegación entre pantallas
- **JUnit 4** para tests unitarios
- Gradle 8.7, AGP 8.5.2, compileSdk 35

## Pantallas principales

- **Dashboard** — lista de tarjetas con conteo del período actual, chip de estado (En track / Bajo / Alto / Sin rango) y botón rápido "+ Hoy" con *Snackbar + Deshacer*.
- **Detalle** — cabecera con emoji, resumen de frecuencia esperada, estado, botón "Sucedió hoy", gráfico de barras (últimas 8 semanas), franja de calor de 35 días, historial agrupado por día con borrado individual.
- **Editor** — crear o editar tracker: nombre, emoji picker, rango de frecuencia (opcional) y período (día / semana / mes).

## Construir e instalar

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

El APK debug se genera en:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Ejecutar tests

```bash
# Tests unitarios (22 tests)
./gradlew :app:testDebugUnitTest

# Lint (actualmente sin errores; solo advertencias no críticas)
./gradlew :app:lintDebug

# Tests instrumentados (requieren dispositivo/emulador)
./gradlew :app:connectedDebugAndroidTest
```

## Datos

Todos los datos viven en una base SQLite local gestionada por Room. No se solicita permiso de red; la app funciona completamente offline.

## Idioma

La interfaz y todos los textos están en español.
