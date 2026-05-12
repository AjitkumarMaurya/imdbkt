package io.github.ajitkumarmaurya.imdbkt.sample.ui.detail

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.ajitkumarmaurya.imdbkt.model.CastMember
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    imdbId: String,
    onBack: () -> Unit,
    onCastClick: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val state = uiState) {
                DetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is DetailUiState.Retrying -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Loading… (attempt ${state.attempt} of ${state.maxAttempts})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                        )
                    }
                }
                is DetailUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = viewModel::retry) { Text("Retry") }
                    }
                }
                is DetailUiState.Success -> TitleDetail(state.title, onCastClick)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleDetail(title: ImdbTitle, onCastClick: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {

        // ── Hero poster ────────────────────────────────────────────────────────
        item {
            Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                AsyncImage(
                    model = title.poster,
                    contentDescription = title.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000)),
                                startY = 200f,
                            )
                        )
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                ) {
                    Text(
                        text = title.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        title.year?.let { Text(it, color = Color.LightGray) }
                        if (title.runtimeMinutes != null) {
                            Text("  ·  ${title.runtimeMinutes}m", color = Color.LightGray)
                        }
                        title.certificate?.let {
                            Text("  ·  $it", color = Color.LightGray)
                        }
                    }
                }
            }
        }

        // ── Rating & genres ────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                title.rating?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$rating / 10",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        title.voteCount?.let {
                            Text(
                                "  (${"%,d".format(it)} votes)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (title.genres.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        title.genres.forEach { genre ->
                            AssistChip(onClick = {}, label = { Text(genre) })
                        }
                    }
                }
            }
        }

        // ── Plot ───────────────────────────────────────────────────────────────
        title.description?.let { plot ->
            item {
                var expanded by remember { mutableStateOf(false) }
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Plot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // ── Details row ────────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (title.directors.isNotEmpty()) {
                    DetailRow("Director", title.directors.joinToString { it.name })
                }
                if (title.writers.isNotEmpty()) {
                    DetailRow("Writer", title.writers.take(3).joinToString { it.name })
                }
                if (title.languages.isNotEmpty()) {
                    DetailRow("Language", title.languages.joinToString())
                }
                if (title.countries.isNotEmpty()) {
                    DetailRow("Country", title.countries.joinToString())
                }
                title.releaseDate?.let { DetailRow("Release", it) }
                title.seasons?.let { DetailRow("Seasons", it.toString()) }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Cast ───────────────────────────────────────────────────────────────
        if (title.cast.isNotEmpty()) {
            item {
                Text(
                    "Top Cast",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(title.cast.take(10), key = { it.imdbId }) { member ->
                        CastCard(member = member, onClick = { onCastClick(member.imdbId) })
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CastCard(member: CastMember, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp).clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = member.profileImage,
            contentDescription = member.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = member.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
        member.characters.firstOrNull()?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}
