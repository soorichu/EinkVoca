// VocaListActivity.kt
package com.soorinote.einkvoca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class VocaListActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: VocaAdapter
    private val items = mutableListOf<WordItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voca_list)

        val btnHome = findViewById<Button>(R.id.btnHome)
        btnHome.setOnClickListener {
            finish()
        }

        // 화면 전환 애니메이션 끄기 (E-Ink 깜빡임 방지)
        window.setWindowAnimations(0)

        dbHelper = DatabaseHelper(this)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // E-Ink 화면 깜빡임 방지: 기본 애니메이터 제거
        recyclerView.itemAnimator = null

        // 1. DB에서 단어 목록 로드
        items.addAll(dbHelper.getAllWords())

        // 2. 어댑터 초기화 및 리사이클러뷰 연결
        adapter = VocaAdapter(items, dbHelper)
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        // 다른 화면에서 단어가 추가되거나 삭제되었을 때 목록 동기화
        items.clear()
        items.addAll(dbHelper.getAllWords())
        adapter.notifyDataSetChanged()
    }
}

class VocaAdapter(
    private val items: MutableList<WordItem>,
    private val dbHelper: DatabaseHelper,
) : RecyclerView.Adapter<VocaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvWord: TextView = view.findViewById(R.id.tvWord)
        val tvPron: TextView = view.findViewById(R.id.tvPron)
        val tvMean: TextView = view.findViewById(R.id.tvMean)
        val tvExam: TextView = view.findViewById(R.id.tvExam)
        val cbCheck: CheckBox = view.findViewById(R.id.cbCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_voca, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvWord.text = item.word

        // 발음기호가 비어있을 경우 대괄호 []만 나오는 현상 방지
        holder.tvPron.text = if (item.pron.isNotBlank()) "[${item.pron}]" else ""
        holder.tvMean.text = item.mean
        holder.tvExam.text = item.exam

        // 재사용 시 리스너 중복 호출 방지
        holder.cbCheck.setOnCheckedChangeListener(null)
        holder.cbCheck.isChecked = item.isChecked

        holder.cbCheck.setOnCheckedChangeListener { _, isChecked ->
            item.isChecked = isChecked
            dbHelper.updateCheckStatus(item.word, isChecked)
        }
    }

    override fun getItemCount() = items.size
}