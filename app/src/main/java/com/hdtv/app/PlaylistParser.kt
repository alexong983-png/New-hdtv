package com.hdtv.app

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 负责拉取直播源和点播源两个 M3U 地址，解析后合并成统一的 Channel 列表。
 * 分类(category)取自每条记录的 group-title，如果两个源里出现同名分类，
 * 里面的直播和点播内容会被合并显示在同一个分类行里。
 */
object PlaylistParser {

    // 直播源（iptv12k）
    private const val LIVE_URL =
        "http://iptv12k.com:35461/get.php?username=acllt2910&password=111111&type=m3u"

    // 点播源
    private const val VOD_URL = "https://bit.ly/jaren22"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 同步方法，需在后台线程/协程里调用。
     * 两个源中任意一个拉取失败都不会影响另一个的显示。
     */
    fun fetchAll(): List<Channel> {
        val result = mutableListOf<Channel>()
        try {
            result += fetchOne(LIVE_URL, isLive = true)
        } catch (e: Exception) {
            // 直播源拉取失败，忽略，继续尝试点播源
        }
        try {
            result += fetchOne(VOD_URL, isLive = false)
        } catch (e: Exception) {
            // 点播源拉取失败，忽略
        }
        return result
    }

    private fun fetchOne(url: String, isLive: Boolean): List<Channel> {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return emptyList()
            return parseM3U(body, isLive)
        }
    }

    private val groupTitleRegex = Regex("group-title=\"([^\"]*)\"")
    private val logoRegex = Regex("tvg-logo=\"([^\"]*)\"")

    fun parseM3U(content: String, isLive: Boolean): List<Channel> {
        val lines = content.lines()
        val channels = mutableListOf<Channel>()

        var pendingTitle: String? = null
        var pendingLogo: String? = null
        var pendingCategory: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF")) {
                // 取逗号后面的部分作为标题
                val commaIndex = line.indexOf(',')
                pendingTitle = if (commaIndex != -1 && commaIndex < line.length - 1) {
                    line.substring(commaIndex + 1).trim()
                } else {
                    "未命名"
                }
                pendingLogo = logoRegex.find(line)?.groupValues?.get(1)
                pendingCategory = groupTitleRegex.find(line)?.groupValues?.get(1)
                    ?: if (isLive) "直播" else "点播"
            } else if (!line.startsWith("#")) {
                // 这一行是播放地址
                val title = pendingTitle ?: continue
                channels += Channel(
                    title = title,
                    logoUrl = pendingLogo,
                    category = pendingCategory ?: if (isLive) "直播" else "点播",
                    streamUrl = line,
                    isLive = isLive
                )
                pendingTitle = null
                pendingLogo = null
                pendingCategory = null
            }
        }
        return channels
    }
}
