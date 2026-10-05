package com.xtreamlytv.androidtv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xtreamlytv.androidtv.data.CredentialsStore
import com.xtreamlytv.androidtv.data.BackupManager
import com.xtreamlytv.androidtv.data.VideoDownloader
import com.xtreamlytv.androidtv.data.OfflineFile
import com.xtreamlytv.androidtv.data.StreamUrlBuilder
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import com.xtreamlytv.androidtv.data.ExportResult
import com.xtreamlytv.androidtv.data.ImportResult
import com.xtreamlytv.androidtv.data.LocalStateStore
import com.xtreamlytv.androidtv.data.ProviderUrl
import com.xtreamlytv.androidtv.data.XtreamClient
import com.xtreamlytv.androidtv.data.M3uParser
import com.xtreamlytv.androidtv.data.M3uCredentialExtractor
import com.xtreamlytv.androidtv.data.M3uChannel
import com.xtreamlytv.androidtv.data.itemKey
import com.xtreamlytv.androidtv.model.AppSettings
import com.xtreamlytv.androidtv.model.CatalogItem
import com.xtreamlytv.androidtv.model.Category
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.model.Credentials
import com.xtreamlytv.androidtv.model.FavoriteGroup
import com.xtreamlytv.androidtv.model.FavoriteGroupAppearance
import com.xtreamlytv.androidtv.model.PlaybackProgress
import com.xtreamlytv.androidtv.model.PlayerRequest
import com.xtreamlytv.androidtv.model.ProviderSummary
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class FocusRequest(
    val scope: String,
    val itemKey: String? = null,
    val firstItem: Boolean = false,
)

sealed interface AppScreen {
    data object Login : AppScreen
    data object Home : AppScreen
    data class Catalog(val type: ContentType) : AppScreen
    data class Detail(
        val item: CatalogItem,
        val origin: AppScreen,
        val returnFocus: FocusRequest? = null,
    ) : AppScreen
    data object Favorites : AppScreen
    data class FavoriteGroupBrowser(val groupId: String) : AppScreen
    data object FavoriteGroupsManager : AppScreen
    data class FavoriteGroupEditor(val groupId: String?) : AppScreen
    data class Player(
        val request: PlayerRequest,
        val origin: AppScreen,
        val returnFocus: FocusRequest? = null,
    ) : AppScreen
    data object Settings : AppScreen
    data object OfflineVideos : AppScreen
    data object M3u : AppScreen
}

data class AppUiState(
    val backupMessage: String? = null,
    val screen: AppScreen = AppScreen.Home,
    val initializing: Boolean = true,
    val loading: Boolean = false,
    val catalogsLoading: Boolean = false,
    val error: String? = null,
    val credentials: Credentials? = null,
    val provider: ProviderSummary? = null,
    val categories: Map<ContentType, List<Category>> = emptyMap(),
    val selectedCategories: Map<ContentType, Category> = emptyMap(),
    val items: List<CatalogItem> = emptyList(),
    val loadedItems: Map<ContentType, List<CatalogItem>> = emptyMap(),
    val searchQuery: String = "",
    val favorites: List<CatalogItem> = emptyList(),
    val favoriteGroups: List<FavoriteGroup> = emptyList(),
    val favoriteGroupOrder: List<String> = listOf("all", "live", "movie", "series"),
    val favoriteItemOrders: Map<String, List<String>> = emptyMap(),
    val favoriteGroupAppearances: Map<String, FavoriteGroupAppearance> = emptyMap(),
    val hiddenFavoriteGroupIds: Set<String> = emptySet(),
    val selectedFavoriteGroupId: String = "all",
    val recent: List<CatalogItem> = emptyList(),
    val progress: Map<String, PlaybackProgress> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val detailEpisodes: List<CatalogItem> = emptyList(),
    val detailSelectedSeason: Int? = null,
    val focusRequest: FocusRequest? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val credentialsStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CredentialsStore(application)
    }
    private val localStateStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalStateStore(application)
    }
    private val backupManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BackupManager(application)
    }
    private val downloader by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        VideoDownloader(application)
    }
    private var client: XtreamClient? = null
    private val categoryCache = LinkedHashMap<String, List<CatalogItem>>(16, 0.75f, true)
    private var catalogRequestId = 0L
    private var m3uChannels = mutableMapOf<String, M3uChannel>()
    private var m3uMode = false
    private var m3uCatalog = mutableMapOf<String, List<CatalogItem>>()
    private val lastFocusByArea = mutableMapOf<String, FocusRequest>()
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val (localResult, credentialsResult) = coroutineScope {
                val localDeferred = async(Dispatchers.IO) { runCatching { localStateStore.load() } }
                val credentialsDeferred = async(Dispatchers.IO) { runCatching { credentialsStore.loadActive() } }
                localDeferred.await() to credentialsDeferred.await()
            }
            val local = localResult.getOrNull()
            _state.update { current ->
                current.copy(
                    settings = local?.settings ?: AppSettings(),
                    favorites = local?.favorites.orEmpty(),
                    favoriteGroups = local?.favoriteGroups.orEmpty(),
                    favoriteGroupOrder = local?.favoriteGroupOrder ?: listOf("all", "live", "movie", "series"),
                    favoriteItemOrders = local?.favoriteItemOrders.orEmpty(),
                    favoriteGroupAppearances = local?.favoriteGroupAppearances.orEmpty(),
                    hiddenFavoriteGroupIds = local?.hiddenFavoriteGroupIds.orEmpty(),
                    selectedFavoriteGroupId = firstVisibleFavoriteGroupId(
                        order = local?.favoriteGroupOrder ?: BuiltInFavoriteGroupIds,
                        hidden = local?.hiddenFavoriteGroupIds.orEmpty(),
                    ),
                    recent = local?.recent.orEmpty(),
                    progress = local?.progress.orEmpty(),
                )
            }
            val savedCredentials = credentialsResult.getOrNull()
            if (savedCredentials == null) {
                _state.update {
                    it.copy(
                        screen = AppScreen.Home,
                        initializing = false,
                        error = credentialsResult.exceptionOrNull()?.let {
                            "Saved provider details could not be read. Enter them again."
                        },
                    )
                }
            } else {
                startConnection(
                    credentials = savedCredentials,
                    persist = false,
                    startup = true,
                    successScreen = AppScreen.Home,
                    failureScreen = AppScreen.Home,
                )
            }
        }
    }

    fun connect(credentials: Credentials) {
        startConnection(
            credentials = credentials,
            persist = true,
            startup = false,
            successScreen = AppScreen.Home,
            failureScreen = AppScreen.Home,
        )
    }

    fun updateProvider(credentials: Credentials) {
        startConnection(
            credentials = credentials,
            persist = true,
            startup = false,
            successScreen = AppScreen.Settings,
            failureScreen = AppScreen.Settings,
        )
    }

    private fun startConnection(
        credentials: Credentials,
        persist: Boolean,
        startup: Boolean,
        successScreen: AppScreen,
        failureScreen: AppScreen,
    ) {
        val normalizedCredentials = runCatching {
            if (credentials.kind == "m3u") {
                credentials.copy(server = credentials.server.trim()).also {
                    require(it.server.isNotBlank()) { "Enter the M3U URL." }
                }
            } else {
                credentials.copy(
                    server = ProviderUrl.normalize(credentials.server),
                    username = credentials.username.trim(),
                ).also {
                    require(it.username.isNotBlank()) { "Enter your provider username." }
                    require(it.password.isNotBlank()) { "Enter your provider password." }
                }
            }
        }.getOrElse { error ->
            _state.update {
                it.copy(
                    initializing = false,
                    loading = false,
                    screen = failureScreen,
                    credentials = credentials,
                    error = error.userMessage(),
                )
            }
            return
        }

        _state.update {
            it.copy(
                initializing = startup,
                loading = true,
                catalogsLoading = false,
                error = null,
                credentials = normalizedCredentials,
            )
        }

        if (normalizedCredentials.kind == "m3u") {
            viewModelScope.launch {
                try {
                    val channels = withTimeout(180_000L) {
                        val f = ensureM3uLocal(normalizedCredentials)
                        M3uParser.parseFromFile(f, limit = 30_000)
                    }
                    client = null
                    m3uMode = true
                    m3uChannels.clear()
                    channels.forEach { m3uChannels[it.id] = it }
                    val itemsByGroup = mutableMapOf<String, MutableList<CatalogItem>>()
                    val items = ArrayList<CatalogItem>(channels.size)
                    channels.forEach { ch ->
                        val item = CatalogItem(id = ch.id, type = ContentType.LIVE, name = ch.name, categoryId = ch.group, imageUrl = ch.logo)
                        items.add(item)
                        itemsByGroup.getOrPut(ch.group) { mutableListOf() }.add(item)
                    }
                    m3uCatalog.clear()
                    m3uCatalog["all"] = items
                    m3uCatalog.putAll(itemsByGroup)
                    val cats = listOf(Category(id = "all", name = "ALL")) + itemsByGroup.keys.map { Category(id = it, name = it) }
                    if (persist) {
                        withContext(Dispatchers.IO) {
                            val list = credentialsStore.loadAll().toMutableList()
                            val idx = list.indexOfFirst { it.server == normalizedCredentials.server && it.username == normalizedCredentials.username }
                            if (idx >= 0) { list[idx] = normalizedCredentials.copy(id = list[idx].id, name = list[idx].name) } else { list.add(normalizedCredentials) }
                            credentialsStore.saveAll(list)
                            credentialsStore.setActive(normalizedCredentials.id)
                        }
                    }
                    _state.update {
                        it.copy(
                            screen = successScreen,
                            initializing = false,
                            loading = false,
                            catalogsLoading = false,
                            error = null,
                            provider = ProviderSummary(username = "M3U", status = "Active", expiration = null),
                            categories = mapOf(ContentType.LIVE to cats),
                            selectedCategories = emptyMap(),
                            items = emptyList(),
                            loadedItems = emptyMap(),
                            searchQuery = "",
                        )
                    }
                    (cats.firstOrNull { it.id != "all" } ?: cats.first()).let { selectCategory(ContentType.LIVE, it) }
                } catch (error: CancellationException) {
                    if (error !is TimeoutCancellationException) throw error
                    connectionFailed(error, failureScreen, startup)
                } catch (error: Throwable) {
                    connectionFailed(error, failureScreen, startup)
                }
            }
            return
        }

        viewModelScope.launch {
            try {
                m3uMode = false
                m3uCatalog.clear()
                val candidate = XtreamClient(normalizedCredentials)
                val profile = withTimeout(CONNECTION_TIMEOUT_MS) { candidate.authenticate() }

                client = candidate
                categoryCache.clear()
                if (persist) {
                    withContext(Dispatchers.IO) {
                        val list = credentialsStore.loadAll().toMutableList()
                        val idx = list.indexOfFirst { it.server == normalizedCredentials.server && it.username == normalizedCredentials.username }
                        if (idx >= 0) { list[idx] = normalizedCredentials.copy(id = list[idx].id, name = list[idx].name) } else { list.add(normalizedCredentials) }
                        credentialsStore.saveAll(list)
                        credentialsStore.setActive(normalizedCredentials.id)
                    }
                }
                _state.update {
                    it.copy(
                        screen = successScreen,
                        initializing = false,
                        loading = false,
                        catalogsLoading = true,
                        error = null,
                        provider = profile,
                        categories = emptyMap(),
                        selectedCategories = emptyMap(),
                        items = emptyList(),
                        loadedItems = emptyMap(),
                        searchQuery = "",
                    )
                }
                loadCategories(candidate)
            } catch (error: CancellationException) {
                if (error !is TimeoutCancellationException) throw error
                connectionFailed(error, failureScreen, startup)
            } catch (error: Throwable) {
                connectionFailed(error, failureScreen, startup)
            }
        }
    }

    private fun loadCategories(candidate: XtreamClient) {
        viewModelScope.launch {
            val categories = coroutineScope {
                ContentType.entries
                    .filter { it != ContentType.EPISODE }
                    .associateWith { type ->
                        async {
                            runCatching { candidate.categories(type) }
                                .getOrDefault(emptyList())
                        }
                    }
                    .mapValues { (_, deferred) -> deferred.await() }
            }
            if (client === candidate) {
                _state.update { it.copy(categories = categories.mapValues { (_, list) ->
                listOf(Category(id = "all", name = "ALL")) + list
            }, catalogsLoading = false) }
                val currentScreen = _state.value.screen as? AppScreen.Catalog
                if (currentScreen != null && _state.value.selectedCategories[currentScreen.type] == null) {
                    (categories[currentScreen.type]?.firstOrNull { it.id != "all" } ?: categories[currentScreen.type]?.firstOrNull())?.let { firstCategory ->
                        selectCategory(currentScreen.type, firstCategory)
                    }
                }
            }
        }
    }

    private fun connectionFailed(error: Throwable, failureScreen: AppScreen, startup: Boolean) {
        if (startup || failureScreen == AppScreen.Home) client = null
        _state.update {
            it.copy(
                screen = failureScreen,
                initializing = false,
                loading = false,
                catalogsLoading = false,
                error = error.userMessage(),
            )
        }
    }

    fun rememberFocusedItem(area: String, scope: String, key: String) {
        lastFocusByArea[area] = FocusRequest(scope = scope, itemKey = key)
    }

    fun consumeFocusRequest(scope: String) {
        _state.update { current ->
            if (current.focusRequest?.scope == scope) current.copy(focusRequest = null) else current
        }
    }

    fun openHome() = _state.update {
        it.copy(
            screen = AppScreen.Home,
            items = emptyList(),
            searchQuery = "",
            error = null,
            focusRequest = lastFocusByArea[AREA_HOME],
        )
    }

    fun openFavorites() = _state.update { current ->
        val selected = current.selectedFavoriteGroupId.takeIf { id ->
            id !in current.hiddenFavoriteGroupIds && id in validFavoriteGroupIds(current.favoriteGroups)
        } ?: firstVisibleFavoriteGroupId(current.favoriteGroupOrder, current.hiddenFavoriteGroupIds)
        val scope = selected.takeIf { it.isNotBlank() }?.let(::favoriteFocusScope)
        val remembered = lastFocusByArea[AREA_FAVORITES]?.takeIf { it.scope == scope }
        current.copy(
            screen = if (selected == "all" || selected.isBlank()) AppScreen.Favorites else AppScreen.FavoriteGroupBrowser(selected),
            selectedFavoriteGroupId = selected,
            items = emptyList(),
            searchQuery = "",
            error = null,
            focusRequest = remembered ?: scope?.let { FocusRequest(it, firstItem = true) },
        )
    }

    fun openM3u() = _state.update {
        it.copy(screen = AppScreen.M3u, items = emptyList(), searchQuery = "", error = null, focusRequest = null)
    }

    fun openSettings() = _state.update {
        it.copy(screen = AppScreen.Settings, items = emptyList(), searchQuery = "", error = null, focusRequest = null)
    }

    fun openCatalog(type: ContentType) {
        val categories = _state.value.categories[type].orEmpty()
        val selected = _state.value.selectedCategories[type] ?: categories.firstOrNull { it.id != "all" } ?: categories.firstOrNull()
        val remembered = lastFocusByArea[catalogArea(type)]
        _state.update {
            it.copy(
                screen = AppScreen.Catalog(type),
                items = emptyList(),
                searchQuery = "",
                error = null,
                focusRequest = remembered ?: selected?.let { category ->
                    FocusRequest(catalogFocusScope(type, category.id), firstItem = true)
                },
            )
        }
        selected?.let { selectCategory(type, it, preserveRememberedFocus = remembered != null) }
    }

    fun selectCategory(
        type: ContentType,
        category: Category,
        preserveRememberedFocus: Boolean = false,
    ) {
        if (m3uMode) {
            val list = m3uCatalog[category.id].orEmpty()
            _state.update {
                it.copy(
                    loading = false,
                    selectedCategories = it.selectedCategories + (type to category),
                    items = list,
                    searchQuery = "",
                    error = null,
                    focusRequest = if (preserveRememberedFocus) it.focusRequest
                    else FocusRequest(catalogFocusScope(type, category.id), firstItem = true),
                )
            }
            updateLoadedItems(type, list)
            return
        }
        val api = client ?: return
        val key = cacheKey(type, category.id)
        val cached = categoryCache[key]
        val requestId = ++catalogRequestId
        _state.update {
            it.copy(
                loading = cached == null,
                selectedCategories = it.selectedCategories + (type to category),
                items = cached.orEmpty(),
                searchQuery = "",
                error = null,
                focusRequest = if (preserveRememberedFocus) it.focusRequest
                else FocusRequest(catalogFocusScope(type, category.id), firstItem = true),
            )
        }
        if (cached != null) {
            updateLoadedItems(type, cached)
            return
        }
        viewModelScope.launch {
            runCatching { api.items(type, category.id) }
                .onSuccess { items ->
                    cacheCategory(type, category.id, items)
                    if (requestId == catalogRequestId && _state.value.screen == AppScreen.Catalog(type)) {
                        _state.update { it.copy(loading = false, items = items) }
                    }
                    updateLoadedItems(type, items)
                }
                .onFailure { error ->
                    if (requestId == catalogRequestId) {
                        _state.update { it.copy(loading = false, error = error.userMessage()) }
                    }
                }
        }
    }

    fun setSearchQuery(query: String) = _state.update { it.copy(searchQuery = query) }

    fun activate(item: CatalogItem) {
        when (item.type) {
            ContentType.EPISODE -> play(item)
            ContentType.LIVE, ContentType.MOVIE, ContentType.SERIES -> openDetail(item)
        }
    }

    fun openDetail(item: CatalogItem) {
        val origin = _state.value.screen
        val returnFocus = focusForScreen(origin)
        _state.update {
            it.copy(
                screen = AppScreen.Detail(item, origin, returnFocus),
                detailEpisodes = emptyList(),
                detailSelectedSeason = null,
                loading = item.type == ContentType.SERIES,
                error = null,
                focusRequest = null,
            )
        }
        if (item.type == ContentType.SERIES) loadSeriesEpisodes(item)
    }

    private fun loadSeriesEpisodes(series: CatalogItem) {
        val api = client ?: return
        viewModelScope.launch {
            runCatching { api.seriesEpisodes(series) }
                .onSuccess { episodes ->
                    val current = _state.value.screen as? AppScreen.Detail
                    if (current?.item?.id == series.id) {
                        val firstSeason = episodes.mapNotNull { it.season }.distinct().sorted().firstOrNull()
                        _state.update {
                            it.copy(
                                loading = false,
                                detailEpisodes = episodes,
                                detailSelectedSeason = firstSeason,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.userMessage()) }
                }
        }
    }

    fun selectDetailSeason(seriesId: String, season: Int) {
        _state.update {
            it.copy(
                detailSelectedSeason = season,
                focusRequest = FocusRequest(detailFocusScope(seriesId, season), firstItem = true),
            )
        }
    }

    fun play(item: CatalogItem, queue: List<CatalogItem> = currentQueueFor(item)) {
        // Check if this is an M3U channel
        val m3u = m3uChannels[item.id]
        if (m3u != null) {
            val origin = _state.value.screen
            val returnFocus = focusForScreen(origin)
            val request = PlayerRequest(
                item = item,
                queue = listOf(item),
                urlCandidates = listOf(m3u.url),
                startPositionMs = 0L,
            )
            addRecent(item)
            _state.update { it.copy(screen = AppScreen.Player(request, origin, returnFocus), error = null, focusRequest = null) }
            return
        }
        
        val api = client ?: return
        val playableQueue = queue.filter { it.type == item.type && it.type != ContentType.SERIES }
        val origin = _state.value.screen
        val returnFocus = focusForScreen(origin)
        val progress = _state.value.progress[itemKey(item)]
        val request = PlayerRequest(
            item = item,
            queue = playableQueue.ifEmpty { listOf(item) },
            urlCandidates = urlCandidatesFor(item),
            startPositionMs = progress?.positionMs ?: 0L,
        )
        addRecent(item)
        _state.update { it.copy(screen = AppScreen.Player(request, origin, returnFocus), error = null, focusRequest = null) }
    }

    private suspend fun ensureM3uLocal(cred: Credentials): java.io.File = withContext(Dispatchers.IO) {
        val f = java.io.File(getApplication<android.app.Application>().cacheDir, "m3u_" + cred.id + ".m3u")
        if (f.exists() && f.length() > 0) return@withContext f
        val conn = M3uParser.openConnection(cred.server)
        conn.inputStream.use { input -> f.outputStream().use { out -> input.copyTo(out, 65536) } }
        f
    }

    private fun urlCandidatesFor(item: CatalogItem): List<String> {
        m3uChannels[item.id]?.let { return listOf(it.url) }
        val api = client ?: return emptyList()
        return urlCandidatesFor(item)
    }

    fun playAdjacent(delta: Int) {
        val screen = _state.value.screen as? AppScreen.Player ?: return
        val queue = screen.request.queue
        val currentIndex = queue.indexOfFirst { itemKey(it) == itemKey(screen.request.item) }
        if (currentIndex < 0 || queue.size < 2) return
        val next = queue[(currentIndex + delta + queue.size) % queue.size]
        val progress = _state.value.progress[itemKey(next)]
        addRecent(next)
        _state.update {
            it.copy(
                screen = AppScreen.Player(
                    request = PlayerRequest(
                        item = next,
                        queue = queue,
                        urlCandidates = urlCandidatesFor(next),
                        startPositionMs = progress?.positionMs ?: 0L,
                    ),
                    origin = screen.origin,
                    returnFocus = screen.returnFocus,
                ),
            )
        }
    }

    fun toggleFavorite(item: CatalogItem) {
        val key = itemKey(item)
        val current = _state.value
        val exists = current.favorites.any { itemKey(it) == key }
        val nextFavorites = if (exists) {
            current.favorites.filterNot { itemKey(it) == key }
        } else {
            (listOf(item) + current.favorites.filterNot { itemKey(it) == key }).take(250)
        }
        val nextGroups = if (exists) {
            current.favoriteGroups.map { group ->
                if (key in group.itemKeys) group.copy(itemKeys = group.itemKeys - key, updatedAt = System.currentTimeMillis())
                else group
            }
        } else current.favoriteGroups
        val nextOrders = if (exists) {
            current.favoriteItemOrders.mapValues { (_, order) -> order.filterNot { it == key } }
        } else current.favoriteItemOrders
        _state.update {
            it.copy(
                favorites = nextFavorites,
                favoriteGroups = nextGroups,
                favoriteItemOrders = nextOrders,
            )
        }
        persistFavorites(nextFavorites, nextGroups, nextOrders)
    }

    fun isFavorite(item: CatalogItem): Boolean =
        _state.value.favorites.any { itemKey(it) == itemKey(item) }

    fun selectFavoriteGroup(groupId: String) {
        val current = _state.value
        if (groupId in current.hiddenFavoriteGroupIds || groupId !in validFavoriteGroupIds(current.favoriteGroups)) return
        _state.update {
            it.copy(
                screen = if (groupId == "all") AppScreen.Favorites else AppScreen.FavoriteGroupBrowser(groupId),
                selectedFavoriteGroupId = groupId,
                searchQuery = "",
                error = null,
                focusRequest = FocusRequest(favoriteFocusScope(groupId), firstItem = true),
            )
        }
    }

    fun openFavoriteGroup(groupId: String) = selectFavoriteGroup(groupId)

    fun openFavoriteGroupsManager() = _state.update {
        it.copy(screen = AppScreen.FavoriteGroupsManager, error = null, focusRequest = null)
    }

    fun openFavoriteEditor(groupId: String? = null) = _state.update {
        it.copy(screen = AppScreen.FavoriteGroupEditor(groupId), error = null, focusRequest = null)
    }

    fun saveBuiltInFavoriteGroupAppearance(
        groupId: String,
        name: String,
        icon: String,
        color: String,
    ) {
        if (groupId !in BuiltInFavoriteGroupIds) return
        val fallback = defaultFavoriteGroupAppearance(groupId)
        val appearance = FavoriteGroupAppearance(
            name = name.trim().take(36).ifBlank { fallback.name },
            icon = icon,
            color = color,
        )
        val appearances = _state.value.favoriteGroupAppearances + (groupId to appearance)
        _state.update {
            it.copy(
                screen = AppScreen.FavoriteGroupsManager,
                favoriteGroupAppearances = appearances,
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) { localStateStore.saveFavoriteGroupAppearances(appearances) }
    }

    fun toggleFavoriteGroupHidden(groupId: String) {
        val current = _state.value
        if (groupId !in validFavoriteGroupIds(current.favoriteGroups)) return
        val hidden = current.hiddenFavoriteGroupIds.toMutableSet().apply {
            if (!add(groupId)) remove(groupId)
        }.toSet()
        val selected = current.selectedFavoriteGroupId.takeIf { it !in hidden }
            ?: firstVisibleFavoriteGroupId(current.favoriteGroupOrder, hidden)
        _state.update {
            it.copy(
                hiddenFavoriteGroupIds = hidden,
                selectedFavoriteGroupId = selected,
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) { localStateStore.saveHiddenFavoriteGroupIds(hidden) }
    }

    fun saveFavoriteGroup(
        groupId: String?,
        name: String,
        icon: String,
        color: String,
        itemKeys: Set<String>,
    ) {
        if (groupId in BuiltInFavoriteGroupIds) {
            saveBuiltInFavoriteGroupAppearance(groupId!!, name, icon, color)
            return
        }
        val trimmedName = name.trim().take(36)
        if (trimmedName.isBlank()) {
            _state.update { it.copy(error = "Enter a group name.") }
            return
        }
        val now = System.currentTimeMillis()
        val existing = _state.value.favoriteGroups.firstOrNull { it.id == groupId }
        val saved = FavoriteGroup(
            id = existing?.id ?: "group-${now.toString(36)}",
            name = trimmedName,
            icon = icon,
            color = color,
            itemKeys = itemKeys.intersect(_state.value.favorites.map(::itemKey).toSet()).take(250).toSet(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        val groups = if (existing == null) {
            (_state.value.favoriteGroups + saved).take(24)
        } else {
            _state.value.favoriteGroups.map { if (it.id == saved.id) saved else it }
        }
        val groupOrder = normalizeGroupOrder(
            _state.value.favoriteGroupOrder + saved.id,
            groups,
        )
        _state.update {
            it.copy(
                screen = AppScreen.FavoriteGroupBrowser(saved.id),
                favoriteGroups = groups,
                favoriteGroupOrder = groupOrder,
                selectedFavoriteGroupId = saved.id,
                error = null,
                focusRequest = FocusRequest(favoriteFocusScope(saved.id), firstItem = true),
            )
        }
        persistGroups(groups, groupOrder, _state.value.favoriteItemOrders)
    }

    fun deleteFavoriteGroup(groupId: String) {
        val groups = _state.value.favoriteGroups.filterNot { it.id == groupId }
        val groupOrder = normalizeGroupOrder(
            _state.value.favoriteGroupOrder.filterNot { it == groupId },
            groups,
        )
        val itemOrders = _state.value.favoriteItemOrders - groupId
        val hidden = _state.value.hiddenFavoriteGroupIds - groupId
        val selected = firstVisibleFavoriteGroupId(groupOrder, hidden)
        _state.update {
            it.copy(
                screen = if (selected == "all" || selected.isBlank()) AppScreen.Favorites else AppScreen.FavoriteGroupBrowser(selected),
                favoriteGroups = groups,
                favoriteGroupOrder = groupOrder,
                favoriteItemOrders = itemOrders,
                hiddenFavoriteGroupIds = hidden,
                selectedFavoriteGroupId = selected,
                error = null,
                focusRequest = selected.takeIf { it.isNotBlank() }?.let { FocusRequest(favoriteFocusScope(it), firstItem = true) },
            )
        }
        persistGroups(groups, groupOrder, itemOrders)
        viewModelScope.launch(Dispatchers.IO) { localStateStore.saveHiddenFavoriteGroupIds(hidden) }
    }

    fun saveFavoriteGroupOrder(order: List<String>) {
        val normalized = normalizeGroupOrder(order, _state.value.favoriteGroups)
        _state.update { it.copy(favoriteGroupOrder = normalized, error = null) }
        viewModelScope.launch(Dispatchers.IO) { localStateStore.saveFavoriteGroupOrder(normalized) }
    }

    fun saveFavoriteItemOrder(groupId: String, order: List<String>) {
        if (groupId == "all") return
        val validFavoriteKeys = _state.value.favorites.map(::itemKey).toSet()
        val normalized = order.filter { it in validFavoriteKeys }.distinct().take(250)
        val orders = _state.value.favoriteItemOrders + (groupId to normalized)
        _state.update { it.copy(favoriteItemOrders = orders, error = null) }
        viewModelScope.launch(Dispatchers.IO) { localStateStore.saveFavoriteItemOrders(orders) }
    }

    fun updateSettings(settings: AppSettings) {
        val normalized = settings.copy(maxCachedCategories = settings.maxCachedCategories.coerceIn(2, 5))
        _state.update { it.copy(settings = normalized, error = null) }
        trimCategoryCache(normalized.maxCachedCategories)
        viewModelScope.launch(Dispatchers.IO) { localStateStore.saveSettings(normalized) }
    }

    fun loadM3uFromUrl(url: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val channels = M3uParser.parseFromUrl(url)
                m3uChannels.clear()
                channels.forEach { m3uChannels[it.id] = it }
                val items = channels.map { 
                    CatalogItem(
                        id = it.id,
                        type = ContentType.LIVE,
                        name = it.name,
                    )
                }
                _state.update { 
                    it.copy(
                        items = items,
                        loading = false,
                        screen = AppScreen.Catalog(ContentType.LIVE),
                    ) 
                }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(loading = false, error = "Failed to load M3U: ${e.message}") 
                }
            }
        }
    }

    fun clearCatalogCache() {
        categoryCache.clear()
        _state.update { it.copy(items = emptyList(), loadedItems = emptyMap(), error = null) }
    }

    fun clearHistoryForType(type: ContentType) {
        val typesToClear = if (type == ContentType.SERIES) setOf(ContentType.SERIES, ContentType.EPISODE) else setOf(type)
        val filteredRecent = _state.value.recent.filter { it.type !in typesToClear }
        val filteredProgress = _state.value.progress.filterKeys { key -> typesToClear.none { key.startsWith("${it.name}:") } }
        _state.update { it.copy(recent = filteredRecent, progress = filteredProgress) }
        viewModelScope.launch(Dispatchers.IO) { localStateStore.clearHistoryForType(type) }
    }

    fun exportBackup() {
        viewModelScope.launch {
            val result = backupManager.export()
            val msg = when (result) {
                is ExportResult.Success -> result.message
                is ExportResult.Error -> result.message
                else -> "Erro desconhecido"
            }
            _state.update { it.copy(backupMessage = msg) }
            kotlinx.coroutines.delay(5000)
            _state.update { it.copy(backupMessage = null) }
        }
    }
    
    fun importBackup() {
        viewModelScope.launch {
            val result = backupManager.import()
            val msg = when (result) {
                is ImportResult.Success -> result.message
                is ImportResult.Error -> result.message
                else -> "Erro desconhecido"
            }
            _state.update { it.copy(backupMessage = msg) }
            if (result is ImportResult.Success) {
                // Recarregar providers após importação (useProvider já recarrega tudo)
                val providers = credentialsStore.loadAll()
                if (providers.isNotEmpty()) {
                    val active = credentialsStore.loadActive() ?: providers.first()
                    useProvider(active.id)
                }
            }
            kotlinx.coroutines.delay(5000)
            _state.update { it.copy(backupMessage = null) }
        }
    }
    
    fun downloadVideo(item: CatalogItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val cred = credentialsStore.loadActive() ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Sem conta ativa", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val urls = StreamUrlBuilder.candidates(cred, item, state.value.settings.streamFormat)
            val url = urls.firstOrNull() ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "URL nao disponivel para este item", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val ext = item.containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"
            val prefix = when (item.type) {
                ContentType.MOVIE -> "movie"
                ContentType.EPISODE -> "ep_s" + (item.season ?: 0).toString().padStart(2, '0') + "e" + (item.episode ?: 0).toString().padStart(2, '0')
                else -> "video"
            }
            val safeTitle = item.name.replace(Regex("[^a-zA-Z0-9 ]"), "").take(60).trim().replace(" ", "_").ifBlank { item.id }
            val fileName = "${prefix}_${safeTitle}_${item.id}.${ext}"
            downloader.enqueue(item.id, item.name, url, fileName)
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "⬇ Download iniciado: ${item.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun copyStreamLink(item: CatalogItem) {
        val cred = credentialsStore.loadActive() ?: return
        val urls = StreamUrlBuilder.candidates(cred, item, state.value.settings.streamFormat)
        val url = urls.firstOrNull() ?: return
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("XtreamlyTV", url))
        Toast.makeText(getApplication(), "🔗 Link copiado: ${item.name.take(40)}", Toast.LENGTH_SHORT).show()
    }
    
    val downloads get() = downloader.active

    fun openOfflineVideos() {
        _state.update { it.copy(screen = AppScreen.OfflineVideos) }
    }

    fun refreshDownloads() {
        downloader.queryProgress()
    }

    fun scanOffline(): List<OfflineFile> = downloader.listFiles()

    fun playOffline(file: OfflineFile) {
        val item = CatalogItem(
            id = "offline_" + file.name.replace(" ", "_"),
            type = ContentType.MOVIE,
            name = file.name,
        )
        val origin = _state.value.screen
        val returnFocus = focusForScreen(origin)
        val request = PlayerRequest(
            item = item,
            queue = listOf(item),
            urlCandidates = listOf(android.net.Uri.fromFile(java.io.File(file.path)).toString()),
            startPositionMs = 0L,
        )
        _state.update { it.copy(screen = AppScreen.Player(request, origin, returnFocus)) }
    }

    fun deleteOffline(file: OfflineFile) {
        downloader.deleteFile(file.path)
        downloader.dropByPath(file.path)
    }

    fun cancelDownload(id: Long) {
        downloader.cancel(id)
    }

    fun playAdjacentInQueue(delta: Int) {
        val current = _state.value.screen as? AppScreen.Player ?: return
        val queue = current.request.queue
        if (queue.size < 2) return
        val idx = queue.indexOfFirst { it.id == current.request.item.id }
        if (idx < 0) return
        val target = idx + delta
        if (target !in queue.indices) return
        val item = queue[target]
        val progress = _state.value.progress[itemKey(item)]
        val request = PlayerRequest(
            item = item,
            queue = queue,
            urlCandidates = urlCandidatesFor(item),
            startPositionMs = progress?.positionMs ?: 0L,
        )
        addRecent(item)
        _state.update { it.copy(screen = AppScreen.Player(request, current.origin, current.returnFocus), error = null, focusRequest = null) }
    }

    fun clearHistory() {
        _state.update { it.copy(recent = emptyList(), progress = emptyMap(), error = null) }
        viewModelScope.launch(Dispatchers.IO) { localStateStore.clearHistory() }
    }

    fun savePlaybackProgress(item: CatalogItem, positionMs: Long, durationMs: Long) {
        if (item.type == ContentType.LIVE || positionMs < 0L) return
        val key = itemKey(item)
        val next = _state.value.progress.toMutableMap()
        if (durationMs > 0L && positionMs >= durationMs - 30_000L) {
            next.remove(key)
        } else {
            next[key] = PlaybackProgress(positionMs, durationMs.coerceAtLeast(0L))
        }
        _state.update { it.copy(progress = next) }
        viewModelScope.launch(Dispatchers.IO) { localStateStore.saveProgress(next) }
    }

    fun clearPlaybackProgress(item: CatalogItem) {
        val next = _state.value.progress - itemKey(item)
        _state.update { it.copy(progress = next) }
        viewModelScope.launch(Dispatchers.IO) { localStateStore.saveProgress(next) }
    }

    fun getProviders(): List<Credentials> = credentialsStore.loadAll()

    fun addProvider(name: String, credentials: Credentials) {
        val current = credentialsStore.loadAll().toMutableList()
        val novo = credentials.copy(name = name.ifBlank { "Provider " + (current.size + 1) })
        current.add(novo)
        credentialsStore.saveAll(current)
        credentialsStore.setActive(novo.id)
        connect(novo)
    }

    fun importM3uAsProvider(url: String, onResult: (String) -> Unit = {}) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) { onResult("URL vazia."); return }
        viewModelScope.launch {
            try {
                _state.update { it.copy(loading = true, error = null) }
                val head = M3uCredentialExtractor.fetchFirstLines(trimmed)
                val extracted = M3uCredentialExtractor.extract(head)
                if (extracted != null) {
                    onResult("Credenciais extraídas — conectando como Xtream...")
                    val current = credentialsStore.loadAll().toMutableList()
                    current.add(extracted)
                    credentialsStore.saveAll(current)
                    credentialsStore.setActive(extracted.id)
                    _state.update { it.copy(loading = false) }
                    connect(extracted)
                } else {
                    onResult("Lista pública detectada — carregando como M3U nativo...")
                    addM3uProvider("M3U List", trimmed)
                    _state.update { it.copy(loading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Falha ao importar M3U: ${e.message?.take(120) ?: "erro"}") }
                onResult("Erro: ${e.message?.take(80)}")
            }
        }
    }

    fun importM3uFromFile(uri: android.net.Uri, onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(loading = true, error = null) }
                val text = withContext(Dispatchers.IO) {
                    getApplication<android.app.Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { r ->
                        val sb = StringBuilder()
                        var lines = 0
                        var bytes = 0L
                        while (lines < 200 && bytes < 256 * 1024) {
                            val line = r.readLine() ?: break
                            sb.appendLine(line)
                            bytes += line.length + 1
                            lines++
                        }
                        sb.toString()
                    } ?: ""
                }
                val extracted = M3uCredentialExtractor.extract(text)
                if (extracted != null) {
                    onResult("Credenciais extraídas do arquivo — conectando como Xtream...")
                    val current = credentialsStore.loadAll().toMutableList()
                    current.add(extracted)
                    credentialsStore.saveAll(current)
                    credentialsStore.setActive(extracted.id)
                    _state.update { it.copy(loading = false) }
                    connect(extracted)
                } else {
                    // fallback: salvar o arquivo no cache como provider M3U
                    val id = java.util.UUID.randomUUID().toString()
                    val f = java.io.File(getApplication<android.app.Application>().cacheDir, "m3u_$id.m3u")
                    withContext(Dispatchers.IO) {
                        getApplication<android.app.Application>().contentResolver.openInputStream(uri)?.use { input ->
                            f.outputStream().use { out -> input.copyTo(out) }
                        }
                    }
                    val channels = M3uParser.parseFromFile(f, limit = 30_000)
                    client = null
                    m3uMode = true
                    m3uChannels.clear()
                    channels.forEach { m3uChannels[it.id] = it }
                    val itemsByGroup = mutableMapOf<String, MutableList<CatalogItem>>()
                    val items = ArrayList<CatalogItem>(channels.size)
                    channels.forEach { ch ->
                        val item = CatalogItem(id = ch.id, type = ContentType.LIVE, name = ch.name, categoryId = ch.group, imageUrl = ch.logo)
                        items.add(item)
                        itemsByGroup.getOrPut(ch.group) { mutableListOf() }.add(item)
                    }
                    m3uCatalog.clear()
                    m3uCatalog["all"] = items
                    m3uCatalog.putAll(itemsByGroup)
                    val cats = listOf(Category(id = "all", name = "ALL")) + itemsByGroup.keys.map { Category(id = it, name = it) }
                    val cred = Credentials(server = "file://$id", username = "m3u", password = "", name = "Arquivo M3U", kind = "m3u")
                    val current = credentialsStore.loadAll().toMutableList()
                    current.add(cred)
                    credentialsStore.saveAll(current)
                    credentialsStore.setActive(cred.id)
                    _state.update {
                        it.copy(
                            screen = AppScreen.Home,
                            initializing = false,
                            loading = false,
                            catalogsLoading = false,
                            error = null,
                            provider = ProviderSummary(username = "M3U", status = "Active", expiration = null),
                            categories = mapOf(ContentType.LIVE to cats),
                            selectedCategories = emptyMap(),
                            items = emptyList(),
                            loadedItems = emptyMap(),
                            searchQuery = "",
                            credentials = cred,
                        )
                    }
                    onResult("Lista pública carregada do arquivo.")
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Falha ao ler arquivo: ${e.message?.take(120) ?: "erro"}") }
                onResult("Erro: ${e.message?.take(80)}")
            }
        }
    }

    fun addM3uProvider(name: String, url: String) {
        val cred = Credentials(server = url.trim(), username = "m3u", password = "", name = name.ifBlank { "M3U List" }, kind = "m3u")
        val current = credentialsStore.loadAll().toMutableList()
        current.add(cred)
        credentialsStore.saveAll(current)
        credentialsStore.setActive(cred.id)
        connect(cred)
    }

    fun updateProviderById(id: String, name: String, credentials: Credentials) {
        val current = credentialsStore.loadAll().toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            current[idx] = credentials.copy(id = id, name = name.ifBlank { current[idx].name })
            credentialsStore.saveAll(current)
            if (credentialsStore.loadActive()?.id == id) connect(current[idx])
        }
    }

    fun deleteProvider(id: String) {
        runCatching { java.io.File(getApplication<android.app.Application>().cacheDir, "m3u_$id.m3u").delete() }
        val current = credentialsStore.loadAll().toMutableList()
        val removed = current.firstOrNull { it.id == id } ?: return
        current.remove(removed)
        credentialsStore.saveAll(current)
        if (credentialsStore.loadActive()?.id == id) {
            if (current.isEmpty()) disconnect() else { credentialsStore.setActive(current[0].id); connect(current[0]) }
        }
    }

    fun useProvider(id: String) {
        val cred = credentialsStore.loadAll().find { it.id == id } ?: return
        credentialsStore.setActive(id)
        connect(cred)
    }

    fun disconnect() {
        client = null
        m3uMode = false
        m3uCatalog.clear()
        m3uChannels.clear()
        categoryCache.clear()
        lastFocusByArea.clear()
        _state.update {
            it.copy(
                screen = AppScreen.Login,
                initializing = false,
                loading = false,
                catalogsLoading = false,
                error = null,
                credentials = null,
                provider = null,
                categories = emptyMap(),
                selectedCategories = emptyMap(),
                items = emptyList(),
                loadedItems = emptyMap(),
                focusRequest = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) { credentialsStore.clear() }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun back() {
        when (val screen = _state.value.screen) {
            AppScreen.Login, AppScreen.Home, AppScreen.M3u -> Unit
            is AppScreen.Player -> _state.update {
                it.copy(screen = screen.origin, error = null, focusRequest = screen.returnFocus)
            }
            is AppScreen.Detail -> _state.update {
                it.copy(
                    screen = screen.origin,
                    detailEpisodes = emptyList(),
                    detailSelectedSeason = null,
                    error = null,
                    focusRequest = screen.returnFocus,
                )
            }
            AppScreen.FavoriteGroupsManager -> openFavorites()
            is AppScreen.FavoriteGroupEditor -> openFavoriteGroupsManager()
            is AppScreen.Catalog, AppScreen.Favorites, is AppScreen.FavoriteGroupBrowser, AppScreen.Settings, AppScreen.OfflineVideos -> openHome()
        }
    }

    private fun focusForScreen(screen: AppScreen): FocusRequest? = when (screen) {
        AppScreen.Home -> lastFocusByArea[AREA_HOME]
        is AppScreen.Catalog -> lastFocusByArea[catalogArea(screen.type)]
        AppScreen.Favorites, is AppScreen.FavoriteGroupBrowser -> lastFocusByArea[AREA_FAVORITES]
        is AppScreen.Detail -> lastFocusByArea[detailArea(screen.item.id)]
        else -> null
    }

    private fun currentQueueFor(item: CatalogItem): List<CatalogItem> = when {
        item.type == ContentType.EPISODE && _state.value.detailEpisodes.isNotEmpty() -> _state.value.detailEpisodes
        _state.value.items.isNotEmpty() -> _state.value.items
        else -> listOf(item)
    }

    private fun addRecent(item: CatalogItem) {
        val next = (listOf(item) + _state.value.recent.filterNot { itemKey(it) == itemKey(item) }).take(40)
        _state.update { it.copy(recent = next) }
        viewModelScope.launch(Dispatchers.IO) { localStateStore.saveRecent(next) }
    }

    private fun persistFavorites(
        favorites: List<CatalogItem>,
        groups: List<FavoriteGroup>,
        itemOrders: Map<String, List<String>>,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            localStateStore.saveFavorites(favorites)
            localStateStore.saveFavoriteGroups(groups)
            localStateStore.saveFavoriteItemOrders(itemOrders)
        }
    }

    private fun persistGroups(
        groups: List<FavoriteGroup>,
        groupOrder: List<String>,
        itemOrders: Map<String, List<String>>,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            localStateStore.saveFavoriteGroups(groups)
            localStateStore.saveFavoriteGroupOrder(groupOrder)
            localStateStore.saveFavoriteItemOrders(itemOrders)
        }
    }

    private fun normalizeGroupOrder(order: List<String>, groups: List<FavoriteGroup>): List<String> {
        val valid = BuiltInFavoriteGroupIds + groups.map { it.id }
        val validSet = valid.toSet()
        return (order.filter { it in validSet } + valid.filterNot { it in order }).distinct()
    }

    private fun updateLoadedItems(type: ContentType, items: List<CatalogItem>) {
        _state.update { state ->
            state.copy(loadedItems = state.loadedItems + (type to items.take(60)))
        }
    }

    private fun cacheCategory(type: ContentType, categoryId: String, items: List<CatalogItem>) {
        categoryCache[cacheKey(type, categoryId)] = items
        trimCategoryCache(_state.value.settings.maxCachedCategories)
    }

    private fun trimCategoryCache(maxPerType: Int) {
        ContentType.entries.filter { it != ContentType.EPISODE }.forEach { type ->
            val prefix = "${type.name}:"
            while (categoryCache.keys.count { it.startsWith(prefix) } > maxPerType) {
                val oldest = categoryCache.keys.firstOrNull { it.startsWith(prefix) } ?: break
                categoryCache.remove(oldest)
            }
        }
    }

    private fun cacheKey(type: ContentType, categoryId: String) = "${type.name}:$categoryId"

    private companion object {
        const val CONNECTION_TIMEOUT_MS = 30_000L
    }
}

internal const val AREA_HOME = "home"
internal const val AREA_FAVORITES = "favorites"

internal fun catalogArea(type: ContentType): String = "catalog:${type.name}"
internal fun catalogFocusScope(type: ContentType, categoryId: String): String = "catalog:${type.name}:$categoryId"
internal fun homeFocusScope(type: ContentType): String = "home:${type.name}"
internal fun favoriteFocusScope(groupId: String): String = "favorites:$groupId"
internal fun detailArea(seriesId: String): String = "detail:$seriesId"
internal fun detailFocusScope(seriesId: String, season: Int?): String = "detail:$seriesId:${season ?: 0}"

internal fun validFavoriteGroupIds(groups: List<FavoriteGroup>): Set<String> =
    (BuiltInFavoriteGroupIds + groups.map { it.id }).toSet()

internal fun firstVisibleFavoriteGroupId(order: List<String>, hidden: Set<String>): String =
    (order + BuiltInFavoriteGroupIds).distinct().firstOrNull { it !in hidden }.orEmpty()

private fun Throwable.userMessage(): String = when (this) {
    is TimeoutCancellationException, is SocketTimeoutException ->
        "The provider did not respond in time. Check the address and try again."
    is UnknownHostException ->
        "The provider host could not be found. Check the address and network connection."
    is ConnectException ->
        "The provider refused the connection. Check the address and port."
    is SSLHandshakeException ->
        "The provider's HTTPS certificate could not be verified."
    is IllegalArgumentException -> message ?: "The provider details are invalid."
    else -> message?.takeIf { it.isNotBlank() } ?: "Unable to connect to the provider."
}
