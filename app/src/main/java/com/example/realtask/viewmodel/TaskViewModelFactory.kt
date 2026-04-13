package com.example.realtask.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.realtask.data.TaskDao

class TaskViewModelFactory(
    private val application: Application,
    private val dao: TaskDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(application, dao) as T
        }
        throw IllegalArgumentException("Classe ViewModel desconhecida")
    }
}
