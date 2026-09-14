// StudyModeActivity.kt
package com.soorinote.einkvoca

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class StudyModeActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var uncheckedWords: List<WordItem> = emptyList()
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvStudyWord: TextView
    private lateinit var tvStudyMean: TextView

    private val runnable = object : Runnable {
        override fun run() {
            if (uncheckedWords.isNotEmpty() && currentIndex < uncheckedWords.size) {
                val item = uncheckedWords[currentIndex]
                tvStudyWord.text = item.word
                tvStudyMean.text = item.mean

                // 단어가 새로 바뀔 때 가려진 텍스트를 다시 보이게 초기화하고 싶다면
                // 아래 두 줄의 주석을 해제하세요.
                // tvStudyWord.visibility = View.VISIBLE
                // tvStudyMean.visibility = View.VISIBLE

                currentIndex++
                handler.postDelayed(this, 1000) // 1초 간격 갱신
            } else if (uncheckedWords.isNotEmpty()) {
                currentIndex = 0 // 순환 반복
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_mode)

        tvStudyWord = findViewById(R.id.tvStudyWord)
        tvStudyMean = findViewById(R.id.tvStudyMean)

        dbHelper = DatabaseHelper(this)
        uncheckedWords = dbHelper.getUncheckedWords()

        // --- 터치(클릭) 토글 기능 추가 ---
        tvStudyWord.setOnClickListener {
            toggleVisibility(tvStudyWord)
        }

        tvStudyMean.setOnClickListener {
            toggleVisibility(tvStudyMean)
        }
        // ---------------------------------

        if (uncheckedWords.isEmpty()) {
            tvStudyWord.text = "모든 단어를 암기했습니다!"
            tvStudyMean.text = ""
        } else {
            handler.post(runnable)
        }
    }

    /**
     * View의 가시성(Visibility)을 토글하는 함수
     * GONE 대신 INVISIBLE을 사용하여 텍스트가 사라져도 레이아웃 영역 및 터치 영역을 유지합니다.
     */
    private fun toggleVisibility(view: View) {
        if (view.visibility == View.VISIBLE) {
            view.visibility = View.INVISIBLE
        } else {
            view.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }
}