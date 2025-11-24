package com.example.cityexplorer.ui.cityselector

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.ui.theme.CityExplorerTheme

@Composable
fun CitySelectorScreen(
    viewModel: CitySelectorViewModel = viewModel()
) {
    val uiState = viewModel.uiState

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is MainUiState.Loading -> CircularProgressIndicator()
            is MainUiState.Success -> {
                CountriesWithCitiesList(countries = uiState.countriesWithCities)
            }
            is MainUiState.Error -> {
                Text(text = "Error: ${uiState.message}")
            }
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