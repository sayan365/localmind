package ai.localmind.device

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SharedContentStore(context: Context) {
    private val preferences = context.getSharedPreferences("shared_content", Context.MODE_PRIVATE)

    fun add(mimeType: String?, text: String?, uri: String?) {
        val items = list().toMutableList()
        items.add(
            0,
            SharedContent(
                id = UUID.randomUUID().toString(),
                mimeType = mimeType ?: "application/octet-stream",
                text = text,
                uri = uri,
                receivedAt = System.currentTimeMillis()
            )
        )
        save(items.take(MAX_ITEMS))
    }

    fun list(): List<SharedContent> {
        val raw = preferences.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                SharedContent(
                    id = item.getString("id"),
                    mimeType = item.getString("mimeType"),
                    text = item.optString("text").takeIf { it.isNotBlank() },
                    uri = item.optString("uri").takeIf { it.isNotBlank() },
                    receivedAt = item.getLong("receivedAt")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun search(query: String): List<SharedContent> = searchSharedContent(list(), query)

    private fun save(items: List<SharedContent>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("mimeType", item.mimeType)
                put("text", item.text ?: "")
                put("uri", item.uri ?: "")
                put("receivedAt", item.receivedAt)
            })
        }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private companion object {
        const val KEY_ITEMS = "items"
        const val MAX_ITEMS = 50
    }
}
