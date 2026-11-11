package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(
    tableName = "cached_checklists",
    primaryKeys = ["userId", "schemeId", "documentId"]
)
data class CachedChecklistEntity(
    val userId: String,
    val schemeId: String,
    val documentId: String,
    val status: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Dao
interface CachedChecklistDao {
    @Query("SELECT * FROM cached_checklists WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<CachedChecklistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedChecklistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedChecklistEntity>)

    @Query("DELETE FROM cached_checklists WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}

@Database(entities = [CachedChecklistEntity::class], version = 1, exportSchema = false)
abstract class SchemeSaathiDatabase : RoomDatabase() {
    abstract fun checklistDao(): CachedChecklistDao

    companion object {
        @Volatile
        private var instance: SchemeSaathiDatabase? = null

        fun getInstance(context: Context): SchemeSaathiDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SchemeSaathiDatabase::class.java,
                    "schemesaathi_offline.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

