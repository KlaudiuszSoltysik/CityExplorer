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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cityexplorer.data.dtos.GetCountriesWithCitiesResponseDto

@Composable
fun CitySelectorScreen(
    onNavigateToMapScreen: (city: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CitySelectorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    CitySelectorContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshData() },
        onCityClick = onNavigateToMapScreen,
        modifier = modifier
    )
}

@Composable
fun CitySelectorContent(
    uiState: CitySelectorUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onCityClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is CitySelectorUiState.Loading -> {
                CircularProgressIndicator()
            }

            is CitySelectorUiState.Success -> {
                CountriesList(
                    countries = uiState.data,
                    onCityClick = onCityClick,
                    modifier = Modifier.fillMaxSize()
                )
            }

            is CitySelectorUiState.Error -> {
                ErrorMessage(
                    message = uiState.message,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun CountriesList(
    countries: List<GetCountriesWithCitiesResponseDto>,
    onCityClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp)
    ) {
        items(countries) { country ->
            CountryItem(
                country = country,
                onCityClick = onCityClick,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun CountryItem(
    country: GetCountriesWithCitiesResponseDto,
    onCityClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = country.country,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        }

        if (expanded) {
            Column {
                country.cities.forEach { city ->
                    Text(
                        text = city,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = {
                                onCityClick(city)
                            })
                            .padding(start = 24.dp, top = 8.dp, bottom = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ErrorMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp)
        )
    }
}
