package com.example.smartautoclicker

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                }
                startForegroundService(serviceIntent)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SmartClickerScreen(
                    onRequestCapture = {
                        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        captureLauncher.launch(manager.createScreenCaptureIntent())
                    },
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onStopCapture = {
                        stopService(Intent(this, ScreenCaptureService::class.java))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartClickerScreen(
    onRequestCapture: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onStopCapture: () -> Unit
) {
    var rules by remember { mutableStateOf(listOf<ClickRule>()) }
    var nextId by remember { mutableIntStateOf(1) }
    var status by remember { mutableStateOf("متوقف") }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ClickRule?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Smart Auto Clicker Pro") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("لوحة التحكم", style = MaterialTheme.typography.headlineSmall)
            Text("الحالة: $status")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = null; showEditor = true }) { Text("إضافة قاعدة") }
                OutlinedButton(onClick = onRequestCapture) { Text("السماح بالتقاط الشاشة") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenAccessibility) { Text("تفعيل خدمة النقر") }
                OutlinedButton(onClick = onStopCapture) { Text("إيقاف الالتقاط") }
            }
            Text("عدد القواعد: ${rules.size}")
            HorizontalDivider()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rules, key = { it.id }) { rule ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(rule.name, style = MaterialTheme.typography.titleMedium)
                            Text("اللون: ${rule.colorHex}  |  التسامح: ${rule.tolerance}")
                            Text("منطقة البحث: ${rule.left}, ${rule.top} إلى ${rule.right}, ${rule.bottom}")
                            Text("مكان النقر: ${rule.clickX}, ${rule.clickY}  |  التأخير: ${rule.delayMs} ms")
                            Row {
                                TextButton(onClick = { editing = rule; showEditor = true }) { Text("تعديل") }
                                TextButton(onClick = { rules = rules.filterNot { it.id == rule.id } }) { Text("حذف") }
                                TextButton(onClick = { status = "جاهز - المحرك غير مربوط بعد" }) { Text("تجهيز") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        RuleEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { updated ->
                rules = if (editing == null) rules + updated.copy(id = nextId++)
                        else rules.map { if (it.id == updated.id) updated else it }
                showEditor = false
            },
            newId = nextId
        )
    }
}

@Composable
private fun RuleEditorDialog(
    initial: ClickRule?,
    newId: Int,
    onDismiss: () -> Unit,
    onSave: (ClickRule) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "Rule $newId") }
    var color by remember { mutableStateOf(initial?.colorHex ?: "#FF0000") }
    var left by remember { mutableStateOf((initial?.left ?: 0).toString()) }
    var top by remember { mutableStateOf((initial?.top ?: 0).toString()) }
    var right by remember { mutableStateOf((initial?.right ?: 500).toString()) }
    var bottom by remember { mutableStateOf((initial?.bottom ?: 500).toString()) }
    var x by remember { mutableStateOf((initial?.clickX ?: 300).toString()) }
    var y by remember { mutableStateOf((initial?.clickY ?: 300).toString()) }
    var tolerance by remember { mutableStateOf((initial?.tolerance ?: 15).toString()) }
    var delay by remember { mutableStateOf((initial?.delayMs ?: 500L).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة قاعدة" else "تعديل قاعدة") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("اسم القاعدة") }, singleLine = true) }
                item { OutlinedTextField(color, { color = it }, label = { Text("لون HEX مثل #FF0000") }, singleLine = true) }
                item { Text("منطقة البحث") }
                item { NumberField("Left", left) { left = it } }
                item { NumberField("Top", top) { top = it } }
                item { NumberField("Right", right) { right = it } }
                item { NumberField("Bottom", bottom) { bottom = it } }
                item { Text("إحداثيات النقر") }
                item { NumberField("X", x) { x = it } }
                item { NumberField("Y", y) { y = it } }
                item { NumberField("Tolerance", tolerance) { tolerance = it } }
                item { NumberField("Delay ms", delay) { delay = it } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rule = ClickRule(
                    id = initial?.id ?: newId,
                    name = name.ifBlank { "Rule $newId" },
                    colorHex = color,
                    left = left.toIntOrNull() ?: 0,
                    top = top.toIntOrNull() ?: 0,
                    right = right.toIntOrNull() ?: 500,
                    bottom = bottom.toIntOrNull() ?: 500,
                    clickX = x.toIntOrNull() ?: 300,
                    clickY = y.toIntOrNull() ?: 300,
                    tolerance = tolerance.toIntOrNull() ?: 15,
                    delayMs = (delay.toLongOrNull() ?: 500L).coerceAtLeast(0L)
                )
                onSave(rule)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.isEmpty() || input.all(Char::isDigit)) onChange(input) },
        label = { Text(label) },
        singleLine = true
    )
}
