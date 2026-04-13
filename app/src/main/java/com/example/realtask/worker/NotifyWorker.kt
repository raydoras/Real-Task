package com.example.realtask.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class NotifyWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val taskTitle = inputData.getString("TASK_TITLE") ?: "Tarefa pendente"
        val leadTime = inputData.getInt("LEAD_TIME", 5)

        Log.d("RealTask", "NotifyWorker disparado para a tarefa: $taskTitle")

        showNotification(taskTitle, leadTime)
        return Result.success()
    }

    private fun showNotification(title: String, leadTime: Int) {
        val channelId = "task_notifications"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, 
                "Lembretes de Tarefas", 
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Lembrete de Tarefa")
            .setContentText("A sua tarefa '$title' começa em $leadTime minutos!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        Log.d("RealTask", "Notificação enviada ao NotificationManager")
    }
}
