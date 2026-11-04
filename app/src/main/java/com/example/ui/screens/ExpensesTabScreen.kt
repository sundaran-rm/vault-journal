package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DecryptedExpenseItem
import com.example.data.model.ExpenseCategory
import com.example.data.network.CurrencyInfo
import com.example.data.network.ExchangeRatesSnapshot
import com.example.ui.viewmodel.SyncModeStatus
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
fun ExpensesTabScreen(
    expenses: List<DecryptedExpenseItem>,
    displayCurrency: String,
    supportedCurrencies: List<CurrencyInfo>,
    ratesSnapshot: ExchangeRatesSnapshot?,
    onSelectDisplayCurrency: (String) -> Unit,
    onAddExpense: (
        title: String,
        notes: String,
        amountMajor: Double,
        currencyCode: String,
        category: ExpenseCategory
    ) -> Unit,
    onDeleteExpense: (String) -> Unit,
    syncStatus: SyncModeStatus = SyncModeStatus.CLOUD_SYNCED
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    // Convert any expense into the chosen displayCurrency using live/fallback rates
    fun convertMinorToDisplayMajor(item: DecryptedExpenseItem): Double {
        val major = item.amountMinor / 100.0
        if (item.currencyCode == displayCurrency) return major
        val rates = ratesSnapshot?.rates ?: emptyMap()
        // If ratesSnapshot is anchored at displayCurrency, 1 displayCurrency = rate[item.currencyCode]
        val rateFromDisplay = if (ratesSnapshot?.baseCurrency == displayCurrency) {
            rates[item.currencyCode]
        } else {
            null
        }
        return if (rateFromDisplay != null && rateFromDisplay > 0.0) {
            major / rateFromDisplay
        } else {
            major
        }
    }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    val todaySpend = expenses.filter {
        Instant.ofEpochMilli(it.spentAtEpochMs).atZone(zone).toLocalDate() == today
    }.sumOf { convertMinorToDisplayMajor(it) }

    val monthSpend = expenses.filter {
        val d = Instant.ofEpochMilli(it.spentAtEpochMs).atZone(zone).toLocalDate()
        d.year == today.year && d.month == today.month
    }.sumOf { convertMinorToDisplayMajor(it) }

    val last7DaysTotals = remember(expenses, displayCurrency, ratesSnapshot) {
        (6 downTo 0).map { offset ->
            val targetDate = today.minusDays(offset.toLong())
            val sum = expenses.filter {
                Instant.ofEpochMilli(it.spentAtEpochMs).atZone(zone).toLocalDate() == targetDate
            }.sumOf { convertMinorToDisplayMajor(it) }
            targetDate.dayOfWeek.name.take(3) to sum
        }
    }

    val categoryTotals = remember(expenses, displayCurrency, ratesSnapshot) {
        ExpenseCategory.entries.mapNotNull { cat ->
            val sum = expenses.filter { it.category == cat }.sumOf { convertMinorToDisplayMajor(it) }
            if (sum > 0.0) cat to sum else null
        }.sortedByDescending { it.second }
    }

    val currencySymbol = supportedCurrencies.firstOrNull { it.code == displayCurrency }?.symbol ?: displayCurrency

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(id = R.string.expenses_header_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    SyncStatusPill(
                        syncStatus = syncStatus,
                        itemCountText = "${expenses.size} ${if (expenses.size == 1) "record" else "records"}",
                        testTag = "expenses_sync_status_badge"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(supportedCurrencies.take(6), key = { it.code }) { currency ->
                            FilterChip(
                                selected = displayCurrency == currency.code,
                                onClick = { onSelectDisplayCurrency(currency.code) },
                                label = { Text("${currency.symbol} ${currency.code}") },
                                modifier = Modifier.testTag("expense_display_currency_${currency.code}")
                            )
                        }
                    }
                }
            }

            // Summary KPI Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Today's Spend",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = String.format(Locale.US, "%s%.2f", currencySymbol, todaySpend),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "This Month",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = String.format(Locale.US, "%s%.2f", currencySymbol, monthSpend),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // 7-Day Spending Habit Bar Chart
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "7-Day Daily Spending Rhythm ($displayCurrency)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val barColor = MaterialTheme.colorScheme.primary
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        val maxDayVal = (last7DaysTotals.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        ) {
                            val count = last7DaysTotals.size
                            val slotWidth = size.width / count
                            val barWidth = (slotWidth * 0.48f).coerceAtLeast(12f)
                            last7DaysTotals.forEachIndexed { idx, (_, value) ->
                                val ratio = (value / maxDayVal).toFloat().coerceIn(0.05f, 1f)
                                val barHeight = if (value <= 0.0) 8f else size.height * ratio
                                val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                                drawRoundRect(
                                    color = trackColor,
                                    topLeft = Offset(x, 0f),
                                    size = Size(barWidth, size.height),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )
                                drawRoundRect(
                                    color = if (value > 0.0) barColor else trackColor,
                                    topLeft = Offset(x, size.height - barHeight),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            last7DaysTotals.forEach { (dayLabel, _) ->
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Category Breakdown
            if (categoryTotals.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val grandTotal = categoryTotals.sumOf { it.second }.coerceAtLeast(0.01)
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Category Breakdown",
                                style = MaterialTheme.typography.titleMedium
                            )
                            categoryTotals.forEach { (cat, sum) ->
                                val pct = (sum / grandTotal).toFloat().coerceIn(0f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(cat.label, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            text = String.format(Locale.US, "%s%.2f", currencySymbol, sum),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { pct },
                                        color = Color(cat.accentHex),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Expense List or Empty State
            if (expenses.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.empty_expenses_title),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = stringResource(id = R.string.empty_expenses_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(expenses, key = { it.id }) { item ->
                    ExpenseRowCard(
                        item = item,
                        onDelete = { onDeleteExpense(item.id) }
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAddSheet = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(id = R.string.new_expense_fab)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_expense_fab")
        )
    }

    if (showAddSheet) {
        AddExpenseBottomSheet(
            defaultCurrency = displayCurrency,
            supportedCurrencies = supportedCurrencies,
            onDismiss = { showAddSheet = false },
            onSave = { title, notes, amount, currency, category ->
                onAddExpense(title, notes, amount, currency, category)
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun ExpenseRowCard(
    item: DecryptedExpenseItem,
    onDelete: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()) }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("expense_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(item.category.accentHex).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(item.category.accentHex))
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${item.category.label} • ${dateFmt.format(Date(item.spentAtEpochMs))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.notes.isNotBlank()) {
                    Text(
                        text = item.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = String.format(Locale.US, "%s %.2f", item.currencyCode, item.amountMinor / 100.0),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_expense_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete expense ${item.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddExpenseBottomSheet(
    defaultCurrency: String,
    supportedCurrencies: List<CurrencyInfo>,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, ExpenseCategory) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var selectedCurrency by rememberSaveable { mutableStateOf(defaultCurrency) }
    var selectedCategoryName by rememberSaveable { mutableStateOf(ExpenseCategory.DINING.name) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Log Encrypted Expense",
                style = MaterialTheme.typography.headlineSmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { next ->
                        if (next.length <= 12 && next.count { it == '.' } <= 1 && next.all { it.isDigit() || it == '.' }) {
                            amountText = next
                        }
                    },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("expense_amount_input")
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Merchant / Item") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("expense_title_input")
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(supportedCurrencies, key = { it.code }) { cur ->
                    FilterChip(
                        selected = selectedCurrency == cur.code,
                        onClick = { selectedCurrency = cur.code },
                        label = { Text("${cur.symbol} ${cur.code}") },
                        modifier = Modifier.testTag("sheet_currency_${cur.code}")
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExpenseCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryName == cat.name,
                        onClick = { selectedCategoryName = cat.name },
                        label = { Text(cat.label) },
                        modifier = Modifier.testTag("sheet_category_${cat.name.lowercase()}")
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Optional Note (Encrypted)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_notes_input")
            )

            Button(
                onClick = {
                    val parsed = amountText.toDoubleOrNull() ?: 0.0
                    if (parsed > 0.0) {
                        onSave(
                            title,
                            notes,
                            parsed,
                            selectedCurrency,
                            ExpenseCategory.fromKey(selectedCategoryName)
                        )
                    }
                },
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0.0,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_expense_button")
            ) {
                Text("Save Expense")
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
