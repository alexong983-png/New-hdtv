package com.example.tvapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class Item(val name: String, val url: String, val logo: String?, val group: String)

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Item> {
        val out = mutableListOf<Item>()
        var name: String? = null
        var logo: String? = null
        var group = "其他"
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    val a = attr.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    logo = a["tvg-logo"]?.ifBlank { null }
                    group = a["group-title"]?.ifBlank { null } ?: "其他"
                    name = line.substringAfterLast(",").trim()
                        .ifBlank { a["tvg-name"] ?: "未命名" }
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val n = name
                    if (n != null) out += Item(n, line, logo, group)
                    name = null; logo = null; group = "其他"
                }
            }
        }
        return out
    }
}

object Repo {
    private val client = OkHttpClient()

    suspend fun load(url: String): List<Item> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", Config.USER_AGENT).build()
        client.newCall(req).execute().use { M3uParser.parse(it.body?.string().orEmpty()) }
    }
}
