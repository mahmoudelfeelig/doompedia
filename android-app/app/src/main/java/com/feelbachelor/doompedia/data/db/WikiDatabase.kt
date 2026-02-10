package com.feelbachelor.doompedia.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ArticleEntity::class,
        AliasEntity::class,
        BookmarkEntity::class,
        SaveFolderEntity::class,
        ArticleFolderRefEntity::class,
        HistoryEntity::class,
        TopicAffinityEntity::class,
    ],
    version = 2,
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
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS save_folders (
                        folderId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        isDefault INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_save_folders_name
                    ON save_folders(name)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS article_folder_refs (
                        folderId INTEGER NOT NULL,
                        pageId INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(folderId, pageId),
                        FOREIGN KEY(folderId) REFERENCES save_folders(folderId) ON DELETE CASCADE,
                        FOREIGN KEY(pageId) REFERENCES articles(pageId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_article_folder_refs_pageId
                    ON article_folder_refs(pageId)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_article_folder_refs_folderId
                    ON article_folder_refs(folderId)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO save_folders(folderId, name, isDefault, createdAt)
                    VALUES (1, 'Bookmarks', 1, 0)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO article_folder_refs(folderId, pageId, createdAt)
                    SELECT 1, pageId, createdAt FROM bookmarks
                    """.trimIndent()
                )
            }
        }
    }
}
