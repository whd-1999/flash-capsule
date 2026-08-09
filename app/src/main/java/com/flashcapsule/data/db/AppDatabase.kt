package com.flashcapsule.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CapsuleEntity::class, AttachmentEntity::class], version = 5, exportSchema = false)
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

        /** v3→v4：attachments 表（胶囊附件）。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS attachments (" +
                        "id TEXT PRIMARY KEY NOT NULL, " +
                        "capsuleId TEXT NOT NULL, " +
                        "uri TEXT NOT NULL, " +
                        "mime TEXT, " +
                        "sizeBytes INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
            }
        }

        /** v4→v5：capsules 加 kind 列（AI 智能判断类型）。 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE capsules ADD COLUMN kind TEXT")
            }
        }
    }
}
