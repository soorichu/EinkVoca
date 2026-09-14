// DatabaseHelper.kt
package com.soorinote.einkvoca

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class WordItem(
    val word: String,
    val mean: String,
    val pron: String,
    val exam: String,
    var isChecked: Boolean
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "EinkVoca.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE voca (
                word TEXT PRIMARY KEY,
                mean TEXT,
                pron TEXT,
                exam TEXT,
                check_status INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS voca")
        onCreate(db)
    }

    fun insertOrUpdateWord(word: String, mean: String, pron: String, exam: String, isChecked: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("word", word)
            put("mean", mean)
            put("pron", pron)
            put("exam", exam)
            put("check_status", if (isChecked) 1 else 0)
        }
        db.insertWithOnConflict("voca", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateCheckStatus(word: String, isChecked: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("check_status", if (isChecked) 1 else 0)
        }
        db.update("voca", values, "word = ?", arrayOf(word))
    }

    fun getAllWords(): List<WordItem> {
        val list = mutableListOf<WordItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM voca", null)
        if (cursor.moveToFirst()) {
            do {
                val word = cursor.getString(cursor.getColumnIndexOrThrow("word"))
                val mean = cursor.getString(cursor.getColumnIndexOrThrow("mean"))
                val pron = cursor.getString(cursor.getColumnIndexOrThrow("pron"))
                val exam = cursor.getString(cursor.getColumnIndexOrThrow("exam"))
                val isChecked = cursor.getInt(cursor.getColumnIndexOrThrow("check_status")) == 1
                list.add(WordItem(word, mean, pron, exam, isChecked))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getUncheckedWords(): List<WordItem> {
        return getAllWords().filter { !it.isChecked }
    }
}