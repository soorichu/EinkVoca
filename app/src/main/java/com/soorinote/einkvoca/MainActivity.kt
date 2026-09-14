// MainActivity.kt
package com.soorinote.einkvoca

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.InputStreamReader
import com.soorinote.einkvoca.DatabaseHelper;
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var rgStorage: RadioGroup
    private lateinit var rbInternal: RadioButton
    private lateinit var rbExternal: RadioButton
    private lateinit var tvGuidePath: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 화면 전환 애니메이션 끄기 (E-Ink 깜빡임 방지)
        window.setWindowAnimations(0)
        // DB 생성
        dbHelper = DatabaseHelper(this)
        // 저장소 선택
        rgStorage = findViewById(R.id.rgStorage)
        rbInternal = findViewById(R.id.rbInternal)
        rbExternal = findViewById(R.id.rbExternal)
        tvGuidePath = findViewById(R.id.tvGuidePath)

        val btnSync = findViewById<Button>(R.id.btnSync)
        val btnOpenVoca = findViewById<Button>(R.id.btnOpenVoca)
        val btnStudyMode = findViewById<Button>(R.id.btnStudyMode)



        // 권한 확인 및 요청
        checkPermissions()

        // 초기 경로 설정
        updateFileDirectory()
        // 샘플 CSV 파일 생성
        createSampleCsvIfNeeded()

        btnSync.setOnClickListener {
            syncCsvToDb()
        }

        btnOpenVoca.setOnClickListener {
            startActivity(Intent(this, VocaListActivity::class.java))
        }

        btnStudyMode.setOnClickListener {
            startActivity(Intent(this, StudyModeActivity::class.java))
        }

        rbInxternal.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 내부 저장소로 변경
                currentTargetDir = getExternalFilesDir(null) // 또는 File(Environment.getExternalStorageDirectory(), "einknote")
            } else {
                // 내부 저장소로 변경 (예: getFilesDir())
                currentTargetDir = filesDir
            }
        }

        rbExternal.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 1. 공용 외부 저장소 루트의 특정 폴더 설정
                val targetDir = File(Environment.getExternalStorageDirectory(), "einknote")

                // 폴더가 없으면 생성
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                // 2. 절대 경로 문자열 추출 (/storage/emulated/0/einknote)
                val pathText: String = targetDir.absolutePath

                tvGuidePath.text = "* 외부 저장소 경로 : ${pathText}/einknote/ \n 위 경로에 'voca.csv'를 UTF-8로 저장하여 넣어주세요.\n헤더: word(단어), mean(의미), pron(발음), exam(예문)"
            }
        }
    }

    /**
     * 현재 선택된 저장소 Base Directory 반환
     */
    private fun getPathDirectory(): File {
        // 저장할 폴더명
        val subFolderName = "einknote"
        // RadioButton 체크 여부에 따라 경로 분기
        val targetDir: File = if (rbExternal.isChecked) {
            // [외부 저장소] 공용 외부 저장소 루트 (/storage/emulated/0/einknote)
            File(Environment.getExternalStorageDirectory(), subFolderName)
        } else {
            // [내부 저장소] 앱 전용 내부 저장소 루트 (/data/user/0/패키지명/files/einknote)
            File(filesDir, subFolderName)
        }

        // 폴더가 없으면 생성
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        return targetDir;
    }

    private fun updateFileDirectory() {
        // 경로 텍스트 추출
        val dir: String = getPathDirectory().absolutePath
        tvGuidePath.setText("* 경로 : ${dir}\n\n위 경로에 'voca.csv'를 UTF-8로 저장하여 넣어주세요.\n헤더: word(단어), mean(의미), pron(발음), exam(예문)")
    }



    private fun checkPermissions() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
    }

    /**
     * <기본저장소>/einknote/ 폴더 생성 및 voca.csv 파일 생성
     */
    private fun createSampleCsvIfNeeded() {
        try {
            val dir = File(getPathDirectory().absolutePath)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val sampleFile = File(dir, "voca.csv")
            if (!sampleFile.exists()) {
                val sampleContent = """
                    word,mean,pron,exam
                    apple,사과,æpl,I eat an apple.
                    banana,바나나,bəˈnænə,
                    book,책,,This book is interesting.
                """.trimIndent()

                FileOutputStream(sampleFile).use { fos ->
                    fos.write(sampleContent.toByteArray(Charsets.UTF_8))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * <저장소>/einknote/voca.csv 데이터를 읽어 DB에 동기화
     */
    private fun syncCsvToDb() {
        val csvFile = File(Environment.getExternalStorageDirectory(), "einknote/voca.csv")
        if (!csvFile.exists()) {
            Toast.makeText(this, "voca.csv 파일이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val reader = InputStreamReader(csvFile.inputStream(), "UTF-8").buffered()
            var line: String? = reader.readLine() // Header(word,mean,pron,exam) 스킵
            var count = 0

            while (reader.readLine().also { line = it } != null) {
                val tokens = line!!.split(",")
                if (tokens.size >= 4) {
                    val word = tokens[0].trim()
                    val mean = tokens[1].trim()
                    val pron = tokens[2].trim()
                    val exam = tokens[3].trim()
                    // 4개의 헤더 기준 동기화 시 기본 check 상태는 false로 설정
                    val isChecked = if (tokens.size >= 5) tokens[4].trim().lowercase() == "true" else false

                    dbHelper.insertOrUpdateWord(word, mean, pron, exam, isChecked)
                    count++
                }
            }
            reader.close()
            Toast.makeText(this, "$count 개 단어 동기화 완료!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "동기화 오류: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}