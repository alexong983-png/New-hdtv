package com.hdtv.app

/**
 * 代表一条可播放的内容，直播频道和点播视频统一用这个模型，
 * 通过 category 字段决定它显示在哪一个分类行里。
 */
data class Channel(
    val title: String,
    val logoUrl: String?,
    val category: String,
    val streamUrl: String,
    val isLive: Boolean
)
