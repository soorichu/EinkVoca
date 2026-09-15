// StudyModeActivity.kt
package com.soorinote.einkvoca

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class StudyModeActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var words: MutableList<WordItem> = mutableListOf()
    private var currentIndex = 0

    // UI 컴포넌트
    private lateinit var cardContainer: LinearLayout
    private lateinit var tvStudyWord: TextView
    private lateinit var tvStudyMean: TextView
    private lateinit var tvStudyExam: TextView
    private lateinit var cbStudyCheck: CheckBox
    private lateinit var tvIntervalStatus: TextView
    private lateinit var btnMinus: Button
    private lateinit var btnPlus: Button

    // 타이머 관련 (0: 수동, 1~10: 자동 넘김 초, 기본값 3초)
    private var maxSeconds = 10
    private var intervalSeconds = 3
    private val handler = Handler(Looper.getMainLooper())

    // 단어 관련
    private var wordVisible = true;
    private var meanVisible = true;
    private var examVisible = true;

    private val autoFlipRunnable = object : Runnable {
        override fun run() {
            showNextWord()
            if (intervalSeconds > 0) {
                handler.postDelayed(this, intervalSeconds * 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_mode)

        val btnHome = findViewById<Button>(R.id.btnHome)
        btnHome.setOnClickListener {
            finish()
        }

        // E-Ink 화면 깜빡임 방지
        window.setWindowAnimations(0)

        dbHelper = DatabaseHelper(this)

        // 뷰 바인딩
        cardContainer = findViewById(R.id.cardContainer)
        tvStudyWord = findViewById(R.id.tvStudyWord)
        tvStudyMean = findViewById(R.id.tvStudyMean)
        tvStudyExam = findViewById(R.id.tvStudyExam)
        cbStudyCheck = findViewById(R.id.cbStudyCheck)
        tvIntervalStatus = findViewById(R.id.tvIntervalStatus)
        btnMinus = findViewById(R.id.btnMinus)
        btnPlus = findViewById(R.id.btnPlus)

        // 미암기 단어 목록 로드
        words = dbHelper.getUncheckedWords().toMutableList()

        // 1. 단어/뜻 터치 토글
        tvStudyWord.setOnClickListener {
            wordVisible = !wordVisible
        }

        tvStudyMean.setOnClickListener {
            meanVisible = !meanVisible
        }

        tvStudyExam.setOnClickListener {
            examVisible = !examVisible
        }
        // 2. 카드 영역 클릭 시 수동 넘김
        cardContainer.setOnClickListener {
            showNextWord()
            resetTimer()
        }

        // 3. [-] 버튼 클릭 (최소 0초: 수동)
        btnMinus.setOnClickListener {
            if (intervalSeconds > 0) {
                intervalSeconds--
                updateIntervalDisplay()
                resetTimer()
            }
        }

        // 4. [+] 버튼 클릭
        btnPlus.setOnClickListener {
            if (intervalSeconds < maxSeconds) {
                intervalSeconds++
                updateIntervalDisplay()
                resetTimer()
            }
        }

        // 5. 암기 완료 체크박스 이벤트 처리
        cbStudyCheck.setOnCheckedChangeListener { _, isChecked ->
            if (words.isNotEmpty() && currentIndex in words.indices) {
                val currentWord = words[currentIndex]
                currentWord.isChecked = isChecked
                dbHelper.updateCheckStatus(currentWord.word, isChecked)
            }
        }

        // 초기 화면 표시
        updateIntervalDisplay()
        showCurrentWord()

        Toast.makeText(this, "단어나 뜻 영역을 터치하면 숨겨집니다.", Toast.LENGTH_SHORT).show()
    }

    /**
     * 현재 단어를 화면에 바인딩
     */
    private fun showCurrentWord() {
        if (words.isEmpty()) {
            tvStudyWord.text = "모든 단어를 암기했습니다!"
            tvStudyMean.text = ""
            tvStudyWord.visibility = View.VISIBLE
            tvStudyMean.visibility = View.VISIBLE
            cbStudyCheck.isEnabled = false
            handler.removeCallbacks(autoFlipRunnable)
            return
        }

        val item = words[currentIndex]
        tvStudyWord.text = item.word
        tvStudyMean.text = item.mean
        tvStudyExam.text = item.exam

        if (wordVisible) tvStudyWord.setTextColor(Color.BLACK)
        else tvStudyWord.setTextColor(Color.WHITE)

        if (meanVisible) tvStudyMean.setTextColor(Color.BLACK)
        else tvStudyMean.setTextColor(Color.WHITE)

        if (examVisible) tvStudyExam.setTextColor(Color.BLACK)
        else tvStudyExam.setTextColor(Color.WHITE)

        // 체크박스 상태 갱신 (리스너 중복 호출 방지)
        cbStudyCheck.setOnCheckedChangeListener(null)
        cbStudyCheck.isChecked = item.isChecked
        cbStudyCheck.setOnCheckedChangeListener { _, isChecked ->
            item.isChecked = isChecked
            dbHelper.updateCheckStatus(item.word, isChecked)
        }
    }

    /**
     * 다음 단어로 인덱스 이동
     */
    private fun showNextWord() {
        if (words.isEmpty()) return
        currentIndex = (currentIndex + 1) % words.size
        showCurrentWord()
    }

    /**
     * 초 표시 텍스트 갱신
     */
    private fun updateIntervalDisplay() {
        tvIntervalStatus.text = if (intervalSeconds == 0) "수동" else "${intervalSeconds}초"
    }

    /**
     * 타이머 초기화 및 재시작
     */
    private fun resetTimer() {
        handler.removeCallbacks(autoFlipRunnable)
        if (intervalSeconds > 0 && words.isNotEmpty()) {
            handler.postDelayed(autoFlipRunnable, intervalSeconds * 1000L)
        }
    }

    /**
     * 터치 시 글자 보임/숨김 토글
     */
    private fun toggleVisibility(view: View) {
        view.visibility = if (view.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
    }
    /**
     * 터치 시 글자 흰색으로 바꾸기
     */
//    private fun toggleTextColor(textView: TextView) {
//        if (textView.currentTextColor == Color.WHITE) {
//            textView.setTextColor(Color.BLACK)
//        } else {
//            textView.setTextColor(Color.WHITE)
//        }
//    }

    override fun onResume() {
        super.onResume()
        resetTimer()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoFlipRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoFlipRunnable)
        super.onDestroy()
    }
}