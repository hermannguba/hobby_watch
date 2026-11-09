package tv.hobby.player.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.hobby.player.data.Account
import tv.hobby.player.data.AntonovApi
import tv.hobby.player.data.AntonovSummary
import tv.hobby.player.data.CardModel
import tv.hobby.player.data.CategoryChip
import tv.hobby.player.data.DetailUi
import tv.hobby.player.data.PlayerRequest
import tv.hobby.player.data.Rail
import tv.hobby.player.data.SourceId
import tv.hobby.player.data.Studio36Api
import tv.hobby.player.data.StudioMatch
import tv.hobby.player.data.canOpen
import tv.hobby.player.data.toCard
import tv.hobby.player.data.toDetail
import tv.hobby.player.data.userMessage

sealed interface Screen {
    data object Home : Screen
    data class Browse(val source: SourceId) : Screen
    data class Detail(val source: SourceId, val id: String) : Screen
    data class Watch(val request: PlayerRequest) : Screen
    data object Login : Screen
}

class AppViewModel : ViewModel() {
    private val studioApi = Studio36Api()
    private val antonovApi = AntonovApi()
    private val backStack = kotlin.collections.ArrayDeque<Screen>()
    private val studioCache = linkedMapOf<String, StudioMatch>()
    private val antonovCache = linkedMapOf<String, AntonovSummary>()

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var studioRows by mutableStateOf<List<Rail>>(emptyList())
        private set
    var studioError by mutableStateOf<String?>(null)
        private set
    var studioLoading by mutableStateOf(false)
        private set
    var categories by mutableStateOf<List<CategoryChip>>(emptyList())
        private set
    var selectedCategory by mutableStateOf<String?>(null)
        private set
    var antonovCards by mutableStateOf<List<CardModel>>(emptyList())
        private set
    var antonovHasMore by mutableStateOf(false)
        private set
    var antonovError by mutableStateOf<String?>(null)
        private set
    var antonovLoading by mutableStateOf(false)
        private set
    var account by mutableStateOf<Account?>(null)
        private set
    var detail by mutableStateOf<DetailUi?>(null)
        private set
    var detailError by mutableStateOf<String?>(null)
        private set
    var opening by mutableStateOf(false)
        private set

    private var studioGen by mutableIntStateOf(0)
    private var antonovGen = 0
    private var antonovOffset = 0

    init {
        refreshStudio()
        refreshAntonov(reset = true, loadCategories = true)
        refreshAccount()
    }

    fun openBrowse(source: SourceId) = open(Screen.Browse(source))

    fun openStudio(id: String) {
        val match = studioCache[id] ?: return
        detail = match.toDetail()
        detailError = null
        open(Screen.Detail(SourceId.Studio, id))
    }

    fun openAntonov(id: String) {
        val summary = antonovCache[id]
        detail = summary?.let { placeholder(it) }
        detailError = null
        open(Screen.Detail(SourceId.Antonov, id))
        reloadAntonov(id)
    }

    fun playPrimary() {
        val current = detail ?: return
        if (current.source == SourceId.Antonov) {
            playFreshAntonov(current.id)
            return
        }
        openRequest(current.primary)
    }

    fun playAlt() {
        val alt = detail?.alt ?: return
        openRequest(alt)
    }

    fun openLogin() = open(Screen.Login)

    fun back() {
        val leaving = screen
        screen = if (backStack.isEmpty()) Screen.Home else backStack.removeLast()
        if (leaving == Screen.Login) {
            viewModelScope.launch {
                refreshAccountNow()
                val current = screen
                if (current is Screen.Detail && current.source == SourceId.Antonov) {
                    reloadAntonov(current.id)
                }
            }
        }
    }

    fun refreshStudio() {
        val generation = ++studioGen
        studioLoading = true
        studioError = null
        viewModelScope.launch {
            try {
                val matches = withContext(Dispatchers.IO) { studioApi.load() }
                if (generation != studioGen) return@launch
                studioCache.clear()
                matches.forEach { studioCache[it.id] = it }
                studioRows = tv.hobby.player.data.studioRails(matches)
            } catch (error: Exception) {
                if (generation == studioGen) studioError = userMessage(error)
            } finally {
                if (generation == studioGen) studioLoading = false
            }
        }
    }

    fun selectCategory(id: String?) {
        if (selectedCategory == id && antonovCards.isNotEmpty() && antonovError == null) return
        selectedCategory = id
        refreshAntonov(reset = true, loadCategories = false)
    }

    fun retryAntonov() {
        refreshAntonov(reset = true, loadCategories = categories.isEmpty())
    }

    fun loadMoreAntonov() {
        if (antonovLoading || !antonovHasMore) return
        val generation = antonovGen
        val offset = antonovOffset
        antonovLoading = true
        viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    antonovApi.page(offset, PAGE, selectedCategory)
                }
                if (generation != antonovGen) return@launch
                val known = antonovCards.map { it.id }.toSet()
                val fresh = page.posts.filter { it.id !in known }
                fresh.forEach { antonovCache[it.id] = it }
                antonovCards = antonovCards + fresh.map { it.toCard() }
                antonovOffset = offset + page.posts.size
                antonovHasMore = page.hasMore && fresh.isNotEmpty()
            } catch (error: Exception) {
                if (generation == antonovGen) antonovError = userMessage(error)
            } finally {
                if (generation == antonovGen) antonovLoading = false
            }
        }
    }

    fun refreshAccount() {
        viewModelScope.launch { refreshAccountNow() }
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { antonovApi.logout() }
            account = null
            val current = screen
            if (current is Screen.Detail && current.source == SourceId.Antonov) {
                reloadAntonov(current.id)
            }
        }
    }

    private fun refreshAntonov(reset: Boolean, loadCategories: Boolean) {
        val generation = ++antonovGen
        antonovLoading = true
        antonovError = null
        if (reset) {
            antonovOffset = 0
            antonovCards = emptyList()
            antonovHasMore = false
        }
        viewModelScope.launch {
            try {
                if (loadCategories) {
                    val loaded = withContext(Dispatchers.IO) { antonovApi.categories() }
                    if (generation != antonovGen) return@launch
                    categories = loaded
                }
                val page = withContext(Dispatchers.IO) {
                    antonovApi.page(0, PAGE, selectedCategory)
                }
                if (generation != antonovGen) return@launch
                page.posts.forEach { antonovCache[it.id] = it }
                antonovCards = page.posts.map { it.toCard() }
                antonovOffset = page.posts.size
                antonovHasMore = page.hasMore
            } catch (error: Exception) {
                if (generation == antonovGen) antonovError = userMessage(error)
            } finally {
                if (generation == antonovGen) antonovLoading = false
            }
        }
    }

    private fun reloadAntonov(id: String) {
        viewModelScope.launch {
            try {
                val post = withContext(Dispatchers.IO) { antonovApi.post(id) }
                if ((screen as? Screen.Detail)?.id == id) {
                    detail = post.toDetail(account != null)
                    detailError = null
                }
            } catch (error: Exception) {
                if ((screen as? Screen.Detail)?.id == id) detailError = userMessage(error)
            }
        }
    }

    private fun playFreshAntonov(id: String) {
        opening = true
        detailError = null
        viewModelScope.launch {
            try {
                val post = withContext(Dispatchers.IO) { antonovApi.post(id) }
                val fresh = post.toDetail(account != null)
                detail = fresh
                if (fresh.primary.canOpen()) {
                    open(Screen.Watch(fresh.primary))
                } else {
                    detailError = fresh.note
                }
            } catch (error: Exception) {
                detailError = userMessage(error)
            } finally {
                opening = false
            }
        }
    }

    private suspend fun refreshAccountNow() {
        try {
            account = withContext(Dispatchers.IO) { antonovApi.account() }
        } catch (_: Exception) {
        }
    }

    private fun openRequest(request: PlayerRequest) {
        if (!request.canOpen()) {
            detailError = "Трансляция ещё не опубликована."
            return
        }
        open(Screen.Watch(request))
    }

    private fun open(next: Screen) {
        backStack.addLast(screen)
        screen = next
    }

    private fun placeholder(summary: AntonovSummary) = DetailUi(
        source = SourceId.Antonov,
        id = summary.id,
        title = summary.title,
        eyebrow = summary.categoryName.orEmpty(),
        imageUrl = summary.imageUrl,
        description = summary.description,
        note = "Открываем…",
        primary = PlayerRequest(title = summary.title),
        alt = null,
        showLogin = account == null,
    )

    private companion object {
        const val PAGE = 24
    }
}
