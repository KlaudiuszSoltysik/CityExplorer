package com.example.cityexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityexplorer.data.GetCountriesWithCitiesDto
import com.example.cityexplorer.ui.theme.CityExplorerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CityExplorerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CityExplorerApp(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun CityExplorerApp(modifier: Modifier = Modifier) {
    val viewModel: MainViewModel = viewModel()
    val uiState = viewModel.uiState

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is MainUiState.Loading -> CircularProgressIndicator()
            is MainUiState.Success -> {
                CountriesWithCitiesList(countries = uiState.cities)
            }
            is MainUiState.Error -> Text(text = "Błąd: ${uiState.message}")
        }
    }
}

@Composable
fun CountriesWithCitiesList(countries: List<GetCountriesWithCitiesDto>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.padding(16.dp)) {
        countries.forEach { dto ->
            item {
                Text(
                    text = dto.country,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(dto.cities) { city ->
                Text(
                    text = " - $city",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CountriesWithCitiesListPreview() {
    CityExplorerTheme {
        CountriesWithCitiesList(
            countries = listOf(
                GetCountriesWithCitiesDto(
                    country = "Poland",
                    cities = listOf("Warszawa", "Kraków", "Gdańsk")
                ),
                GetCountriesWithCitiesDto(
                    country = "Germany",
                    cities = listOf("Berlin", "Hamburg", "Munich")
                )
            )
        )
    }
}
