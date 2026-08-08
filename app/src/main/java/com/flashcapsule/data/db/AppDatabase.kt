package com.flashcapsule.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CapsuleEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun capsuleDao(): CapsuleDao
}
