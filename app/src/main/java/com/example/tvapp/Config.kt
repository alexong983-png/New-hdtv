package com.example.tvapp

/** 在这里修改直播源 / 点播源地址（M3U 格式） */
object Config {
    const val IPTV_URL = "https://bit.ly/alexalex983"
    const val VOD_URL = "https://bit.ly/jaren22"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"

    /** 点播源里的分组最多显示成多少个顶部标签 */
    const val MAX_VOD_TABS = 12
    /** 每个"推荐"区块放多少个内容 */
    const val SECTION_SIZE = 24
}
