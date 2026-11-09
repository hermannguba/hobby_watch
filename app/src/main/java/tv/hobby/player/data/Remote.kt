package tv.hobby.player.data

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SessionCookies {
    fun header(url: String): String = runCatching {
        CookieManager.getInstance().getCookie(url).orEmpty()
    }.getOrDefault("")

    fun flush() {
        runCatching { CookieManager.getInstance().flush() }
    }

    fun clear() {
        runCatching {
            CookieManager.getInstance().removeAllCookies { }
            CookieManager.getInstance().flush()
        }
    }
}

private val webCookies = object : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = CookieManager.getInstance()
        val target = url.toString()
        cookies.forEach { cookie -> manager.setCookie(target, cookie.toString()) }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return header.split(';').mapNotNull { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) null else Cookie.parse(url, trimmed)
        }
    }
}

private val client: OkHttpClient = OkHttpClient.Builder()
    .cookieJar(webCookies)
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(40, TimeUnit.SECONDS)
    .callTimeout(45, TimeUnit.SECONDS)
    .build()

class Studio36Api {
    fun load(): List<StudioMatch> {
        val request = Request.Builder()
            .url(STUDIO_API)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw CatalogException("Сайт ответил ${response.code}")
            return parseStudioMatches(body)
        }
    }
}

class AntonovApi {
    fun categories(): List<CategoryChip> {
        val data = graphql("query { categories { id name slug } }", null)
        return parseCategories(data.optJSONArray("categories") ?: org.json.JSONArray())
    }

    fun page(offset: Int, limit: Int, categoryId: String?): AntonovPage {
        val filters = JSONObject()
            .put("limit", limit)
            .put("offset", offset)
            .put("type", "VIDEO")
        if (!categoryId.isNullOrBlank()) filters.put("categoryId", categoryId)
        val data = graphql(POSTS, JSONObject().put("filters", filters))
        return parseAntonovPage(data.getJSONObject("posts"))
    }

    fun post(id: String): AntonovPost {
        val data = graphql(POST, JSONObject().put("id", id))
        return parseAntonovPost(data.getJSONObject("post"))
    }

    fun account(): Account? {
        val data = graphql("query { me { id displayName } }", null)
        val me = if (data.isNull("me")) null else data.optJSONObject("me")
        return parseAccount(me)
    }

    fun logout() {
        runCatching { graphql("mutation { logout }", null) }
        SessionCookies.clear()
    }

    private fun graphql(query: String, variables: JSONObject?): JSONObject {
        val payload = JSONObject().put("query", query)
        if (variables != null) payload.put("variables", variables)
        val request = Request.Builder()
            .url(ANTONOV_GRAPHQL)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json")
            .header("Origin", "https://www.antonov.media")
            .header("Referer", ANTONOV_ORIGIN)
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw CatalogException("Сайт ответил ${response.code}")
            val json = JSONObject(body)
            if (json.has("errors")) {
                val message = json.getJSONArray("errors").optJSONObject(0)?.optString("message").orEmpty()
                throw CatalogException(if (message.isBlank()) "Сайт не принял запрос" else message)
            }
            return json.getJSONObject("data")
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val POSTS = """
            query Posts(${'$'}filters: PostFiltersInput) {
              posts(filters: ${'$'}filters) {
                totalCount
                hasMore
                posts {
                  id
                  title
                  description
                  duration
                  subscriptionName
                  publishedAt
                  author { displayName }
                  category { id name }
                  previewAsset { previewUrl }
                }
              }
            }
        """.trimIndent()
        val POST = """
            query Post(${'$'}id: ID!) {
              post(id: ${'$'}id) {
                id
                title
                description
                duration
                postAvailable
                video { id s3HlsMasterPlaylist }
                previewVideo { id s3HlsMasterPlaylist }
                subscription { name }
                previewAsset { previewUrl }
              }
            }
        """.trimIndent()
    }
}
