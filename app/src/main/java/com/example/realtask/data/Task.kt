package com.example.realtask.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Define que esta classe é uma tabela chamada "tasks" no SQLite
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0, // O ID é gerado automaticamente pelo banco
    val title: String,
    val isDone: Boolean = false,
    val timestamp: Long = System.currentTimeMillis() // Para ordenar as tarefas por data de criação
)