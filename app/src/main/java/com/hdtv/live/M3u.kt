package com.hdtv.live

import java.net.HttpURLConnection
import java.net.URL

data class Channel(
    val name: String,
    val logo: String,
    val group: String,
    val url: String,
    val derived: String = ""
)

object M3u {
    // 只保留直播:过滤点播(电影 / 电视剧)
    private val vodGroup = Regex("movie|series|vod|film|电影|电视剧|剧集", RegexOption.IGNORE_CASE)
    private val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"")
    private val prefixRegex = Regex("^([A-Za-z ]{2,14}?)\\s*[\\]\\)|｜:：\\-–]")

    private val countries: Map<String, String> = mapOf(
        "MY" to "Malaysia", "MALAYSIA" to "Malaysia",
        "SG" to "Singapore", "SINGAPORE" to "Singapore",
        "HK" to "Hong Kong", "HONG KONG" to "Hong Kong", "HONGKONG" to "Hong Kong",
        "TW" to "Taiwan", "TAIWAN" to "Taiwan",
        "CN" to "China", "CHINA" to "China",
        "MO" to "Macau", "MACAU" to "Macau",
        "US" to "USA", "USA" to "USA",
        "UK" to "UK", "GB" to "UK",
        "JP" to "Japan", "JAPAN" to "Japan",
        "KR" to "Korea", "KOREA" to "Korea", "SOUTH KOREA" to "Korea",
        "TH" to "Thailand", "THAILAND" to "Thailand",
        "ID" to "Indonesia", "INDONESIA" to "Indonesia",
        "IN" to "India", "INDIA" to "India",
        "PH" to "Philippines", "PHILIPPINES" to "Philippines",
        "VN" to "Vietnam", "VIETNAM" to "Vietnam",
        "AU" to "Australia", "AUSTRALIA" to "Australia",
        "CA" to "Canada", "CANADA" to "Canada"
    )

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
        var tvgId = ""
        for (raw in lines) {
            val l = raw.trim()
            if (l.startsWith("#EXTINF")) {
                val attrs = attrRegex.findAll(l).associate {
                    it.groupValues[1].lowercase() to it.groupValues[2]
                }
                logo = attrs["tvg-logo"] ?: ""
                group = attrs["group-title"] ?: ""
                tvgId = attrs["tvg-id"] ?: ""
                name = l.substringAfterLast(",").trim()
                if (name.isEmpty()) name = attrs["tvg-name"] ?: ""
            } else if (l.isNotEmpty() && !l.startsWith("#")) {
                if (name.isNotEmpty() && !isVod(l, group)) {
                    val derived = derive(name, tvgId)
                    out.add(Channel(name, logo, group.ifBlank { derived }, l, derived))
                }
                name = ""
                logo = ""
                group = ""
                tvgId = ""
            }
        }
        // 如果源里的分组几乎没有区分,就改用从频道名 / ID 推断出来的国家地区分类
        if (out.map { it.group }.distinct().size <= 2) {
            return out.map { it.copy(group = it.derived) }
        }
        return out
    }

    private fun derive(name: String, tvgId: String): String {
        val cleaned = name.trimStart { !it.isLetterOrDigit() }
        prefixRegex.find(cleaned)?.let {
            countries[it.groupValues[1].trim().uppercase()]?.let { c -> return c }
        }
        countries[tvgId.substringAfterLast('.', "").uppercase()]?.let { return it }
        return "其他"
    }

    private fun isVod(url: String, group: String): Boolean =
        url.contains("/movie/") || url.contains("/series/") || vodGroup.containsMatchIn(group)
}
