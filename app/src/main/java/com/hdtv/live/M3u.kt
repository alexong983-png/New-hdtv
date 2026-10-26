package com.hdtv.live

import java.net.HttpURLConnection
import java.net.URL

data class Channel(val name: String, val logo: String, val group: String, val url: String)

object M3u {
    // 只保留直播:过滤点播(电影 / 电视剧)
    private val vodGroup = Regex("movie|series|vod|film|电影|电视剧|剧集", RegexOption.IGNORE_CASE)
    private val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun fetch(link: String): List<Channel> {
        val conn = URL(link).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        val lines = conn.inputStream.bufferedReader().use { it.readLines() }
        conn.disconnect()
        return parse(lines)
    }

    fun parse(lines: List<String>): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""
        var logo = ""
        var group = ""
        for (raw in lines) {
            val l = raw.trim()
            if (l.startsWith("#EXTINF")) {
                val attrs = attrRegex.findAll(l).associate {
                    it.groupValues[1].lowercase() to it.groupValues[2]
                }
                logo = attrs["tvg-logo"] ?: ""
                group = attrs["group-title"] ?: ""
                name = l.substringAfterLast(",").trim()
                if (name.isEmpty()) name = attrs["tvg-name"] ?: ""
            } else if (l.isNotEmpty() && !l.startsWith("#")) {
                if (name.isNotEmpty() && !isVod(l, group)) {
                    out.add(Channel(name, logo, group.ifEmpty { "其他" }, l))
                }
                name = ""
                logo = ""
                group = ""
            }
        }
        return out
    }

    private fun isVod(url: String, group: String): Boolean =
        url.contains("/movie/") || url.contains("/series/") || vodGroup.containsMatchIn(group)
}
