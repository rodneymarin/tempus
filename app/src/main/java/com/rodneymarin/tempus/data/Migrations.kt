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
