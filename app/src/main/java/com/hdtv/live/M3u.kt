package com.hdtv.live

import java.net.HttpURLConnection
import java.net.URL

data class Channel(
    val name: String,
    val logo: String,
    val group: String,
    val url: String
)

object M3u {
    // 只用链接路径判断点播:/movie/ 和 /series/ 是电影、电视剧,直播不带这两个路径
    private val vodGroup = Regex("\\bvod\\b", RegexOption.IGNORE_CASE)
    private val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"")

    // 频道名前缀,例如 "Malaysia 1 | AEC"、"SPORTS: beIN 1"、"[SG] Channel 5"
    private val prefixRegex =
        Regex("^([\\p{L}\\p{N}][\\p{L}\\p{N} &+]{1,24}?)\\s*(?:[\\]\\)|｜:：]|\\s[-–]\\s)\\s*(.+)$")

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

    private class Raw(
        val name: String,
        val logo: String,
        val group: String,
        val url: String,
        val tvgId: String
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
        val raws = ArrayList<Raw>()
        var name = ""
        var logo = ""
        var group = ""
        var tvgId = ""
        for (line in lines) {
            val l = line.trim()
            if (l.startsWith("#EXTINF")) {
                val attrs = attrRegex.findAll(l).associate {
                    it.groupValues[1].lowercase() to it.groupValues[2]
                }
                logo = attrs["tvg-logo"] ?: attrs["logo"] ?: ""
                group = attrs["group-title"] ?: ""
                tvgId = attrs["tvg-id"] ?: ""
                name = l.substringAfterLast(",").trim()
                if (name.isEmpty()) name = attrs["tvg-name"] ?: ""
            } else if (l.isNotEmpty() && !l.startsWith("#")) {
                if (name.isNotEmpty() && !isVod(l, group)) {
                    raws.add(Raw(name, logo, group, l, tvgId))
                }
                name = ""
                logo = ""
                group = ""
                tvgId = ""
            }
        }

        // 源里的分组如果基本没有区分,就改用频道名前缀分类
        val useDerived = raws.map { it.group }.filter { it.isNotBlank() }.distinct().size <= 1

        val splits = raws.map { splitPrefix(it.name) }
        val canon = HashMap<String, String>()
        val counts = HashMap<String, Int>()
        for (s in splits) {
            if (s != null) {
                val k = s.first.lowercase()
                canon.getOrPut(k) { s.first }
                counts[k] = (counts[k] ?: 0) + 1
            }
        }

        return raws.mapIndexed { i, r ->
            val s = splits[i]
            val key = s?.first?.lowercase()
            // 同一个前缀至少出现 3 次才当作分类,避免把 "CNN - News" 这类误当分类
            val prefixOk = key != null && (counts[key] ?: 0) >= 3
            val derived = if (prefixOk) {
                canon[key!!]!!
            } else {
                countries[r.tvgId.substringAfterLast('.', "").uppercase()] ?: "其他"
            }
            val g = if (r.group.isNotBlank() && !useDerived) r.group else derived
            val n = if (prefixOk && g == derived) s!!.second else r.name
            Channel(n, r.logo, g, r.url)
        }
    }

    private fun splitPrefix(name: String): Pair<String, String>? {
        val cleaned = name.trimStart { !it.isLetterOrDigit() }
        val m = prefixRegex.find(cleaned) ?: return null
        val raw = m.groupValues[1].trim()
        val rest = m.groupValues[2].trim()
        if (raw.isEmpty() || rest.isEmpty()) return null
        val key = countries[raw.uppercase()] ?: pretty(raw)
        return key to rest
    }

    private fun pretty(s: String): String =
        if (s.length > 3 && s == s.uppercase() && s.any { it.isLetter() }) {
            s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        } else s

    private fun isVod(url: String, group: String): Boolean =
        url.contains("/movie/") || url.contains("/series/") || vodGroup.containsMatchIn(group)
}
