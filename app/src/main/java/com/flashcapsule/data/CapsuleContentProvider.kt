package com.flashcapsule.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import androidx.room.Room
import com.flashcapsule.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * 只读内容提供者：供外部脚本/自动化拉取胶囊。
 *
 *   content://com.flashcapsule.provider/capsules?since=<epoch>
 *
 * 返回 JSON 数组：[{id, text, title, createdAt, tags, colorTag, status}]。
 * 独立建自己的 Room 实例（ContentProvider 先于 Application.onCreate，不能依赖 app 的 repository）。
 */
class CapsuleContentProvider : ContentProvider() {
    private var db: AppDatabase? = null

    override fun onCreate(): Boolean {
        // 惰性建，等首次 query 时再初始化（此时 app 已启动）
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (uri.pathSegments.firstOrNull() != "capsules") return null
        val since = uri.getQueryParameter("since")?.toLongOrNull() ?: 0L
        val dao = db()?.capsuleDao() ?: return null
        val rows = runBlocking { dao.since(since) }
        val arr = JSONArray()
        rows.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("text", e.text)
                    .put("title", e.title ?: "")
                    .put("createdAt", e.createdAt)
                    .put("tags", if (e.tags.isBlank()) JSONArray() else JSONArray(e.tags.split(",")))
                    .put("colorTag", e.colorTag ?: JSONObject.NULL)
                    .put("status", e.status)
            )
        }
        // 用一个单列 MatrixCursor 返回 JSON 文本
        val c = android.database.MatrixCursor(arrayOf("json"))
        c.addRow(arrayOf(arr.toString()))
        return c
    }

    private fun db(): AppDatabase? {
        db?.let { return it }
        val ctx = context ?: return null
        val built = Room.databaseBuilder(ctx, AppDatabase::class.java, "flashcapsule.db")
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .build()
        db = built
        return built
    }

    override fun getType(uri: Uri): String = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
