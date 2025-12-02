package com.example.cityexplorer.ui.cityselector

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesDto
import com.example.cityexplorer.data.util.CacheService

@Composable
fun CitySelectorScreen(
    modifier: Modifier = Modifier,
    cacheService: CacheService,
    onNavigateToModeSelectorScreen: (city: String) -> Unit,
    viewModel: CitySelectorViewModel = viewModel(factory = CitySelectorViewModelFactory(cacheService)),
) {
    val uiState = viewModel.uiState
    val isRefreshing = viewModel.isRefreshing

    fun handleCityClick(city: String) {
        onNavigateToModeSelectorScreen(city)
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshData() },
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is MainUiState.Loading -> CircularProgressIndicator()
            is MainUiState.Success -> {
                CountriesWithCitiesList(
                    countries = uiState.countriesWithCities,
                    onCityClick = { city ->
                        handleCityClick(city)
                    }
                )
            }

            is MainUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = uiState.message,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CountriesWithCitiesList(
    countries: List<GetCountriesWithCitiesDto>,
    onCityClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(countries) { dto ->
            CountryItem(
                dto = dto,
                onCityClick = onCityClick
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun CountryItem(
    dto: GetCountriesWithCitiesDto,
    onCityClick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    expanded = !expanded
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dto.country,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }

        if (expanded) {
            Column {
                dto.cities.forEach { city ->
                    Text(
                        text = city,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCityClick(city)
                            }
                            .padding(start = 24.dp, top = 8.dp, bottom = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
