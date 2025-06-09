package com.example.todolist

import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.todolist.ui.theme.ToDoListTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import java.util.*

data class Task(
    val title: String,
    val description: String,
    val deadline: LocalDate,
    val priority: String,
    val category: String,
    var isDone: Boolean = false
)

data class AppData(
    val tasks: List<Task>,
    val categories: List<String>
)

enum class SortOption {
    DEADLINE_ASCENDING,
    DEADLINE_DESCENDING,
    PRIORITY_ASCENDING,
    PRIORITY_DESCENDING
}

class MainActivity : ComponentActivity() {

    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (!isGranted) {
                    Toast.makeText(this, "Brak zgody na powiadomienia", Toast.LENGTH_SHORT).show()
                }
            }
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "deadline_channel",
                "Deadline Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        setContent {
            ToDoListTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { padding ->
                    TaskListApp(
                        modifier = Modifier.padding(padding),
                        snackbarHostState = snackbarHostState,
                        scope = scope
                    )
                }
            }
        }
    }
}

@Composable
fun TaskListApp(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val today = LocalDate.now()

    var appData by remember {
        mutableStateOf(StorageHelper.loadAppData(context))
    }

    var tasks by remember { mutableStateOf(appData.tasks) }
    var categoryList by remember { mutableStateOf(appData.categories.toMutableList()) }

    var showCompleted by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Niski") }
    var category by remember { mutableStateOf("Szkoła") }
    var selectedCategory by remember { mutableStateOf("Szkoła") }
    var deadline by remember { mutableStateOf<LocalDate?>(null) }
    var sortOption by remember { mutableStateOf(SortOption.DEADLINE_ASCENDING) }
    var newCategory by remember { mutableStateOf("") }


    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val calendar = Calendar.getInstance()
    val datePicker = DatePickerDialog(
        context,
        { _, year, month, day -> deadline = LocalDate.of(year, month + 1, day) },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    LaunchedEffect(tasks, categoryList) {
        StorageHelper.saveAppData(context, AppData(tasks, categoryList))
    }

    LaunchedEffect(Unit) {
        val ctx = context.applicationContext
        while (true) {
            val upcoming = tasks.filter {
                !it.isDone && (it.deadline == LocalDate.now() || it.deadline == LocalDate.now().plusDays(1))
            }
            if (upcoming.isNotEmpty()) {
                val notification = NotificationCompat.Builder(ctx, "deadline_channel")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Zbliżający się deadline!")
                    .setContentText("Masz ${upcoming.size} zadań na dziś lub jutro.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setContentIntent(
                        PendingIntent.getActivity(
                            ctx,
                            0,
                            Intent(ctx, MainActivity::class.java),
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(1, notification)
            }
            delay(180000)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Dodaj zadanie", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Tytuł") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Opis") }, modifier = Modifier.fillMaxWidth())

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { datePicker.show() }) {
                Text("Deadline: ${deadline?.format(dateFormatter) ?: "Brak"}")
            }
            PriorityDropdown(priority) { priority = it }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CategoryDropdown(categoryList, category) { category = it }
            Button(onClick = {
                if (title.isNotBlank() && deadline != null) {
                    tasks = tasks + Task(title, description, deadline!!, priority, category)
                    title = ""
                    description = ""
                    deadline = null
                    priority = "Niski"
                    category = selectedCategory

                    scope.launch {
                        snackbarHostState.showSnackbar("Dodano nowe zadanie")
                    }
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("Uzupełnij tytuł i deadline!")
                    }
                }
            }) {
                Text("Dodaj")
            }
        }

        OutlinedTextField(value =  newCategory, onValueChange = { newCategory = it }, label = { Text("Nowa lista") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (newCategory.isNotBlank() && !categoryList.contains(newCategory)) {
                categoryList.add(newCategory)
                newCategory = ""

                scope.launch {
                    snackbarHostState.showSnackbar("Dodano nową kategorię")
                }
            }
        }) {
            Text("Dodaj nową listę")
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Aktywna lista:", style = MaterialTheme.typography.titleMedium)
        CategoryDropdown(categoryList, selectedCategory) { selectedCategory = it }

        Spacer(modifier = Modifier.height(8.dp))

        var sortMenuExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { sortMenuExpanded = true }) {
                Text("Sortowanie: ${sortOption.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}")
            }
            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                DropdownMenuItem(text = { Text("Deadline rosnąco") }, onClick = {
                    sortOption = SortOption.DEADLINE_ASCENDING; sortMenuExpanded = false })
                DropdownMenuItem(text = { Text("Deadline malejąco") }, onClick = {
                    sortOption = SortOption.DEADLINE_DESCENDING; sortMenuExpanded = false })
                DropdownMenuItem(text = { Text("Priorytet rosnąco") }, onClick = {
                    sortOption = SortOption.PRIORITY_ASCENDING; sortMenuExpanded = false })
                DropdownMenuItem(text = { Text("Priorytet malejąco") }, onClick = {
                    sortOption = SortOption.PRIORITY_DESCENDING; sortMenuExpanded = false })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showCompleted, onCheckedChange = { showCompleted = it })
            Text("Pokaż ukończone", modifier = Modifier.padding(start = 4.dp))
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Zadania (${selectedCategory})", style = MaterialTheme.typography.titleLarge)

        LazyColumn(modifier = Modifier.weight(1f)) {
            val sortedTasks = tasks
                .filter { it.category == selectedCategory && (showCompleted || !it.isDone) }
                .sortedWith(
                    when (sortOption) {
                        SortOption.DEADLINE_ASCENDING -> compareBy { it.deadline }
                        SortOption.DEADLINE_DESCENDING -> compareByDescending { it.deadline }
                        SortOption.PRIORITY_ASCENDING -> compareBy { priorityValue(it.priority) }
                        SortOption.PRIORITY_DESCENDING -> compareByDescending { priorityValue(it.priority) }
                    }
                )

            items(sortedTasks) { task ->
                TaskItem(
                    task = task,
                    onToggle = {
                        tasks = tasks.map {
                            if (it == task) it.copy(isDone = !it.isDone) else it
                        }
                    },
                    onDelete = {
                        tasks = tasks - task
                        scope.launch {
                            snackbarHostState.showSnackbar("Usunięto zadanie")
                        }
                    }
                )
            }
        }
    }
}

fun priorityValue(priority: String): Int {
    return when (priority) {
        "Niski" -> 1
        "Średni" -> 2
        "Wysoki" -> 3
        else -> Int.MAX_VALUE
    }
}

@Composable
fun PriorityDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("Niski", "Średni", "Wysoki")

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Priorytet: $selected")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach {
                DropdownMenuItem(text = { Text(it) }, onClick = {
                    onSelect(it)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun CategoryDropdown(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Kategoria: $selected")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach {
                DropdownMenuItem(text = { Text(it) }, onClick = {
                    onSelect(it)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun TaskItem(task: Task, onToggle: () -> Unit, onDelete: () -> Unit) {
    val color = when (task.priority) {
        "Wysoki" -> Color.Red
        "Średni" -> Color(0xFFFFA500)
        "Niski" -> Color.Green
        else -> Color.Gray
    }

    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tytuł: ${task.title}", style = MaterialTheme.typography.bodyLarge)
                    Text("Opis: ${task.description}")
                    Text("Deadline: ${task.deadline}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(color, shape = CircleShape)
                        )
                        Text(" Priorytet: ${task.priority}", modifier = Modifier.padding(start = 4.dp))
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Checkbox(
                        checked = task.isDone,
                        onCheckedChange = { onToggle() }
                    )
                    TextButton(onClick = { onDelete() }, contentPadding = PaddingValues(0.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Usuń",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Usuń", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
