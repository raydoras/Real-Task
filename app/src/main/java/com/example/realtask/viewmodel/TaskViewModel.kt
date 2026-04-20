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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit

class TaskViewModel(application: Application, private val dao: TaskDao) : AndroidViewModel(application) {

    private val firestore = FirebaseFirestore.getInstance()
    private val tasksCollection = firestore.collection("tasks")

    val tasks: StateFlow<List<Task>> = dao.getAllTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTask(title: String, scheduledTime: Long, leadTimeMinutes: Int) {
        viewModelScope.launch {
            // 1. Criar a tarefa para o Room
            val localTask = Task(
                title = title, 
                scheduledTime = scheduledTime, 
                leadTimeMinutes = leadTimeMinutes
            )
            
            // 2. Salvar no Room e obter o ID (Long) gerado
            val generatedId = dao.insertTask(localTask)
            
            // 3. Salvar no Firestore usando o ID convertido para String e enviando o objeto Task com o ID correto
            val taskToSync = localTask.copy(id = generatedId.toInt())
            
            tasksCollection.document(generatedId.toString()).set(taskToSync)
                .addOnSuccessListener { 
                    Log.d("RealTask", "Sincronizado com Firestore: ${taskToSync.title}") 
                }
                .addOnFailureListener { e -> 
                    Log.w("RealTask", "Salvo apenas localmente (Firestore offline/erro)", e) 
                }
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            val updatedTask = task.copy(isDone = !task.isDone)
            dao.updateTask(updatedTask)
            
            // Atualiza no Firestore usando o ID do Room como chave do documento
            tasksCollection.document(task.id.toString()).set(updatedTask)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.deleteTask(task)
            
            // Remove do Firestore
            tasksCollection.document(task.id.toString()).delete()
        }
    }

    fun scheduleTaskWithLeadTime(taskTitle: String, taskTimeMillis: Long, leadTimeMinutes: Int) {
        val leadTimeMillisTotal = leadTimeMinutes * 60 * 1000L
        val notificationTime = taskTimeMillis - leadTimeMillisTotal
        val delay = notificationTime - System.currentTimeMillis()

        Log.d("RealTask", "--- Iniciando Agendamento ---")
        Log.d("RealTask", "Tarefa: $taskTitle")
        Log.d("RealTask", "Horário da Notificação: ${Date(notificationTime)}")
        Log.d("RealTask", "Atraso (Delay): ${delay / 1000} segundos")

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
            Log.d("RealTask", "Notificação agendada via WorkManager")
        }
    }
}
