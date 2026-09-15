// DatabaseHelper.kt
package com.soorinote.einkvoca

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

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

    fun insertInitialWords(){
        val db = writableDatabase
        val initialWords = listOf(
            arrayOf(
                "apple",
                "사과",
                "ˈæpl",
                "I like apple"
            ),
            arrayOf(
                "banana",
                "바나나",
                "",
                "I love banana"
            ),
            arrayOf(
                "sunday",
                "일요일",
                "",
                "Happy sunday!"
            ),
            arrayOf(
                "birthday",
                "생일",
                "|bɜːrθdeɪ",
                "Today is my birthday"
            ),
            arrayOf(
                "dog",
                "개",
                "",
                ""
            ),
            arrayOf(
                "cat",
                "고양이",
                "",
                ""
            )

        )
        db.beginTransaction()
        try {
            val values = ContentValues()
            for (item in initialWords) {
                values.clear()
                values.put("word", item[0])
                values.put("mean", item[1])
                values.put("pron", item[2])
                values.put("exam", item[3].trim())
                values.put("check_status", 0)
                db.insertWithOnConflict("voca", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                Log.d("DB_CHECK", "단어 삽입: ${item[0]}")
            }
            db.setTransactionSuccessful()
            Log.d("DB_CHECK", "초기 단어 6개 삽입 및 트랜잭션 커밋 완료")
        } catch (e: Exception) {
            Log.e("DB_CHECK", "초기 데이터 삽입 중 오류 발생 (롤백됨): ${e.message}", e)
        } finally {
            db.endTransaction()
        }
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

    // check_status가 1인 단어들을 일괄 삭제하고 삭제된 개수를 반환
    fun deleteCheckedWords(): Int {
        val db = writableDatabase
        return db.delete("voca", "check_status = ?", arrayOf("1"))
    }

    fun getUncheckedWords(): List<WordItem> {
        return getAllWords().filter { !it.isChecked }
    }
}