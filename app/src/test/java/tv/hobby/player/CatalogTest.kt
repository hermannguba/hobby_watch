package tv.hobby.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.hobby.player.data.AntonovMedia
import tv.hobby.player.data.AntonovPost
import tv.hobby.player.data.PlaybackChoice
import tv.hobby.player.data.StudioMatch
import tv.hobby.player.data.actionLabel
import tv.hobby.player.data.chooseAntonov
import tv.hobby.player.data.externalLabel
import tv.hobby.player.data.isDirectMediaUrl
import tv.hobby.player.data.parseAntonovPost
import tv.hobby.player.data.studioRails
import tv.hobby.player.data.toDetail
import org.json.JSONObject

class CatalogTest {
    @Test
    fun directUrlClassification() {
        assertTrue(isDirectMediaUrl("https://cdn.example.com/a/master.m3u8"))
        assertTrue(isDirectMediaUrl("https://cdn.example.com/a/video.mp4?token=1"))
        assertTrue(isDirectMediaUrl("https://cdn.example.com/a/stream.mpd"))
        assertTrue(isDirectMediaUrl("https://cdn.example.com/a/clip.webm"))
        assertFalse(isDirectMediaUrl("http://cdn.example.com/a/master.m3u8"))
        assertFalse(isDirectMediaUrl("javascript:alert(1)"))
        assertFalse(isDirectMediaUrl("https://vkvideo.ru/video_ext.php?oid=1&id=2"))
        assertFalse(isDirectMediaUrl("https://rutube.ru/play/embed/abc"))
        assertFalse(isDirectMediaUrl("https://www.youtube.com/embed/abc"))
    }

    @Test
    fun studioPrefersADirectLinkAndKeepsTheOtherPlayer() {
        val match = liveMatch(
            vk = "https://vk.com/video_ext.php?oid=1&id=2",
            alt = "https://cdn.example.com/a/master.m3u8?token=1",
        )
        val detail = match.toDetail()
        assertEquals("https://cdn.example.com/a/master.m3u8?token=1", detail.primary.directUrl)
        assertNull(detail.primary.externalUrl)
        assertEquals("https://vk.com/video_ext.php?oid=1&id=2", detail.alt?.externalUrl)
        assertEquals("VK Видео", detail.alt?.externalLabel)
        val rails = studioRails(listOf(match))
        assertEquals("Сейчас и скоро", rails.first().title)
        assertEquals("В эфире", rails.first().cards.first().badge)
    }

    @Test
    fun studioWithoutAFileOpensTheExternalPlayer() {
        val detail = liveMatch(
            vk = "https://vkvideo.ru/video_ext.php?oid=-1&id=2&hash=abc",
            alt = "https://rutube.ru/play/embed/xyz",
        ).toDetail()
        assertNull(detail.primary.directUrl)
        assertEquals("https://vkvideo.ru/video_ext.php?oid=-1&id=2&hash=abc", detail.primary.externalUrl)
        assertEquals("VK Видео", detail.primary.externalLabel)
        assertEquals("Открыть в VK Видео", detail.primary.actionLabel())
        assertEquals("Rutube", detail.alt?.externalLabel)
        assertTrue(detail.note!!.contains("не опубликован"))
        assertEquals("YouTube", externalLabel("https://www.youtube.com/watch?v=abc"))
    }

    @Test
    fun closedAntonovVideoDoesNotUseTheFullPlaylist() {
        val post = post(
            available = false,
            full = "https://private.antonov.media/videos/hls/full/master.m3u8?sig=1",
            preview = "https://private.antonov.media/videos/hls/preview/master.m3u8",
        )
        val choice = chooseAntonov(post) as PlaybackChoice.Direct
        assertTrue(choice.trailer)
        assertTrue(choice.url.contains("/preview/"))
        assertFalse(choice.url.contains("/full/"))
        val detail = post.toDetail(loggedIn = false)
        assertEquals("Смотреть трейлер", detail.primary.actionLabel())
        assertTrue(detail.showLogin)
    }

    @Test
    fun openAntonovVideoUsesTheFullPlaylist() {
        val post = post(
            available = true,
            full = "https://private.antonov.media/videos/hls/full/master.m3u8?sig=1",
            preview = "https://private.antonov.media/videos/hls/preview/master.m3u8",
        )
        val choice = chooseAntonov(post) as PlaybackChoice.Direct
        assertFalse(choice.trailer)
        assertTrue(choice.url.contains("/full/"))
        val detail = post.toDetail(loggedIn = true)
        assertEquals("Смотреть", detail.primary.actionLabel())
        assertFalse(detail.showLogin)
        assertNull(detail.note)
    }

    @Test
    fun postParserStripsDescription() {
        val json = JSONObject(
            """
            {
              "id": "abc",
              "title": "Выпуск",
              "description": "<b>Текст</b>  выпуска",
              "duration": 125,
              "postAvailable": false,
              "video": null,
              "previewVideo": {"id": "p", "s3HlsMasterPlaylist": "https://private.antonov.media/videos/hls/p/master.m3u8"},
              "subscription": {"name": "Добрый зритель"}
            }
            """,
        )
        val post = parseAntonovPost(json)
        assertEquals("Текст выпуска", post.description)
        assertEquals("Смотреть трейлер", post.toDetail(loggedIn = false).primary.actionLabel())
    }

    private fun liveMatch(vk: String?, alt: String?) = StudioMatch(
        id = "1",
        league = "NHL",
        teamHome = "А",
        teamAway = "Б",
        startTime = "2026-11-10T04:15:00+03:00",
        commentator = "Комментатор",
        vkSrc = vk,
        altSrc = alt,
        status = "live",
        streamTitle = null,
    )

    private fun post(available: Boolean, full: String?, preview: String?) = AntonovPost(
        id = "1",
        title = "Выпуск",
        description = "Описание",
        duration = 90.0,
        postAvailable = available,
        video = full?.let { AntonovMedia("v", it) },
        preview = preview?.let { AntonovMedia("p", it) },
        subscriptionName = "Добрый зритель",
        imageUrl = null,
    )
}
