package tv.hobby.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hobby.player.data.SourceId

@Composable
fun HobbyTvApp(vm: AppViewModel = viewModel()) {
    BackHandler(enabled = vm.screen !is Screen.Home) { vm.back() }
    Box(modifier = Modifier.fillMaxSize().background(Ink)) {
        when (val screen = vm.screen) {
            Screen.Home -> HomeScreen(
                accountName = vm.account?.displayName,
                onOpen = vm::openBrowse,
            )
            is Screen.Browse -> BrowseScreen(
                source = screen.source,
                studioRows = vm.studioRows,
                studioLoading = vm.studioLoading,
                studioError = vm.studioError,
                onRefreshStudio = vm::refreshStudio,
                categories = vm.categories,
                selectedCategory = vm.selectedCategory,
                onSelectCategory = vm::selectCategory,
                antonovCards = vm.antonovCards,
                antonovLoading = vm.antonovLoading,
                antonovError = vm.antonovError,
                antonovHasMore = vm.antonovHasMore,
                onLoadMore = vm::loadMoreAntonov,
                onRetryAntonov = vm::retryAntonov,
                accountName = vm.account?.displayName,
                onLogin = vm::openLogin,
                onLogout = vm::logout,
                onOpen = if (screen.source == SourceId.Studio) vm::openStudio else vm::openAntonov,
                onBack = vm::back,
            )
            is Screen.Detail -> DetailScreen(
                detail = vm.detail,
                error = vm.detailError,
                opening = vm.opening,
                onPlay = vm::playPrimary,
                onAlt = vm::playAlt,
                onLogin = vm::openLogin,
                onBack = vm::back,
            )
            is Screen.Watch -> PlayerScreen(request = screen.request, onBack = vm::back)
            Screen.Login -> LoginScreen(
                accountName = vm.account?.displayName,
                onFinished = vm::back,
                onPageSettled = vm::refreshAccount,
            )
        }
    }
}
