package app.cleanupwalk.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [IssueEntity::class, WalkEntity::class], version = 1, exportSchema = false)
abstract class CleanupDatabase : RoomDatabase() {
    abstract fun dao(): CleanupDao

    companion object {
        fun create(context: Context): CleanupDatabase {
            return Room.databaseBuilder(context, CleanupDatabase::class.java, "cleanup_walk.db").build()
        }
    }
}
