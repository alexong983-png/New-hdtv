package com.hdtv.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainFragment : BrowseSupportFragment() {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        adapter = rowsAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is Channel) {
                val intent = Intent(requireContext(), PlaybackActivity::class.java)
                intent.putExtra(PlaybackActivity.EXTRA_URL, item.streamUrl)
                intent.putExtra(PlaybackActivity.EXTRA_TITLE, item.title)
                startActivity(intent)
            }
        }

        loadContent()
    }

    private fun loadContent() {
        scope.launch {
            val channels = withContext(Dispatchers.IO) {
                PlaylistParser.fetchAll()
            }

            if (channels.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "没有拉取到任何内容，请检查网络或源地址",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            // 按分类分组，直播和点播内容如果分类名相同会合并到同一行
            val grouped = channels.groupBy { it.category }

            rowsAdapter.clear()
            val cardPresenter = CardPresenter()

            for ((category, items) in grouped) {
                val rowAdapter = ArrayObjectAdapter(cardPresenter)
                rowAdapter.addAll(0, items)
                val header = HeaderItem(category)
                rowsAdapter.add(ListRow(header, rowAdapter))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
