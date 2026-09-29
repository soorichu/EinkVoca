// StudyModeActivity.kt
package cloud.einknote.einkvoca

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class StudyModeActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var words: MutableList<WordItem> = mutableListOf()
    private var words_filtered: MutableList<WordItem> = mutableListOf()
    private var currentIndex = 0

    // UI 컴포넌트
    private lateinit var cardContainer: LinearLayout
    private lateinit var tvStudyWord: TextView
    private lateinit var tvStudyPron: TextView
    private lateinit var tvStudyMean: TextView
    private lateinit var tvStudyExam: TextView
    private lateinit var cbStudyCheck: CheckBox
    private lateinit var tvIntervalStatus: TextView
    private lateinit var btnMinus: Button
    private lateinit var btnPlus: Button
    private lateinit var btnLangage: Button


    // 타이머 관련 (0: 수동, 1~10: 자동 넘김)
    private var maxSeconds = 10
    private var intervalSeconds = 0
    private val handler = Handler(Looper.getMainLooper())

    // 단어 관련
    private var wordVisible = true;
    private var pronVisible = true;
    private var meanVisible = true;
    private var examVisible = true;

    private var selectedLang = "ALL";


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
        tvStudyPron = findViewById(R.id.tvStudyPron)
        tvStudyMean = findViewById(R.id.tvStudyMean)
        tvStudyExam = findViewById(R.id.tvStudyExam)
        cbStudyCheck = findViewById(R.id.cbStudyCheck)
        tvIntervalStatus = findViewById(R.id.tvIntervalStatus)
        btnMinus = findViewById(R.id.btnMinus)
        btnPlus = findViewById(R.id.btnPlus)
        btnLangage = findViewById<Button>(R.id.btnLanguage)

        // 미암기 단어 목록 로드
        words = dbHelper.getUncheckedWords().toMutableList()
        words.shuffle()  // 셔플
        words_filtered = words.filter { filterWord(it.word, selectedLang) }.toMutableList()

        // 1. 단어/뜻 터치 토글
        tvStudyWord.setOnClickListener {
            wordVisible = !wordVisible
        }

        tvStudyPron.setOnClickListener {
            pronVisible = !pronVisible
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
//        // 2. 카드 영역 클릭 시 수동 넘김 (좌/우 구분)
//        cardContainer.setOnTouchListener { view, event ->
//            // 손가락이 화면에서 떨어지는 시점(클릭 완료)에 동작
//            if (event.action == MotionEvent.ACTION_UP) {
//                val clickX = event.x
//                val viewWidth = view.width
//
//                if (clickX < viewWidth / 2) {
//                    // 왼쪽 절반 클릭 시
//                    showPreviousWord()
//                } else {
//                    // 오른쪽 절반 클릭 시
//                    showNextWord()
//                }
//                resetTimer()
//                view.performClick() // 접근성(Accessibility) 경고를 해결하기 위해 호출
//            }
//            true // 이벤트를 소비(소모)함
//        }

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

        // 항목이 선택되었을 때의 리스너
        btnLangage.setOnClickListener {
            showLanguageDialog()
        }

        // 초기 화면 표시
        updateIntervalDisplay()
        showCurrentWord()
        Toast.makeText(this, "단어나 뜻 영역을 터치하면 숨겨집니다.", Toast.LENGTH_SHORT).show()
    }

    // 단어 필터
    fun filterWord(word: String, selectedLang: String): Boolean {
        // ALL이 선택되면 무조건 true 반환
        if (selectedLang == "ALL") return true

        // 빈 문자열일 경우 false 반환
        val firstChar = word.firstOrNull() ?: return false

        return when (selectedLang) {
            // 숫자/기호 제외, 라틴 계열 알파벳(외래어 확장 포함)만 매칭
            "en" -> firstChar.toString().matches(Regex("[\\p{IsLatin}]"))

            "ko" -> firstChar in '\uAC00'..'\uD7AF' || firstChar in '\u3130'..'\u318F'

            "cn/jp" -> firstChar in '\u3040'..'\u309F' ||
                    firstChar in '\u30A0'..'\u30FF' ||
                    firstChar in '\u4E00'..'\u9FFF'
            else -> false
        }
    }
    // 언어 선택 다이얼로그 띄우기 함수
    private fun showLanguageDialog() {
        // 1. 위에서 만든 XML 레이아웃을 메모리에 올림(Inflate)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_language, null)

        // 2. 다이얼로그 생성
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // 3. 각 항목 클릭 이벤트 설정
        dialogView.findViewById<TextView>(R.id.tv_lang_all).setOnClickListener {
            selectedLang = "ALL"
            btnLangage.setText("ALL")
            words_filtered = words.filter { filterWord(it.word, selectedLang) }.toMutableList()
            currentIndex = 0
            dialog.dismiss() // 창 닫기
        }
        dialogView.findViewById<TextView>(R.id.tv_lang_ko).setOnClickListener {
            selectedLang = "ko"
            btnLangage.setText("국어(ko)")
            words_filtered = words.filter { filterWord(it.word, selectedLang) }.toMutableList()
            currentIndex = 0
            dialog.dismiss()
        }
        dialogView.findViewById<TextView>(R.id.tv_lang_en).setOnClickListener {
            selectedLang = "en"
            btnLangage.setText("영어(en/latin)")
            words_filtered = words.filter { filterWord(it.word, selectedLang) }.toMutableList()
            currentIndex = 0
            dialog.dismiss()
        }
        dialogView.findViewById<TextView>(R.id.tv_lang_cn_jp).setOnClickListener {
            selectedLang = "cn/jp"
            btnLangage.setText("중국어/일본어/한자")
            words_filtered = words.filter { filterWord(it.word, selectedLang) }.toMutableList()
            currentIndex = 0
            dialog.dismiss()
        }
        // 4. 다이얼로그 화면에 띄우기
        dialog.show()
    }
    /**
     * 현재 단어를 화면에 바인딩
     */
    private fun showCurrentWord() {
        if (words_filtered.isEmpty()) {
            tvStudyWord.text = "모든 단어를 암기했습니다!"
            tvStudyPron.text = ""
            tvStudyMean.text = ""
            tvStudyWord.visibility = View.VISIBLE
            tvStudyMean.visibility = View.VISIBLE
            cbStudyCheck.isEnabled = false
            handler.removeCallbacks(autoFlipRunnable)
            return
        }

        val item = words_filtered[currentIndex]
   //     if(filterWord(item.word, selectedLang)) {
            tvStudyWord.text = item.word
            tvStudyPron.text = "[" + item.pron + "]"
            tvStudyMean.text = item.mean
            tvStudyExam.text = item.exam

            if (wordVisible) tvStudyWord.setTextColor(Color.BLACK)
            else tvStudyWord.setTextColor(Color.WHITE)

            if (pronVisible) tvStudyPron.setTextColor(Color.BLACK)
            else tvStudyPron.setTextColor(Color.WHITE)

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
    //    }
    }

    /**
     * 다음 단어로 인덱스 이동
     */
    private fun showNextWord() {
        if (words_filtered.isEmpty()) return
        currentIndex = (currentIndex + 1) % words_filtered.size
        showCurrentWord()
    }

    private fun showPreviousWord() {
        if (words_filtered.isEmpty()) return
        currentIndex = (currentIndex - 1 + words_filtered.size) % words_filtered.size
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