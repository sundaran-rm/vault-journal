package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DecryptedJournalEntry
import com.example.data.model.JournalTag
import com.example.data.model.MoodType
import com.example.ui.viewmodel.SyncModeStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalTabScreen(
    allEntries: List<DecryptedJournalEntry>,
    filteredEntries: List<DecryptedJournalEntry>,
    tags: List<JournalTag>,
    todayGratitudePrompt: String,
    searchQuery: String,
    selectedFilterTagIds: Set<String>,
    selectedMoodFilter: MoodType?,
    onSearchQueryChange: (String) -> Unit,
    onToggleTagFilter: (String) -> Unit,
    onSelectMoodFilter: (MoodType?) -> Unit,
    onClearFilters: () -> Unit,
    onOpenTagManager: () -> Unit,
    onNewEntryClick: () -> Unit,
    onEditEntryClick: (DecryptedJournalEntry) -> Unit,
    onDeleteEntryClick: (String) -> Unit,
    onQuickLockClick: () -> Unit,
    syncStatus: SyncModeStatus = SyncModeStatus.CLOUD_SYNCED
) {
    val tagsById = remember(tags) { tags.associateBy { it.id } }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Sanctuary Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.journal_header_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        SyncStatusPill(
                            syncStatus = syncStatus,
                            itemCountText = "${allEntries.size} ${if (allEntries.size == 1) "entry" else "entries"}",
                            testTag = "journal_sync_status_badge"
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = onOpenTagManager,
                            label = { Text(stringResource(id = R.string.manage_tags_btn)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Label,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("open_tag_manager_button")
                        )
                        IconButton(
                            onClick = onQuickLockClick,
                            modifier = Modifier.testTag("quick_lock_vault_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Lock Vault Now",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Daily Gratitude Prompt Spotlight Banner
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("daily_gratitude_banner"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(id = R.string.gratitude_card_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                )
                            }
                            Text(
                                text = todayGratitudePrompt,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Button(
                                onClick = onNewEntryClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("reflect_daily_gratitude_button")
                            ) {
                                Text(stringResource(id = R.string.gratitude_reflect_btn))
                            }
                        }
                    }
                }
            }

            // Mood Pulse Summary Strip
            if (allEntries.isNotEmpty()) {
                item {
                    MoodPulseCard(
                        allEntries = allEntries,
                        selectedMoodFilter = selectedMoodFilter,
                        onSelectMood = onSelectMoodFilter
                    )
                }
            }

            // Search & Multi-Tag Filter Controls
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(stringResource(id = R.string.search_journal_placeholder)) },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("journal_search_input")
                    )

                    if (tags.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedFilterTagIds.isNotEmpty() || selectedMoodFilter != null) {
                                item {
                                    AssistChip(
                                        onClick = onClearFilters,
                                        label = { Text("Reset") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.FilterAltOff,
                                                contentDescription = "Clear filters",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("clear_tag_filters_chip")
                                    )
                                }
                            }
                            items(tags, key = { it.id }) { tag ->
                                val isSelected = selectedFilterTagIds.contains(tag.id)
                                val tagColor = parseHexColor(tag.colorHex)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onToggleTagFilter(tag.id) },
                                    label = { Text(tag.name) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(tagColor)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = tagColor.copy(alpha = 0.24f)
                                    ),
                                    modifier = Modifier.testTag("filter_tag_chip_${tag.id}")
                                )
                            }
                        }
                    }
                }
            }

            // Filtered Journal Entries or Empty State
            if (filteredEntries.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = if (allEntries.isEmpty())
                                    stringResource(id = R.string.empty_journal_title)
                                else
                                    "No Matching Entries",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = if (allEntries.isEmpty())
                                    stringResource(id = R.string.empty_journal_subtitle)
                                else
                                    "Try clearing one or more tag filters or updating your search keywords.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredEntries, key = { it.id }) { entry ->
                    JournalEntryCard(
                        entry = entry,
                        tagsById = tagsById,
                        onClick = { onEditEntryClick(entry) },
                        onDelete = { onDeleteEntryClick(entry.id) }
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onNewEntryClick,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(id = R.string.new_entry_fab)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("new_journal_fab")
        )
    }
}

@Composable
private fun MoodPulseCard(
    allEntries: List<DecryptedJournalEntry>,
    selectedMoodFilter: MoodType?,
    onSelectMood: (MoodType?) -> Unit
) {
    val counts = remember(allEntries) {
        allEntries.groupingBy { it.mood }.eachCount()
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Mood Pulse (tap to filter)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MoodType.entries) { mood ->
                    val count = counts[mood] ?: 0
                    val active = selectedMoodFilter == mood
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (active)
                            Color(mood.accentHex).copy(alpha = 0.25f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier
                            .clickable { onSelectMood(mood) }
                            .testTag("mood_filter_${mood.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mood.emoji)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${mood.label} ($count)",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JournalEntryCard(
    entry: DecryptedJournalEntry,
    tagsById: Map<String, JournalTag>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy • h:mm a", Locale.getDefault()) }
    val formattedDate = remember(entry.entryDateEpochMs) {
        dateFormat.format(Date(entry.entryDateEpochMs))
    }
    val photoBitmap = remember(entry.photoBytes) {
        val bytes = entry.photoBytes
        if (bytes != null && bytes.isNotEmpty()) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } else {
            null
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("journal_entry_card_${entry.id}")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(entry.mood.accentHex).copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(entry.mood.emoji)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = entry.mood.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_journal_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete entry ${entry.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap,
                    contentDescription = "Attached journal photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Text(
                text = entry.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (entry.gratitudeResponse.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Gratitude • ${entry.gratitudePromptText}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.gratitudeResponse,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            val resolvedTags = entry.tagIds.mapNotNull { tagsById[it] }
            if (resolvedTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    resolvedTags.forEach { tag ->
                        val c = parseHexColor(tag.colorHex)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = c.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyncStatusPill(
    syncStatus: SyncModeStatus,
    itemCountText: String,
    testTag: String
) {
    val (icon, label, containerColor, contentColor) = when (syncStatus) {
        SyncModeStatus.LOCAL_ONLY_NO_ACCOUNT -> Quad(
            Icons.Filled.Shield,
            stringResource(id = R.string.sync_badge_local_only),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        SyncModeStatus.CLOUD_OFFLINE_CACHED -> Quad(
            Icons.Filled.CloudOff,
            stringResource(id = R.string.sync_badge_cloud_offline),
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        SyncModeStatus.CLOUD_PENDING_UPLOAD -> Quad(
            Icons.Filled.CloudSync,
            stringResource(id = R.string.sync_badge_cloud_syncing),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        SyncModeStatus.CLOUD_SYNCED -> Quad(
            Icons.Filled.CloudDone,
            stringResource(id = R.string.sync_badge_cloud_synced),
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$itemCountText • $label",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

