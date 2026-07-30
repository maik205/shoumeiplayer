package com.maik205.shoumeiplayer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maik205.shoumeiplayer.di.AppContainer
import com.maik205.shoumeiplayer.di.LocalAppContainer

/**
 * Creates a ViewModel from the application composition root without coupling the caller to a
 * particular navigation graph.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    noinline factory: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(
        factory = viewModelFactory {
            initializer { factory(container) }
        },
    )
}
