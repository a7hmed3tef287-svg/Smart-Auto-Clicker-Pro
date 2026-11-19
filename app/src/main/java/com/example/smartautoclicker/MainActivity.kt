package com.example.smartautoclicker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SmartClickerScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartClickerScreen() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var rules by remember { mutableStateOf(RuleStorage.loadRules(context)) }
    var repeatText by remember { mutableStateOf(RuleStorage.loadRepeatCycles(context).toString()) }
    var status by remember { mutableStateOf("متوقف") }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ClickRule?>(null) }
    val metrics = context.resources.displayMetrics

    LaunchedEffect(rules) { RuleStorage.saveRules(context, rules) }
    LaunchedEffect(repeatText) {
        repeatText.toIntOrNull()?.let { RuleStorage.saveRepeatCycles(context, it) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Smart Auto Clicker Pro") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("النقر التلقائي", style = MaterialTheme.typography.headlineSmall)
            Text("الحالة: $status")
            Text("أضف نقاط النقر ثم اضغط بدء. يبدأ التنفيذ بعد 3 ثوانٍ.")
            Text("الإحداثيات بالبكسل — دقة الشاشة: ${metrics.widthPixels} × ${metrics.heightPixels}")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = null; showEditor = true }) { Text("إضافة نقطة") }
                OutlinedButton(onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                    status = "فعّل خدمة Smart Auto Clicker ثم ارجع واضغط بدء"
                }) { Text("تفعيل خدمة النقر") }
            }

            OutlinedTextField(
                value = repeatText,
                onValueChange = { value -> if (value.isEmpty() || value.all(Char::isDigit)) repeatText = value },
                label = { Text("عدد مرات تكرار القائمة (0 = بلا توقف)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    when {
                        rules.isEmpty() -> status = "أضف نقطة نقر واحدة على الأقل"
                        !ClickAccessibilityService.isEnabled(context) -> {
                            status = "فعّل خدمة النقر من إعدادات إمكانية الوصول أولًا"
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                        else -> {
                            val started = ClickAccessibilityService.startAutomation(
                                context,
                                rules,
                                repeatText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                            )
                            status = if (started) "جارٍ التنفيذ؛ انقل للتطبيق الهدف خلال 3 ثوانٍ" else
                                "الخدمة غير جاهزة؛ ارجع من الإعدادات ثم حاول مجددًا"
                        }
                    }
                }) { Text("بدء") }
                OutlinedButton(onClick = {
                    ClickAccessibilityService.stopAutomation()
                    status = "تم الإيقاف"
                }) { Text("إيقاف") }
            }

            Text("النقاط المحفوظة: ${rules.size}")
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(rule.name, style = MaterialTheme.typography.titleMedium)
                            Text("X: ${rule.clickX}  |  Y: ${rule.clickY}  |  الفاصل: ${rule.delayMs} ms")
                            Row {
                                TextButton(onClick = { editing = rule; showEditor = true }) { Text("تعديل") }
                                TextButton(onClick = { rules = rules.filterNot { it.id == rule.id } }) { Text("حذف") }
                            }
                        }
                    }
                }
            }
            Text("أثناء التشغيل يظهر زر إيقاف عائم؛ يمكنك سحبه لمكان مناسب.", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showEditor) {
        RuleEditorDialog(
            initial = editing,
            newId = (rules.maxOfOrNull { it.id } ?: 0) + 1,
            onDismiss = { showEditor = false },
            onSave = { updated ->
                rules = if (editing == null) rules + updated
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
    var name by remember { mutableStateOf(initial?.name ?: "Point $newId") }
    var x by remember { mutableStateOf((initial?.clickX ?: 300).toString()) }
    var y by remember { mutableStateOf((initial?.clickY ?: 300).toString()) }
    var delay by remember { mutableStateOf((initial?.delayMs ?: 500L).toString()) }
    var validation by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة نقطة نقر" else "تعديل نقطة نقر") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم النقطة") }, singleLine = true)
                NumberField("X بالبكسل", x) { x = it }
                NumberField("Y بالبكسل", y) { y = it }
                NumberField("الفاصل بعد النقرة (ms)", delay) { delay = it }
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val xValue = x.toIntOrNull()
                val yValue = y.toIntOrNull()
                val delayValue = delay.toLongOrNull()
                if (xValue == null || yValue == null || delayValue == null || delayValue < 50L) {
                    validation = "أدخل أرقامًا صحيحة واجعل الفاصل 50 ms أو أكثر"
                } else {
                    onSave(
                        ClickRule(
                            id = initial?.id ?: newId,
                            name = name.trim().ifBlank { "Point $newId" },
                            clickX = xValue,
                            clickY = yValue,
                            delayMs = delayValue
                        )
                    )
                }
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
