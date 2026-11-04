package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.WorldClockItem
import com.example.data.network.CurrencyInfo
import com.example.ui.viewmodel.CurrencyConverterUiState
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PresetWorldCity(val city: String, val zoneId: String, val region: String)

val CuratedWorldCities = listOf(
    PresetWorldCity("New York", "America/New_York", "Americas"),
    PresetWorldCity("San Francisco", "America/Los_Angeles", "Americas"),
    PresetWorldCity("Chicago", "America/Chicago", "Americas"),
    PresetWorldCity("Toronto", "America/Toronto", "Americas"),
    PresetWorldCity("São Paulo", "America/Sao_Paulo", "Americas"),
    PresetWorldCity("London", "Europe/London", "Europe"),
    PresetWorldCity("Paris", "Europe/Paris", "Europe"),
    PresetWorldCity("Zurich", "Europe/Zurich", "Europe"),
    PresetWorldCity("Dubai", "Asia/Dubai", "Middle East"),
    PresetWorldCity("Mumbai", "Asia/Kolkata", "Asia"),
    PresetWorldCity("Singapore", "Asia/Singapore", "Asia"),
    PresetWorldCity("Tokyo", "Asia/Tokyo", "Asia"),
    PresetWorldCity("Seoul", "Asia/Seoul", "Asia"),
    PresetWorldCity("Sydney", "Australia/Sydney", "Oceania"),
    PresetWorldCity("Auckland", "Pacific/Auckland", "Oceania")
)

@Composable
fun GlobeAndCurrencyTabScreen(
    converterState: CurrencyConverterUiState,
    supportedCurrencies: List<CurrencyInfo>,
    worldClocks: List<WorldClockItem>,
    onAmountChange: (String) -> Unit,
    onFromCurrencyChange: (String) -> Unit,
    onToCurrencyChange: (String) -> Unit,
    onSwapCurrencies: () -> Unit,
    onRefreshRates: () -> Unit,
    onAddWorldClock: (String, String) -> Unit,
    onDeleteWorldClock: (String) -> Unit
) {
    var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCityPicker by rememberSaveable { mutableStateOf(false) }

    // Live 1-second ticker for all multi-timezone clocks
    LaunchedEffect(Unit) {
        while (true) {
            nowEpochMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(
                text = stringResource(id = R.string.globe_header_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 1. Live Currency Converter Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(id = R.string.currency_converter_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = converterState.ratesSnapshot?.let {
                                    "Source: ${it.dateLabel} • 1 ${converterState.fromCurrency} = ${
                                        String.format(Locale.US, "%.4f", converterState.unitRate)
                                    } ${converterState.toCurrency}"
                                } ?: "Fetching exchange rates…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onRefreshRates,
                            modifier = Modifier.testTag("refresh_fx_rates_button")
                        ) {
                            if (converterState.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh exchange rates"
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = converterState.amountInput,
                        onValueChange = { next ->
                            if (next.length <= 14 && next.count { it == '.' } <= 1 && next.all { it.isDigit() || it == '.' }) {
                                onAmountChange(next)
                            }
                        },
                        label = { Text("Amount (${converterState.fromCurrency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("converter_amount_input")
                    )

                    Text(
                        text = "From Currency",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(supportedCurrencies, key = { "from_${it.code}" }) { cur ->
                            FilterChip(
                                selected = converterState.fromCurrency == cur.code,
                                onClick = { onFromCurrencyChange(cur.code) },
                                label = { Text("${cur.symbol} ${cur.code}") },
                                modifier = Modifier.testTag("fx_from_${cur.code}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AssistChip(
                            onClick = onSwapCurrencies,
                            label = { Text("Swap ${converterState.fromCurrency} ⇄ ${converterState.toCurrency}") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.SwapVert,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("swap_currencies_button")
                        )
                    }

                    Text(
                        text = "To Currency",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(supportedCurrencies, key = { "to_${it.code}" }) { cur ->
                            FilterChip(
                                selected = converterState.toCurrency == cur.code,
                                onClick = { onToCurrencyChange(cur.code) },
                                label = { Text("${cur.symbol} ${cur.code}") },
                                modifier = Modifier.testTag("fx_to_${cur.code}")
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("converted_result_banner")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Converted Total",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%s %,.2f",
                                    converterState.toCurrency,
                                    converterState.convertedAmount
                                ),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        // 2. Multi-Timezone World Clock Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.world_clocks_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Live synchronized clocks across your pinned horizons",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = { showCityPicker = true },
                    label = { Text(stringResource(id = R.string.add_clock_btn)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("add_world_clock_button")
                )
            }
        }

        items(worldClocks, key = { it.id }) { clock ->
            WorldClockCard(
                clock = clock,
                nowEpochMs = nowEpochMs,
                onDelete = { onDeleteWorldClock(clock.id) }
            )
        }
    }

    if (showCityPicker) {
        AddWorldCitySheet(
            existingZoneIds = worldClocks.map { it.zoneId }.toSet(),
            onSelectCity = { city, zoneId ->
                onAddWorldClock(zoneId, city)
                showCityPicker = false
            },
            onDismiss = { showCityPicker = false }
        )
    }
}

@Composable
private fun WorldClockCard(
    clock: WorldClockItem,
    nowEpochMs: Long,
    onDelete: () -> Unit
) {
    val zone = remember(clock.zoneId) {
        try {
            ZoneId.of(clock.zoneId)
        } catch (e: Exception) {
            ZoneId.of("UTC")
        }
    }
    val localZone = ZoneId.systemDefault()
    val zonedNow: ZonedDateTime = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
    val localNow: ZonedDateTime = Instant.ofEpochMilli(nowEpochMs).atZone(localZone)

    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault()) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()) }

    val diffSeconds = zonedNow.offset.totalSeconds - localNow.offset.totalSeconds
    val diffHours = diffSeconds / 3600.0
    val offsetLabel = when {
        diffSeconds == 0 -> "Same as local time"
        diffHours > 0 -> String.format(Locale.US, "+%.1fh ahead", diffHours).replace(".0h", "h")
        else -> String.format(Locale.US, "%.1fh behind", diffHours).replace(".0h", "h")
    }
    val isDaytime = zonedNow.hour in 6..18

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("world_clock_card_${clock.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDaytime)
                    MaterialTheme.colorScheme.secondaryContainer
                else
                    MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isDaytime) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (isDaytime) "Daytime" else "Nighttime",
                        tint = if (isDaytime)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = clock.cityLabel,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${dateFmt.format(zonedNow)} • $offsetLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = timeFmt.format(zonedNow),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_clock_${clock.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Remove ${clock.cityLabel} clock",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWorldCitySheet(
    existingZoneIds: Set<String>,
    onSelectCity: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(search, existingZoneIds) {
        CuratedWorldCities.filter {
            !existingZoneIds.contains(it.zoneId) &&
                (search.isBlank() ||
                    it.city.contains(search, ignoreCase = true) ||
                    it.region.contains(search, ignoreCase = true) ||
                    it.zoneId.contains(search, ignoreCase = true))
        }
    }

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
                text = "Add World Timezone Clock",
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search city or region…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clock_city_search_input")
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                items(filtered, key = { it.zoneId }) { preset ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCity(preset.city, preset.zoneId) }
                            .padding(vertical = 12.dp)
                            .testTag("select_city_${preset.city.lowercase().replace(" ", "_")}")
                    ) {
                        Text(
                            text = preset.city,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${preset.region} • ${preset.zoneId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
