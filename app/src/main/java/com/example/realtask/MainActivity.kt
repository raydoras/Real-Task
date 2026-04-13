package com.example.realtask

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.realtask.data.AppDatabase
import com.example.realtask.data.Task
import com.example.realtask.ui.theme.RealTaskTheme
import com.example.realtask.viewmodel.TaskViewModel
import com.example.realtask.viewmodel.TaskViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("RealTask", "Permissão de notificação concedida: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val db = AppDatabase.getDatabase(this)
        val viewModel: TaskViewModel by viewModels {
            TaskViewModelFactory(application, db.taskDao())
        }

        setContent {
            RealTaskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: TaskViewModel) {
    val taskList by viewModel.tasks.collectAsState()
    var newTaskName by remember { mutableStateOf("") }
    var selectedLeadTime by remember { mutableIntStateOf(5) }
    val leadTimeOptions = listOf(5, 10, 15, 30)

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()

    // CORREÇÃO: O DatePicker retorna milissegundos em UTC (meia-noite).
    // Para exibir o dia correto no botão, o formatador também deve usar UTC.
    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    
    val selectedDateDisplay = datePickerState.selectedDateMillis?.let { dateFormatter.format(Date(it)) } ?: "Selecionar Data"
    val selectedTimeDisplay = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Real Task") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = newTaskName,
                onValueChange = { newTaskName = it },
                label = { Text("Nome da tarefa") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Text(selectedDateDisplay)
                }
                Button(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                    Text(selectedTimeDisplay)
                }
            }

            Text(
                "Notificar quanto tempo antes?",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                leadTimeOptions.forEach { minutes ->
                    FilterChip(
                        selected = selectedLeadTime == minutes,
                        onClick = { selectedLeadTime = minutes },
                        label = { Text("${minutes}min") }
                    )
                }
            }

            Button(
                onClick = {
                    val dateMillis = datePickerState.selectedDateMillis
                    if (newTaskName.isNotBlank() && dateMillis != null) {
                        
                        // Extraímos os campos UTC para montar o calendário local corretamente
                        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = dateMillis
                        }
                        
                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR))
                            set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        val finalTimestamp = calendar.timeInMillis
                        Log.d("RealTask", "Tarefa: $newTaskName | Agendado para: ${Date(finalTimestamp)}")

                        viewModel.addTask(newTaskName, finalTimestamp)
                        viewModel.scheduleTaskWithLeadTime(newTaskName, finalTimestamp, selectedLeadTime)

                        newTaskName = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Adicionar e Agendar")
            }

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("OK") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showTimePicker) {
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = { showTimePicker = false }) { Text("OK") }
                    },
                    title = { Text("Escolha o horário") },
                    text = { TimePicker(state = timePickerState) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Minhas Tarefas", style = MaterialTheme.typography.titleMedium)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(taskList) { task ->
                    TaskRow(
                        task = task,
                        onCheckedChange = { viewModel.toggleTask(task) },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskRow(task: Task, onCheckedChange: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = task.isDone, onCheckedChange = { onCheckedChange() })
            Column(modifier = Modifier.weight(1f)) {
                Text(text = task.title, style = MaterialTheme.typography.bodyLarge)
                val timeFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                Text(
                    text = "Agendado para: ${timeFormat.format(Date(task.scheduledTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
