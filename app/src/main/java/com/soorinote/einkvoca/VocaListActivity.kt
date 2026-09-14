// VocaListActivity.kt
package com.soorinote.einkvoca

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class VocaListActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tts: TextToSpeech
    private lateinit var adapter: VocaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voca_list)

        dbHelper = DatabaseHelper(this)
        tts = TextToSpeech(this, this)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        // 1. 깜빡임을 유발하는 기본 아이템 애니메이터(Fade/Add/Remove 효과) 제거
        recyclerView.itemAnimator = null

        // 2. 모든 아이템 항목의 높이가 일정하다면 성능 향상 및 리프레시 감소
        recyclerView.setHasFixedSize(true)

        adapter = VocaAdapter(dbHelper.getAllWords().toMutableList(), dbHelper) { item ->
            speakWord(item)
        }
        recyclerView.adapter = adapter
    }

    private fun speakWord(item: WordItem) {
        val textToSpeak = "${item.word}. 뜻. ${item.mean}. 예문. ${item.exam}"
        tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREAN
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}

class VocaAdapter(
    private val items: MutableList<WordItem>,
    private val dbHelper: DatabaseHelper,
    private val onTtsClick: (WordItem) -> Unit
) : RecyclerView.Adapter<VocaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvWord: TextView = view.findViewById(R.id.tvWord)
        val tvPron: TextView = view.findViewById(R.id.tvPron)
        val tvMean: TextView = view.findViewById(R.id.tvMean)
        val tvExam: TextView = view.findViewById(R.id.tvExam)
        val cbCheck: CheckBox = view.findViewById(R.id.cbCheck)
        val btnSpeak: ImageButton = view.findViewById(R.id.btnSpeak)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_voca, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvWord.text = item.word
        holder.tvPron.text = "[${item.pron}]"
        holder.tvMean.text = item.mean
        holder.tvExam.text = item.exam

        holder.cbCheck.setOnCheckedChangeListener(null)
        holder.cbCheck.isChecked = item.isChecked

        holder.cbCheck.setOnCheckedChangeListener { _, isChecked ->
            item.isChecked = isChecked
            dbHelper.updateCheckStatus(item.word, isChecked)
        }

        holder.btnSpeak.setOnClickListener {
            onTtsClick(item)
        }
    }

    override fun getItemCount() = items.size
}