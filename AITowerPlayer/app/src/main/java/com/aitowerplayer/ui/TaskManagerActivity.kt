package com.aitowerplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aitowerplayer.R
import com.aitowerplayer.databinding.ActivityTaskManagerBinding
import com.aitowerplayer.databinding.DialogEditTaskBinding
import com.aitowerplayer.databinding.ItemTaskBinding
import com.aitowerplayer.model.TaskItem
import com.aitowerplayer.util.ConfigManager
import com.aitowerplayer.util.LogManager

class TaskManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskManagerBinding
    private lateinit var adapter: TaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Task Manager"

        adapter = TaskAdapter(
            onEdit = { showEditDialog(it) },
            onDelete = { deleteTask(it) },
            onToggle = { task, enabled ->
                task.enabled = enabled
                ConfigManager.updateTask(task)
            }
        )
        binding.rvTasks.layoutManager = LinearLayoutManager(this)
        binding.rvTasks.adapter = adapter

        binding.btnAddTask.setOnClickListener {
            showEditDialog(null)
        }

        refresh()
    }

    private fun refresh() {
        val list = ConfigManager.tasks.toList()
        adapter.submit(list)
        binding.tvEmptyTasks.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.rvTasks.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showEditDialog(existing: TaskItem?) {
        val dialogBinding = DialogEditTaskBinding.inflate(layoutInflater)
        if (existing != null) {
            dialogBinding.etTaskName.setText(existing.name)
            dialogBinding.etCoordX.setText(existing.x.toString())
            dialogBinding.etCoordY.setText(existing.y.toString())
            dialogBinding.etDelay.setText(existing.delayMs.toString())
            dialogBinding.etOrder.setText(existing.order.toString())
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Tambah Task" else "Edit Task")
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                val name = dialogBinding.etTaskName.text?.toString()?.ifBlank { "Task" } ?: "Task"
                val x = dialogBinding.etCoordX.text?.toString()?.toIntOrNull() ?: 0
                val y = dialogBinding.etCoordY.text?.toString()?.toIntOrNull() ?: 0
                val delay = dialogBinding.etDelay.text?.toString()?.toLongOrNull() ?: 800L
                val order = dialogBinding.etOrder.text?.toString()?.toIntOrNull() ?: 1

                if (existing == null) {
                    val task = TaskItem(name = name, x = x, y = y, delayMs = delay, order = order)
                    // Auto-detect trigger from name
                    if (name.contains("SMASH", ignoreCase = true) || name.contains("popup", ignoreCase = true)) {
                        task.trigger = "AFTER_POPUP"
                    } else {
                        task.trigger = "AFTER_WIN"
                    }
                    ConfigManager.addTask(task)
                    LogManager.log("Task ditambah: $name")
                } else {
                    existing.name = name
                    existing.x = x
                    existing.y = y
                    existing.delayMs = delay
                    existing.order = order
                    ConfigManager.updateTask(existing)
                    LogManager.log("Task diupdate: $name")
                }
                refresh()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteTask(task: TaskItem) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Task")
            .setMessage("Hapus '${task.name}'?")
            .setPositiveButton("Hapus") { _, _ ->
                ConfigManager.removeTask(task.id)
                LogManager.log("Task dihapus: ${task.name}")
                refresh()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // --- Adapter ---
    class TaskAdapter(
        private val onEdit: (TaskItem) -> Unit,
        private val onDelete: (TaskItem) -> Unit,
        private val onToggle: (TaskItem, Boolean) -> Unit
    ) : RecyclerView.Adapter<TaskAdapter.VH>() {

        private var items: List<TaskItem> = emptyList()

        fun submit(list: List<TaskItem>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class VH(private val b: ItemTaskBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(task: TaskItem) {
                b.tvTaskName.text = task.name
                val unit = if (task.usePercent) "%" else "px"
                b.tvTaskInfo.text = "X: ${task.x}$unit  Y: ${task.y}$unit  Delay: ${task.delayMs}ms  Order: ${task.order}  [${task.trigger}]"
                b.switchEnabled.setOnCheckedChangeListener(null)
                b.switchEnabled.isChecked = task.enabled
                b.switchEnabled.setOnCheckedChangeListener { _, checked ->
                    onToggle(task, checked)
                }
                b.btnEdit.setOnClickListener { onEdit(task) }
                b.btnDelete.setOnClickListener { onDelete(task) }
            }
        }
    }
}
