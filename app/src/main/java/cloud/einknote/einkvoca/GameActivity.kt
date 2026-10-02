package cloud.einknote.einkvoca

import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class GameActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tvTimer: TextView
    private lateinit var btnPlusTime: Button
    private lateinit var btnRetry: Button
    private lateinit var ivCat: ImageView
    private lateinit var gridLayout: GridLayout
    private lateinit var btnHome: Button

    private var firstSelectedButton: Button? = null
    private var secondSelectedButton: Button? = null
    private var isCheckingMatch = false

    private var matchedPairs = 0
    private var totalPairs = 0

    // 타이머 및 시간 연장 변수
    private var countDownTimer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 60000L // 기본 1분 (60초)
    private var plusTimeCount = 0
    private val MAX_PLUS_TIME = 4

    data class TileData(val text: String, val matchingWord: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        dbHelper = DatabaseHelper(this)
        tvTimer = findViewById(R.id.tvTimer)
        btnPlusTime = findViewById(R.id.btnPlusTime)
        btnRetry = findViewById(R.id.btnRetry)
        ivCat = findViewById(R.id.ivCat)
        gridLayout = findViewById(R.id.gridLayout)
        btnHome = findViewById(R.id.btnHome)

        // 시간 연장 버튼 클릭 이벤트
        btnPlusTime.setOnClickListener {
            if (plusTimeCount < MAX_PLUS_TIME) {
                plusTimeCount++
                timeLeftInMillis += 60000L // 60초 추가

                // 기존 타이머 취소 후 남은 시간에 60초 더해서 재시작
                startTimer()

                val remaining = MAX_PLUS_TIME - plusTimeCount
                btnPlusTime.text = "+1분 ($remaining)"

                if (remaining == 0) {
                    btnPlusTime.isEnabled = false // 4번 다 쓰면 비활성화
                }
            }
        }

        btnHome.setOnClickListener {
            finish()
        }

        // 다시 하기 버튼 클릭 이벤트
        btnRetry.setOnClickListener {
            recreate() // Activity를 재시작하여 완전히 초기화된 새 게임 시작
        }

        setupGame()
    }

    private fun setupGame() {
        val uncheckedWords = dbHelper.getUncheckedWords()
        val wordsToPlay = uncheckedWords.shuffled().take(20)
        totalPairs = wordsToPlay.size

        if (totalPairs == 0) {
            Toast.makeText(this, "학습할 단어가 없습니다!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val tileList = mutableListOf<TileData>()
        for (item in wordsToPlay) {
            tileList.add(TileData(text = item.word, matchingWord = item.word))
            tileList.add(TileData(text = item.mean, matchingWord = item.word))
        }

        tileList.shuffle()

        gridLayout.removeAllViews()
        for (tile in tileList) {
            val button = Button(this).apply {
                text = tile.text
                textSize = 12f
                setTextColor(Color.BLACK)
                setBackgroundResource(R.drawable.img_brick) // 벽돌 벡터 이미지 사용
                gravity = Gravity.CENTER
                isAllCaps = false
                tag = tile.matchingWord

                val params = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    height = 0
                    setMargins(0, 0, 0, 0)
                }
                layoutParams = params

                setOnClickListener { onTileClicked(this) }
            }
            gridLayout.addView(button)
        }

        startTimer()
    }

    private fun onTileClicked(clickedButton: Button) {
        if (isCheckingMatch || clickedButton.visibility == View.INVISIBLE || clickedButton == firstSelectedButton) {
            return
        }

        clickedButton.alpha = 0.6f

        if (firstSelectedButton == null) {
            firstSelectedButton = clickedButton
        } else {
            secondSelectedButton = clickedButton
            isCheckingMatch = true

            val firstMatchingWord = firstSelectedButton?.tag as String
            val secondMatchingWord = secondSelectedButton?.tag as String

            if (firstMatchingWord == secondMatchingWord) {
                dbHelper.updateCheckStatus(firstMatchingWord, true)

                Handler(Looper.getMainLooper()).postDelayed({
                    firstSelectedButton?.visibility = View.INVISIBLE
                    secondSelectedButton?.visibility = View.INVISIBLE
                    resetSelection()

                    matchedPairs++
                    checkWinCondition()
                }, 300)
            } else {
                Handler(Looper.getMainLooper()).postDelayed({
                    firstSelectedButton?.alpha = 1.0f
                    secondSelectedButton?.alpha = 1.0f
                    resetSelection()
                }, 500)
            }
        }
    }

    private fun resetSelection() {
        firstSelectedButton = null
        secondSelectedButton = null
        isCheckingMatch = false
    }

    private fun checkWinCondition() {
        if (matchedPairs == totalPairs) {
            countDownTimer?.cancel()
            tvTimer.text = "성공!"
            btnPlusTime.visibility = View.GONE
            btnRetry.visibility = View.VISIBLE // 성공 시에도 다시 하기 버튼 노출


   //         ivCat.setImageResource(R.drawable.cat_smiling)
            ivCat.setImageResource(R.drawable.cat_animation)
            val animation = ivCat.drawable as AnimationDrawable
            animation.start()
        }
    }

    private fun startTimer() {
        countDownTimer?.cancel() // 진행 중인 타이머가 있다면 취소
        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished // 남은 시간 실시간 갱신
                val seconds = millisUntilFinished / 1000
                tvTimer.text = "남은 시간: ${seconds}초"
            }

            override fun onFinish() {
                tvTimer.text = "시간 초과! 실패..."
                handleGameOver()
            }
        }.start()
    }

    // 실패(타이머 종료) 시 남은 벽돌 자동 제거 애니메이션 처리
    private fun handleGameOver() {
        isCheckingMatch = true // 유저 클릭 방지
        btnPlusTime.visibility = View.GONE

        // 아직 투명해지지 않은(화면에 남은) 버튼들 수집
        val remainingButtons = mutableListOf<Button>()
        for (i in 0 until gridLayout.childCount) {
            val child = gridLayout.getChildAt(i) as? Button
            if (child != null && child.visibility == View.VISIBLE) {
                remainingButtons.add(child)
            }
        }

        // Tag(단어)를 기준으로 짝(Pair)끼리 그룹화
        val pairs = remainingButtons.groupBy { it.tag as String }.values.toList()

        var delay = 0L
        val handler = Handler(Looper.getMainLooper())

        // 0.4초 간격으로 남은 쌍들이 차례대로 사라짐
        for (pair in pairs) {
            handler.postDelayed({
                pair.forEach {
                    it.alpha = 0.6f // 선택된 것처럼 살짝 투명해졌다가
                }
            }, delay)

            handler.postDelayed({
                pair.forEach {
                    it.visibility = View.INVISIBLE // 사라짐
                }
            }, delay + 200) // 0.2초 뒤 사라짐

            delay += 400
        }

        // 모든 벽돌이 다 사라진 직후의 마지막 연출
        handler.postDelayed({
            // 울고 있는 고양이 투명도 100%(0이 아닌 1.0f가 가장 진한 상태입니다)로 선명하게 표시
            ivCat.alpha = 1.0f
            // 다시 하기 버튼 등장
            btnRetry.visibility = View.VISIBLE
        }, delay + 300)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}