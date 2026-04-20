package org.oxycblt.auxio.sync

import android.content.Context
import androidx.room.Room

object SyncDatabaseProvider {
    @Volatile
    private var instance: SyncDatabase? = null

    fun get(context: Context): SyncDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SyncDatabase::class.java,
                "auxify_sync.db"
            ).build().also { instance = it }
        }
    }
}
