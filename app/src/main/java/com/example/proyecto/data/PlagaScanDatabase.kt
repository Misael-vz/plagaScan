package com.example.proyecto.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AnalysisResult::class], version = 3, exportSchema = false)
abstract class PlagaScanDatabase : RoomDatabase() {
    abstract fun analysisDao(): AnalysisDao

    companion object {
        @Volatile
        private var INSTANCE: PlagaScanDatabase? = null

        // RF08: agrega latitude, longitude y cropType sin borrar los
        // registros existentes (migracion no destructiva). cropType usa
        // DEFAULT 'Frijol' para que los 22+ registros previos a esta
        // migracion queden consistentes con el alcance actual del proyecto.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE analysis_results ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE analysis_results ADD COLUMN longitude REAL")
                db.execSQL(
                    "ALTER TABLE analysis_results ADD COLUMN cropType TEXT NOT NULL DEFAULT 'Frijol'"
                )
            }
        }

        // RF08 / prueba 14: agrega la columna "etiqueta" (texto, opcional) sin
        // tocar los registros existentes (migracion no destructiva).
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE analysis_results ADD COLUMN etiqueta TEXT")
            }
        }

        fun getInstance(context: Context): PlagaScanDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlagaScanDatabase::class.java,
                    "plagascan.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
