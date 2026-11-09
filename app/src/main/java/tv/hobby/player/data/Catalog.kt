package tv.hobby.player.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI

enum class SourceId { Studio, Antonov }

data class PlayerRequest(
    val title: String,
    val directUrl: String? = null,
    val referer: String? = null,
    val externalUrl: String? = null,
    val externalLabel: String? = null,
    val trailer: Boolean = false,
)

sealed class PlaybackChoice {
    data class Direct(val url: String, val trailer: Boolean) : PlaybackChoice()
    data object Unavailable : PlaybackChoice()
}

data class CardModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val badge: String?,
)

data class Rail(val title: String, val cards: List<CardModel>)

data class DetailUi(
    val source: SourceId,
    val id: String,
    val title: String,
    val eyebrow: String,
    val imageUrl: String?,
    val description: String,
    val note: String?,
    val primary: PlayerRequest,
    val alt: PlayerRequest?,
    val showLogin: Boolean,
)

data class StudioMatch(
    val id: String,
    val league: String,
    val teamHome: String,
    val teamAway: String,
    val startTime: String,
    val commentator: String,
    val vkSrc: String?,
    val altSrc: String?,
    val status: String,
    val streamTitle: String?,
)

data class CategoryChip(val id: String, val name: String)

data class AntonovSummary(
    val id: String,
    val title: String,
    val description: String,
    val duration: Double?,
    val subscriptionName: String?,
    val author: String?,
    val categoryName: String?,
    val imageUrl: String?,
)

data class AntonovPage(val posts: List<AntonovSummary>, val hasMore: Boolean, val total: Int)

data class AntonovMedia(val id: String, val hls: String?)

data class AntonovPost(
    val id: String,
    val title: String,
    val description: String,
    val duration: Double?,
    val postAvailable: Boolean,
    val video: AntonovMedia?,
    val preview: AntonovMedia?,
    val subscriptionName: String?,
    val imageUrl: String?,
)

data class Account(val id: String, val displayName: String)

class CatalogException(message: String) : Exception(message)

const val STUDIO_ORIGIN = "https://xn--36-llcq7bgh2h.online/"
const val STUDIO_API = "https://xn--36-llcq7bgh2h.online/api/matches.php"
const val ANTONOV_ORIGIN = "https://www.antonov.media/"
const val ANTONOV_GRAPHQL = "https://api.antonov.media/graphql"
const val ANTONOV_LOGIN = "https://www.antonov.media/login"
const val BROWSER_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private val LEAGUE_ORDER = listOf("NHL", "NFL", "NBA", "MLB", "NCAA", "MLS", "ATP", "WTA", "ATP/WTA", "UFL", "PLL")

fun isHttpsUrl(url: String?): Boolean = !url.isNullOrBlank() && url.startsWith("https://")

fun isDirectMediaUrl(url: String?): Boolean {
    if (!isHttpsUrl(url)) return false
    val path = url!!.substringBefore('?').substringBefore('#')
    return path.endsWith(".m3u8") ||
        path.endsWith(".mpd") ||
        path.endsWith(".mp4") ||
        path.endsWith(".m4v") ||
        path.endsWith(".webm")
}

fun externalLabel(url: String): String {
    val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
    return when {
        hostIs(host, "vk.com", "vk.ru", "vkvideo.ru") -> "VK Видео"
        hostIs(host, "rutube.ru") -> "Rutube"
        hostIs(host, "youtube.com", "youtu.be") -> "YouTube"
        else -> "плеер"
    }
}

fun PlayerRequest.actionLabel(): String = when {
    directUrl != null && trailer -> "Смотреть трейлер"
    directUrl != null -> "Смотреть"
    !externalUrl.isNullOrBlank() -> "Открыть в ${externalLabel ?: "плеер"}"
    else -> "Недоступно"
}

fun PlayerRequest.canOpen(): Boolean = directUrl != null || !externalUrl.isNullOrBlank()

fun chooseAntonov(post: AntonovPost): PlaybackChoice {
    val full = post.video?.hls
    if (post.postAvailable && isDirectMediaUrl(full)) {
        return PlaybackChoice.Direct(full!!, trailer = false)
    }
    val preview = post.preview?.hls
    if (isDirectMediaUrl(preview)) {
        return PlaybackChoice.Direct(preview!!, trailer = true)
    }
    return PlaybackChoice.Unavailable
}

fun parseStudioMatches(body: String): List<StudioMatch> {
    val array = JSONArray(body)
    return List(array.length()) { index ->
        val item = array.getJSONObject(index)
        StudioMatch(
            id = item.idString("id"),
            league = item.optString("league"),
            teamHome = item.optString("team_home"),
            teamAway = item.optString("team_away"),
            startTime = item.optString("start_time"),
            commentator = item.optString("commentator"),
            vkSrc = item.cleanString("vk_src"),
            altSrc = item.cleanString("alt_src"),
            status = item.optString("status"),
            streamTitle = item.cleanString("stream_title"),
        )
    }
}

fun studioRails(matches: List<StudioMatch>): List<Rail> {
    val rails = mutableListOf<Rail>()
    val upcoming = matches.filter { it.status == "live" || it.status == "soon" }
    if (upcoming.isNotEmpty()) {
        rails += Rail("Сейчас и скоро", upcoming.map { it.toCard() })
    }
    val recorded = matches.filter { match ->
        match.status == "finished" && match.links().isNotEmpty()
    }
    val byLeague = recorded.groupBy { it.league.ifBlank { "Другое" } }
    val names = byLeague.keys.sortedWith(compareBy({ name ->
        val index = LEAGUE_ORDER.indexOf(name)
        if (index < 0) LEAGUE_ORDER.size else index
    }, { it }))
    names.forEach { name ->
        rails += Rail(name, byLeague.getValue(name).map { it.toCard() })
    }
    return rails
}

fun StudioMatch.toCard(): CardModel = CardModel(
    id = id,
    title = displayTitle(),
    subtitle = listOf(formatClock(startTime), commentator).filter { it.isNotBlank() }.joinToString(" · "),
    imageUrl = null,
    badge = when (status) {
        "live" -> "В эфире"
        "soon" -> "Скоро"
        else -> null
    },
)

fun StudioMatch.toDetail(): DetailUi {
    val title = displayTitle()
    val links = links()
    val direct = links.firstOrNull { isDirectMediaUrl(it) }
    val primary = when {
        direct != null -> PlayerRequest(title = title, directUrl = direct, referer = STUDIO_ORIGIN)
        links.isNotEmpty() -> PlayerRequest(
            title = title,
            externalUrl = links.first(),
            externalLabel = externalLabel(links.first()),
        )
        else -> PlayerRequest(title = title)
    }
    val altUrl = links.firstOrNull { it != primary.directUrl && it != primary.externalUrl }
    val alt = altUrl?.let { url ->
        if (isDirectMediaUrl(url)) {
            PlayerRequest(title = title, directUrl = url, referer = STUDIO_ORIGIN)
        } else {
            PlayerRequest(title = title, externalUrl = url, externalLabel = externalLabel(url))
        }
    }
    val note = when {
        direct == null && links.isNotEmpty() -> "Прямой поток не опубликован. Откроем ${primary.externalLabel}."
        links.isEmpty() -> "Трансляция ещё не опубликована."
        else -> null
    }
    return DetailUi(
        source = SourceId.Studio,
        id = id,
        title = title,
        eyebrow = listOf(league, formatClock(startTime), commentator).filter { it.isNotBlank() }.joinToString(" · "),
        imageUrl = null,
        description = "",
        note = note,
        primary = primary,
        alt = alt,
        showLogin = false,
    )
}

fun parseCategories(array: JSONArray): List<CategoryChip> = List(array.length()) { index ->
    val item = array.getJSONObject(index)
    CategoryChip(id = item.idString("id"), name = item.optString("name"))
}.filter { it.id.isNotBlank() && it.name.isNotBlank() }

fun parseAntonovPage(posts: JSONObject): AntonovPage {
    val array = posts.optJSONArray("posts") ?: JSONArray()
    return AntonovPage(
        posts = List(array.length()) { index -> parseSummary(array.getJSONObject(index)) },
        hasMore = posts.optBoolean("hasMore"),
        total = posts.optInt("totalCount"),
    )
}

fun parseAntonovPost(item: JSONObject): AntonovPost = AntonovPost(
    id = item.idString("id"),
    title = item.optString("title"),
    description = stripHtml(item.optString("description")),
    duration = if (item.isNull("duration")) null else item.optDouble("duration"),
    postAvailable = item.optBoolean("postAvailable"),
    video = item.optJSONObject("video")?.toMedia(),
    preview = item.optJSONObject("previewVideo")?.toMedia(),
    subscriptionName = item.optJSONObject("subscription")?.optString("name")?.takeIf { it.isNotBlank() },
    imageUrl = item.optJSONObject("previewAsset")?.cleanString("previewUrl"),
)

fun parseAccount(me: JSONObject?): Account? {
    if (me == null) return null
    val id = me.idString("id")
    if (id.isBlank()) return null
    val name = me.optString("displayName").ifBlank { "Аккаунт" }
    return Account(id, name)
}

fun AntonovSummary.toCard(): CardModel = CardModel(
    id = id,
    title = title,
    subtitle = listOfNotNull(categoryName, author, formatDuration(duration).ifBlank { null }).joinToString(" · "),
    imageUrl = imageUrl,
    badge = null,
)

fun AntonovPost.toDetail(loggedIn: Boolean): DetailUi {
    val choice = chooseAntonov(this)
    val request = if (choice is PlaybackChoice.Direct) {
        PlayerRequest(
            title = title,
            directUrl = choice.url,
            referer = ANTONOV_ORIGIN,
            trailer = choice.trailer,
        )
    } else {
        PlayerRequest(title = title)
    }
    val note = if (choice is PlaybackChoice.Direct && choice.trailer) {
        if (loggedIn) "Полная запись сейчас закрыта. Можно смотреть трейлер."
        else "Войдите на сайте, чтобы открыть запись. Пока доступен трейлер."
    } else if (choice is PlaybackChoice.Unavailable && !loggedIn) {
        "Войдите через сайт Антонов Медиа, чтобы смотреть это видео."
    } else if (choice is PlaybackChoice.Unavailable) {
        "Видео недоступно в этой сессии."
    } else {
        null
    }
    return DetailUi(
        source = SourceId.Antonov,
        id = id,
        title = title,
        eyebrow = listOfNotNull(subscriptionName, formatDuration(duration).ifBlank { null }).joinToString(" · "),
        imageUrl = imageUrl,
        description = description,
        note = note,
        primary = request,
        alt = null,
        showLogin = !loggedIn && !postAvailable,
    )
}

fun formatDuration(seconds: Double?): String {
    if (seconds == null || seconds <= 0.0 || seconds.isNaN()) return ""
    val total = seconds.toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}

fun formatClock(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val match = Regex("""(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})""").find(raw) ?: return raw
    val month = match.groupValues[2]
    val day = match.groupValues[3]
    val hour = match.groupValues[4]
    val minute = match.groupValues[5]
    return "$day.$month $hour:$minute"
}

fun userMessage(error: Throwable): String = when (error) {
    is CatalogException -> error.message ?: "Не удалось загрузить данные"
    is SocketTimeoutException -> "Сайт не ответил вовремя"
    is IOException -> "Нет соединения с сайтом"
    else -> "Не удалось загрузить данные"
}

private fun StudioMatch.displayTitle(): String = when {
    !streamTitle.isNullOrBlank() -> streamTitle
    teamHome.isNotBlank() || teamAway.isNotBlank() -> listOf(teamHome, teamAway).filter { it.isNotBlank() }.joinToString(" — ")
    else -> league.ifBlank { "Трансляция" }
}

private fun StudioMatch.links(): List<String> = listOfNotNull(vkSrc, altSrc).filter { it.isNotBlank() }

private fun hostIs(host: String, vararg roots: String): Boolean = roots.any { root ->
    host == root || host.endsWith(".$root")
}

private fun parseSummary(item: JSONObject): AntonovSummary = AntonovSummary(
    id = item.idString("id"),
    title = item.optString("title"),
    description = stripHtml(item.optString("description")),
    duration = if (item.isNull("duration")) null else item.optDouble("duration"),
    subscriptionName = item.cleanString("subscriptionName"),
    author = item.optJSONObject("author")?.cleanString("displayName"),
    categoryName = item.optJSONObject("category")?.cleanString("name"),
    imageUrl = item.optJSONObject("previewAsset")?.cleanString("previewUrl"),
)

private fun JSONObject.toMedia(): AntonovMedia = AntonovMedia(
    id = idString("id"),
    hls = cleanString("s3HlsMasterPlaylist"),
)

private fun JSONObject.idString(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return when (val value = get(key)) {
        is Number -> {
            val asDouble = value.toDouble()
            if (asDouble % 1.0 == 0.0) asDouble.toLong().toString() else value.toString()
        }
        else -> value.toString()
    }
}

private fun JSONObject.cleanString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
}

private fun stripHtml(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
