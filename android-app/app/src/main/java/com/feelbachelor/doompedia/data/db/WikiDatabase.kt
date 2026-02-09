package com.feelbachelor.doompedia.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ArticleEntity::class,
        AliasEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        TopicAffinityEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WikiDatabase : RoomDatabase() {
    abstract fun wikiDao(): WikiDao

    companion object {
        @Volatile
        private var instance: WikiDatabase? = null

        fun getInstance(context: Context): WikiDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WikiDatabase::class.java,
                    "doompedia.db",
                ).build().also { instance = it }
            }
        }
    }
}
