package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.DecryptedJournalEntry
import com.example.data.model.JournalTag
import com.example.data.model.MoodType
import com.example.data.util.GratitudePromptProvider

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JournalEditorScreen(
    existingEntry: DecryptedJournalEntry?,
    tags: List<JournalTag>,
    isBusy: Boolean,
    onManageTagsClick: () -> Unit,
    onSave: (
        title: String,
        body: String,
        gratitudeResponse: String,
        gratitudePromptText: String,
        mood: MoodType,
        tagIds: List<String>,
        pickedPhotoUri: Uri?,
        existingEncryptedPhotoBase64: String,
        removePhoto: Boolean,
        entryDateEpochMs: Long
    ) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val entryDateMs = existingEntry?.entryDateEpochMs ?: System.currentTimeMillis()
    val dailyPrompt = existingEntry?.gratitudePromptText?.takeIf { it.isNotBlank() }
        ?: GratitudePromptProvider.getPromptForEpochMs(entryDateMs)

    var title by rememberSaveable { mutableStateOf(existingEntry?.title ?: "") }
    var body by rememberSaveable { mutableStateOf(existingEntry?.body ?: "") }
    var gratitudeResponse by rememberSaveable { mutableStateOf(existingEntry?.gratitudeResponse ?: "") }
    var selectedMoodName by rememberSaveable { mutableStateOf((existingEntry?.mood ?: MoodType.GRATEFUL).name) }
    var selectedTagIds by remember { mutableStateOf(existingEntry?.tagIds?.toSet() ?: emptySet()) }
    var pickedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var removeExistingPhoto by rememberSaveable { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedPhotoUri = uri
            removeExistingPhoto = false
        }
    }

    val existingBitmap = remember(existingEntry?.photoBytes) {
        val bytes = existingEntry?.photoBytes
        if (bytes != null && bytes.isNotEmpty()) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (existingEntry == null) "New Reflection" else "Edit Reflection",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "AES-256 Encrypted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to journal"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            onSave(
                                title,
                                body,
                                gratitudeResponse,
                                dailyPrompt,
                                MoodType.fromKey(selectedMoodName),
                                selectedTagIds.toList(),
                                pickedPhotoUri,
                                existingEntry?.encryptedPhotoBase64 ?: "",
                                removeExistingPhoto,
                                entryDateMs
                            )
                        },
                        enabled = !isBusy && (title.isNotBlank() || body.isNotBlank() || gratitudeResponse.isNotBlank()),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("save_journal_entry_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Mood Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "How are you feeling right now?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MoodType.entries.forEach { mood ->
                        val isSelected = selectedMoodName == mood.name
                        val accent = Color(mood.accentHex)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) accent else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedMoodName = mood.name }
                                .testTag("mood_option_${mood.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = mood.emoji)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mood.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 2. Daily Rotating Gratitude Prompt Card inside the Journal Entry
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Daily Gratitude Prompt",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = dailyPrompt,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("editor_gratitude_prompt_text")
                    )
                    OutlinedTextField(
                        value = gratitudeResponse,
                        onValueChange = { gratitudeResponse = it },
                        placeholder = { Text("Write what you are thankful for today…") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gratitude_response_input")
                    )
                }
            }

            // 3. Entry Title & Body
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Entry Title") },
                placeholder = { Text("Give today's chapter a title…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("journal_title_input")
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Personal Journal") },
                placeholder = { Text("Pour your thoughts freely—everything here is encrypted with your vault passcode before syncing…") },
                minLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("journal_body_input")
            )

            // 4. Multi-Tag Association
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AssistChip(
                        onClick = onManageTagsClick,
                        label = { Text("Edit Tags") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Label,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("editor_manage_tags_chip")
                    )
                }

                if (tags.isEmpty()) {
                    Text(
                        text = "No tags yet. Tap 'Edit Tags' to create tags like Work, Family, or Travel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            val isSelected = selectedTagIds.contains(tag.id)
                            val tagColor = parseHexColor(tag.colorHex)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTagIds = if (isSelected) {
                                        selectedTagIds - tag.id
                                    } else {
                                        selectedTagIds + tag.id
                                    }
                                },
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
                                    selectedContainerColor = tagColor.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier.testTag("editor_tag_chip_${tag.id}")
                            )
                        }
                    }
                }
            }

            // 5. Encrypted Photo Attachment (Zero-Permission Android Photo Picker)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Encrypted Photo Memory",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (pickedPhotoUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        AsyncImage(
                            model = pickedPhotoUri,
                            contentDescription = "Selected photo attachment",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { pickedPhotoUri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    CircleShape
                                )
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove photo")
                        }
                    }
                } else if (existingBitmap != null && !removeExistingPhoto) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        Image(
                            bitmap = existingBitmap,
                            contentDescription = "Encrypted photo memory",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { removeExistingPhoto = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    CircleShape
                                )
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove photo")
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("attach_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (pickedPhotoUri != null || (existingBitmap != null && !removeExistingPhoto))
                            "Replace Encrypted Photo"
                        else
                            "Attach Photo from Gallery"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
