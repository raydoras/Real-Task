package com.example.realtask.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.realtask.data.Task
import com.example.realtask.data.TaskDao
import com.example.realtask.worker.NotifyWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit

class TaskViewModel(application: Application, private val dao: TaskDao) : AndroidViewModel(application) {

    val tasks: StateFlow<List<Task>> = dao.getAllTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTask(title: String, scheduledTime: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            dao.insertTask(Task(title = title, scheduledTime = scheduledTime))
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            dao.updateTask(task.copy(isDone = !task.isDone))
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.deleteTask(task)
        }
    }

    fun scheduleTaskWithLeadTime(taskTitle: String, taskTimeMillis: Long, leadTimeMinutes: Int) {
        val leadTimeMillis = leadTimeMinutes * 60 * 1000L
        val notificationTime = taskTimeMillis - leadTimeMillis
        val delay = notificationTime - System.currentTimeMillis()

        Log.d("RealTask", "--- Iniciando Agendamento ---")
        Log.d("RealTask", "Tarefa: $taskTitle")
        Log.d("RealTask", "Horário da Tarefa: ${Date(taskTimeMillis)}")
        Log.d("RealTask", "Horário da Notificação: ${Date(notificationTime)}")
        Log.d("RealTask", "Atraso (Delay) calculado: ${delay / 1000} segundos")

        if (delay > 0) {
            val data = workDataOf(
                "TASK_TITLE" to taskTitle,
                "LEAD_TIME" to leadTimeMinutes
            )

            val workRequest = OneTimeWorkRequestBuilder<NotifyWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("notification_$taskTitle")
                .build()

            WorkManager.getInstance(getApplication()).enqueue(workRequest)
            Log.d("RealTask", "WorkRequest enfileirado com sucesso!")
        } else {
            Log.w("RealTask", "Aviso: O horário da notificação já passou ou é muito próximo (delay <= 0).")
        }
    }
}
