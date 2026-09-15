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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.KeyStore

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
            // syncCsvToDb()
            showCsvChooserDialog()
        }

        btnOpenVoca.setOnClickListener {
            startActivity(Intent(this, VocaListActivity::class.java))
        }

        btnStudyMode.setOnClickListener {
            startActivity(Intent(this, StudyModeActivity::class.java))
        }

        rbInternal.setOnCheckedChangeListener { _, isChecked -> updateFileDirectory() }

        rbExternal.setOnCheckedChangeListener { _, isChecked -> updateFileDirectory() }

    }

    /**
     * 현재 선택된 저장소 Base Directory 반환
     */
    private fun getPathDirectory(): File {
        val subFolderName = "einknote"

        // Context의 getExternalFilesDirs 호출 (Activity 내부 기준)
        val externalDirs: Array<File?> = getExternalFilesDirs(null)

        // externalDirs[1] 존재 및 null 여부 확인
        val hasSdCard = externalDirs.size > 1 && externalDirs[1] != null

        // if-else 표현식으로 val(불변) 변수에 바로 할당
        val baseDir: File = if (rbExternal.isChecked) {
            if (hasSdCard) {
                externalDirs[1]!!
            } else {
                // 외부 저장소가 없는 경우 라디오 버튼 롤백 및 토스트 안내
                rbInternal.isChecked = true
                rbExternal.isChecked = false
                Toast.makeText(this, "외부 저장소를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                externalDirs[0] ?: filesDir // 0번마저 null일 경우 앱 내부 filesDir로 폴백
            }
        } else {
            externalDirs[0] ?: filesDir
        }

        val targetDir = File(baseDir, subFolderName)

        // 폴더가 없으면 생성
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        return targetDir
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
     * CSV 파일 목록을 검색하고 선택 다이얼로그를 띄우는 함수
     */
    private fun showCsvChooserDialog() {
        val directory = getPathDirectory()

        // 폴더 존재 여부 및 디렉터리 확인
        if (!directory.exists() || !directory.isDirectory) {
            Toast.makeText(this, "einkvoca 폴더를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // .csv 확장자 파일 필터링
        val csvFiles = directory.listFiles { file ->
            file.isFile && file.extension.equals("csv", ignoreCase = true)
        }

        if (csvFiles.isNullOrEmpty()) {
            Toast.makeText(this, "einkvoca 폴더에 CSV 파일이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 파일 이름 목록 추출
        val fileNames = csvFiles.map { it.name }.toTypedArray()

        // 파일 선택 다이얼로그 생성
        AlertDialog.Builder(this)
            .setTitle("동기화할 CSV 파일 선택")
            .setItems(fileNames) { _, which ->
                val selectedFile = csvFiles[which]
                importCsvData(selectedFile)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * <저장소>/einknote/voca.csv 데이터를 읽어 DB에 동기화
     */
    private fun importCsvData(csvFile:File) {
        // val csvFile = File(Environment.getExternalStorageDirectory(), "einknote/voca.csv")
        if (!csvFile.exists()) {
            Toast.makeText(this, "voca.csv 파일이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        try {

            val reader = InputStreamReader(csvFile.inputStream(), "UTF-8").buffered()
            val headerLine = reader.readLine() ?: return // 파일이 비어있으면 종료

            // 헤더 파싱: 공백 제거 및 소문자 변환 후 각 컬럼명의 인덱스 매핑
            val headerTokens = headerLine.split(",").map { it.trim().lowercase() }
            val wordIdx = headerTokens.indexOf("word")
            val meanIdx = headerTokens.indexOf("mean")
            val pronIdx = headerTokens.indexOf("pron")
            val examIdx = headerTokens.indexOf("exam")
            val checkIdx = headerTokens.indexOfFirst { it == "checked" || it == "ischecked" }

            var line: String? = null
            var count = 0

            // 필수 컬럼(word, mean)이 헤더에 없으면 순서대로 읽기
            if (wordIdx == -1 || meanIdx == -1) {
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line!!.split(",")
                    if (tokens.size >= 4) {
                        val word = tokens[0].trim()
                        val mean = tokens[1].trim()
                        val pron = tokens[2].trim()
                        val exam = tokens[3].trim()
                        // 4개의 헤더 기준 동기화 시 기본 check 상태는 false로 설정
                        val isChecked = if (tokens.size >= 5) tokens[4].trim().lowercase() == "true" else false

                        // 최소한의 필수 데이터가 있을 때만 DB 저장
                        if (word.isNotEmpty() && mean.isNotEmpty()) {
                            dbHelper.insertOrUpdateWord(word, mean, pron, exam, isChecked)
                            count++
                        }
                    }

                }

            } else{

                while (reader.readLine().also { line = it } != null) {
                    val tokens = line!!.split(",")

                    // 안전하게 인덱스 값을 가져오는 헬퍼 함수
                    fun getToken(index: Int): String =
                        if (index in tokens.indices) tokens[index].trim() else ""

                    val word = getToken(wordIdx)
                    val mean = getToken(meanIdx)
                    val pron = getToken(pronIdx)
                    val exam = getToken(examIdx)

                    // checked 열이 존재하고 값이 'true'인지 판별 (없으면 기본값 false)
                    val isChecked = if (checkIdx != -1 && checkIdx in tokens.indices) {
                        tokens[checkIdx].trim().equals("true", ignoreCase = true)
                    } else {
                        false
                    }

                    // 최소한의 필수 데이터가 있을 때만 DB 저장
                    if (word.isNotEmpty() && mean.isNotEmpty()) {
                        dbHelper.insertOrUpdateWord(word, mean, pron, exam, isChecked)
                        count++
                    }
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