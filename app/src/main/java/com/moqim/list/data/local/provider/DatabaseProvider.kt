package com.moqim.list.data.local.provider

import android.content.Context
import androidx.room.Room
import com.moqim.list.data.local.database.AppDatabase

object DatabaseProvider {

    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "moqim_list.db",
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
