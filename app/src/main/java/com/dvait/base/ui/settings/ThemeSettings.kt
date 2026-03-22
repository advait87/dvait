package com.dvait.base.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dvait.base.data.settings.SettingsDataStore
import com.dvait.base.ui.theme.*
import com.dvait.base.ui.components.AccentSelectorCircle
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettings(
  settingsDataStore: SettingsDataStore,
  onBack: () -> Unit
) {
  val scope = rememberCoroutineScope()
  val appTheme by settingsDataStore.appTheme.collectAsState(initial = "system")
  val accentColor by settingsDataStore.accentColor.collectAsState(initial = "orange")
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Theme", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          titleContentColor = MaterialTheme.colorScheme.onBackground
        )
      )
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
      ) {
          item {
            SectionLabel("Theme")
            ThemeSelectionCard(
              appTheme,
              onSelect = { scope.launch { settingsDataStore.setAppTheme(it)}}
            )
          }

          item {
              Spacer(Modifier.height(16.dp))
              SectionLabel("Accent Color")
              AccentSelectionCard(
                  accentColor,
                  onSelect = { scope.launch { settingsDataStore.setAccentColor(it) } }
              )
          }
      }

  }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelectionCard(activeTheme: String, onSelect: (String) -> Unit) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      val themes = listOf(
        "system" to "System Default",
        "dark" to "Dark",
        "light" to "Light"
      )

      themes.forEach { (id, label) ->
        Row(
          modifier = Modifier
            .fillMaxSize()
            .clickable { onSelect(id) }
            .padding(vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          RadioButton(
            selected = activeTheme == id,
            onClick = { onSelect(id) },
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
          )
          Spacer(Modifier.width(8.dp))
          Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
      }
    }
  }
}

@Composable
private fun AccentSelectionCard(activeAccent: String, onSelect: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccentSelectorCircle(OrangePalette, activeAccent == "orange") { onSelect("orange") }
                AccentSelectorCircle(TealPalette, activeAccent == "teal") { onSelect("teal") }
                AccentSelectorCircle(BluePalette, activeAccent == "blue") { onSelect("blue") }
                AccentSelectorCircle(MonoPalette, activeAccent == "mono") { onSelect("mono") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

