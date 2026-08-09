package com.flashcapsule.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CapsuleEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun capsuleDao(): CapsuleDao

    companion object {
        /** v2→v3：title / deletedAt / doneAt 三列，全部纯增量 ALTER，不触碰既有数据。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE capsules ADD COLUMN title TEXT")
                db.execSQL("ALTER TABLE capsules ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE capsules ADD COLUMN doneAt INTEGER")
            }
        }
    }
}
