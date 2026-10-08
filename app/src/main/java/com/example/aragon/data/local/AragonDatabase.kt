package com.example.aragon.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        ProjectEntity::class,
        PlanStepEntity::class,
        ArtifactEntity::class,
        ToolExecutionEntity::class,
        TimelineEventEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AragonDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun planStepDao(): PlanStepDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun toolExecutionDao(): ToolExecutionDao
    abstract fun timelineEventDao(): TimelineEventDao

    companion object {
        @Volatile
        private var INSTANCE: AragonDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tool_executions ADD COLUMN artifacts TEXT NOT NULL DEFAULT '[]'")
            }
        }

        fun getInstance(context: Context): AragonDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AragonDatabase::class.java,
                    "aragon_agent.db"
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}
