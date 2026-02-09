package com.feelbachelor.doompedia

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feelbachelor.doompedia.domain.ThemeMode
import com.feelbachelor.doompedia.ui.AppScreen
import com.feelbachelor.doompedia.ui.MainViewModel
import com.feelbachelor.doompedia.ui.MainViewModelFactory
import com.feelbachelor.doompedia.ui.UiEvent
import com.feelbachelor.doompedia.ui.theme.DoompediaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val app = application as DoompediaApplication
        MainViewModelFactory(app.container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            val context = LocalContext.current

            val darkMode = when (state.value.settings.themeMode) {
                ThemeMode.SYSTEM -> null
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            DoompediaTheme(forceDark = darkMode) {
                AppScreen(
                    state = state.value,
                    snackbarHostState = snackbarHostState,
                    onQueryChange = viewModel::onQueryChange,
                    onRefresh = viewModel::refreshFeed,
                    onOpenCard = viewModel::onOpenCard,
                    onToggleBookmark = viewModel::onToggleBookmark,
                    onLessLike = viewModel::onLessLike,
                    onSetPersonalization = viewModel::setPersonalization,
                    onSetThemeMode = viewModel::setTheme,
                    onSetWifiOnly = viewModel::setWifiOnly,
                    onSetManifestUrl = viewModel::setManifestUrl,
                    onCheckUpdatesNow = viewModel::checkForUpdatesNow,
                    onOpenExternalUrl = viewModel::openExternalUrl,
                )
            }

            LaunchedEffect(Unit) {
                viewModel.events.collect { event ->
                    when (event) {
                        is UiEvent.OpenUrl -> {
                            if (!isNetworkAvailable()) {
                                snackbarHostState.showSnackbar("Full article requires a connection")
                                return@collect
                            }
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                                )
                            }.onFailure {
                                snackbarHostState.showSnackbar("Unable to open article link")
                            }
                        }

                        is UiEvent.Snackbar -> {
                            snackbarHostState.showSnackbar(event.message)
                        }
                    }
                }
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
