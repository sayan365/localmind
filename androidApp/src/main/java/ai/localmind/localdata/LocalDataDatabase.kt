package ai.localmind.localdata

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update

@Entity(tableName = "memories")
data class MemoryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val normalizedContent: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "reminders")
data class ReminderRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerAt: Long,
    val createdAt: Long,
    val status: String = STATUS_SCHEDULED
) {
    companion object {
        const val STATUS_SCHEDULED = "scheduled"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_EXPIRED = "expired"
    }
}

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(record: MemoryRecord): Long

    @Update
    fun update(record: MemoryRecord)

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun listAll(): List<MemoryRecord>

    @Query("SELECT * FROM memories WHERE normalizedContent = :normalized LIMIT 1")
    fun findExact(normalized: String): MemoryRecord?

    @Query("DELETE FROM memories WHERE id = :id")
    fun delete(id: Long): Int

    @Query("DELETE FROM memories")
    fun clear()
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(record: ReminderRecord): Long

    @Query("SELECT * FROM reminders WHERE status = 'scheduled' ORDER BY triggerAt ASC")
    fun listScheduled(): List<ReminderRecord>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    fun find(id: Long): ReminderRecord?

    @Query("UPDATE reminders SET status = :status WHERE id = :id")
    fun updateStatus(id: Long, status: String): Int

    @Query("UPDATE reminders SET status = :status WHERE status = 'scheduled' AND triggerAt <= :now")
    fun expirePast(now: Long, status: String): Int

    @Query("DELETE FROM reminders WHERE id = :id")
    fun delete(id: Long): Int

    @Query("DELETE FROM reminders")
    fun clear()
}

@Database(entities = [MemoryRecord::class, ReminderRecord::class], version = 1, exportSchema = true)
abstract class LocalDataDatabase : RoomDatabase() {
    abstract fun memories(): MemoryDao
    abstract fun reminders(): ReminderDao

    companion object {
        @Volatile private var instance: LocalDataDatabase? = null

        fun get(context: Context): LocalDataDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LocalDataDatabase::class.java,
                "localmind.db"
            ).build().also { instance = it }
        }
    }
}
