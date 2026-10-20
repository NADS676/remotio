package app.remotio.tv

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** Remembers the TVs the user has added and which one the remote points at. */
class SavedTvStore(context: Context) {
    private val prefs = context.getSharedPreferences("tvs", Context.MODE_PRIVATE)

    fun load(): List<Tv> {
        val json = prefs.getString(KEY_TVS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getJSONObject(it).toTv() }
        }.getOrDefault(emptyList())
    }

    fun save(tvs: List<Tv>) = prefs.edit {
        putString(KEY_TVS, JSONArray(tvs.map { it.toJson() }).toString())
    }

    var selectedId: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(value) = prefs.edit { putString(KEY_SELECTED, value) }

    private companion object {
        const val KEY_TVS = "tvs"
        const val KEY_SELECTED = "selected"
    }
}

/** The last few things typed or spoken to a TV, offered again as one-tap chips. */
class RecentTextStore(context: Context) {
    private val prefs = context.getSharedPreferences("recent_text", Context.MODE_PRIVATE)

    fun load(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    /** Puts [text] first, dropping an older copy of it and anything past the limit. */
    fun add(text: String): List<String> {
        val updated = (listOf(text) + load().filterNot { it.equals(text, ignoreCase = true) }).take(LIMIT)
        prefs.edit { putString(KEY, JSONArray(updated).toString()) }
        return updated
    }

    private companion object {
        const val KEY = "items"
        const val LIMIT = 6
    }
}

private fun Tv.toJson(): JSONObject = JSONObject()
    .put("platform", platform.name)
    .put("host", host)
    .put("name", name)
    .putOpt("token", token)
    .putOpt("certSha256", certSha256)

private fun JSONObject.toTv() = Tv(
    platform = Platform.valueOf(getString("platform")),
    host = getString("host"),
    name = getString("name"),
    token = stringOrNull("token"),
    certSha256 = stringOrNull("certSha256"),
)

private fun JSONObject.stringOrNull(key: String): String? = if (has(key)) getString(key) else null
