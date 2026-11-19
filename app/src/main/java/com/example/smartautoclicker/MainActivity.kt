package com.example.smartautoclicker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SmartClickerScreen {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartClickerScreen(onOpenAccessibility: () -> Unit) {
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
                OutlinedButton(onClick = onOpenAccessibility) { Text("إعدادات خدمة النقر") }
            }
            Text("عدد القواعد: ${rules.size}")
            HorizontalDivider()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rules, key = { it.id }) { rule ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(rule.name, style = MaterialTheme.typography.titleMedium)
                            Text("مكان النقر: ${rule.clickX}, ${rule.clickY} | التأخير: ${rule.delayMs} ms")
                            Row {
                                TextButton(onClick = { editing = rule; showEditor = true }) { Text("تعديل") }
                                TextButton(onClick = { rules = rules.filterNot { it.id == rule.id } }) { Text("حذف") }
                                TextButton(onClick = { status = "إعداد القاعدة محفوظ - تشغيل النقر غير مكتمل بعد" }) { Text("تجهيز") }
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
            newId = nextId,
            onDismiss = { showEditor = false },
            onSave = { updated ->
                rules = if (editing == null) rules + updated.copy(id = nextId++)
                else rules.map { if (it.id == updated.id) updated else it }
                showEditor = false
            }
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
    var x by remember { mutableStateOf((initial?.clickX ?: 300).toString()) }
    var y by remember { mutableStateOf((initial?.clickY ?: 300).toString()) }
    var delay by remember { mutableStateOf((initial?.delayMs ?: 500L).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة قاعدة" else "تعديل قاعدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم القاعدة") }, singleLine = true)
                NumberField("X", x) { x = it }
                NumberField("Y", y) { y = it }
                NumberField("التأخير بالمللي ثانية", delay) { delay = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    ClickRule(
                        id = initial?.id ?: newId,
                        name = name.ifBlank { "Rule $newId" },
                        clickX = x.toIntOrNull() ?: 300,
                        clickY = y.toIntOrNull() ?: 300,
                        delayMs = (delay.toLongOrNull() ?: 500L).coerceAtLeast(0L)
                    )
                )
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
