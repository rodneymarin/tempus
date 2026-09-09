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
                "INSERT INTO log_entries (id, trackerId, epochDay, timeMinutes) VALUES (1, 1, 20000, 480)"
            )
            db.execSQL(
                "INSERT INTO log_entries (id, trackerId, epochDay, timeMinutes) VALUES (2, 1, 20000, NULL)"
            )
            db.execSQL(
                "INSERT INTO log_entries (id, trackerId, epochDay, timeMinutes) VALUES (3, 1, 20001, NULL)"
            )
        }

        val db = helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2)

        db.query("SELECT id, epochDay, timeMinutes FROM log_entries ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(2, c.getLong(0))            // se conserva MAX(id) del día 20000, no la mayor hora
            assertEquals(20000, c.getLong(1))
            org.junit.Assert.assertTrue(c.isNull(2)) // timeMinutes NULL en el sobreviviente
            c.moveToNext()
            assertEquals(3, c.getLong(0))
            assertEquals(20001, c.getLong(1))
            org.junit.Assert.assertTrue(c.isNull(2))
        }
        db.close()

        // El índice único rechaza un segundo evento en el mismo día.
        helper.createDatabase("migration-test", 2).use { db ->
            db.execSQL(
                "INSERT INTO trackers (id, name, emoji, minFrequency, maxFrequency, period, createdAt) " +
                    "VALUES (1, 'A', 'x', NULL, NULL, 'WEEK', 0)"
            )
            db.execSQL(
                "INSERT INTO log_entries (id, trackerId, epochDay, timeMinutes) VALUES (1, 1, 20000, 480)"
            )
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

    @Test fun migrate3To4_preservesRowsAndAddsNullComment() {
        helper.createDatabase("migration-test-comment", 3).use { db ->
            db.execSQL(
                "INSERT INTO trackers (id, name, emoji, minFrequency, maxFrequency, period, createdAt) " +
                    "VALUES (1, 'A', 'x', NULL, NULL, 'WEEK', 0)"
            )
            db.execSQL(
                "INSERT INTO log_entries (id, trackerId, epochDay, timeMinutes) VALUES (1, 1, 20000, 480)"
            )
            db.execSQL(
                "INSERT INTO log_entries (id, trackerId, epochDay, timeMinutes) VALUES (2, 1, 20001, NULL)"
            )
        }

        val db = helper.runMigrationsAndValidate("migration-test-comment", 4, true, MIGRATION_3_4)

        // Las filas previas sobreviven con comentario NULL…
        db.query("SELECT id, epochDay, timeMinutes, comment FROM log_entries ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(1, c.getLong(0))
            assertEquals(20000, c.getLong(1))
            assertEquals(480, c.getLong(2))
            org.junit.Assert.assertTrue(c.isNull(3))
            c.moveToNext()
            assertEquals(2, c.getLong(0))
            org.junit.Assert.assertTrue(c.isNull(3))
        }

        // …y la nueva columna acepta comentarios en inserciones nuevas.
        db.execSQL(
            "INSERT INTO log_entries (id, trackerId, epochDay, timeMinutes, comment) " +
                "VALUES (3, 1, 20002, NULL, 'nota de prueba')"
        )
        db.query("SELECT comment FROM log_entries WHERE id = 3").use { c ->
            org.junit.Assert.assertTrue(c.moveToFirst())
            assertEquals("nota de prueba", c.getString(0))
        }
        db.close()
    }
}
