package com.airtouch.settings

import android.content.Context
import com.airtouch.control.ActionId
import com.airtouch.control.CustomGesture
import com.airtouch.control.Profile
import org.json.JSONArray
import org.json.JSONObject

/** Guarda no aparelho os perfis do usuário, o perfil ativo e os gestos personalizados. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("airtouch_profiles", Context.MODE_PRIVATE)

    var activeName: String
        get() = prefs.getString("active", Profile.GENERAL) ?: Profile.GENERAL
        set(v) = prefs.edit().putString("active", v).apply()

    /** Perfis embutidos (com as edições salvas) + perfis criados pelo usuário. */
    fun loadProfiles(): List<Profile> {
        val saved = try { JSONObject(prefs.getString("profiles", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        val defaults = Profile.defaults().map { applySaved(it, saved.optJSONObject(it.name)) }
        val userNames = try { JSONArray(prefs.getString("user_names", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        val users = (0 until userNames.length()).mapNotNull { i ->
            val n = userNames.optString(i)
            saved.optJSONObject(n)?.let { applySaved(Profile(n, emptyMap(), false), it) }
        }
        return defaults + users
    }

    private fun applySaved(base: Profile, o: JSONObject?): Profile {
        if (o == null) return base
        val map = HashMap(base.bindings)
        for (k in o.keys()) {
            val id = try { ActionId.valueOf(o.getString(k)) } catch (e: Exception) { null }
            if (id != null) map[k] = id
        }
        return base.copy(bindings = map)
    }

    fun saveProfiles(all: List<Profile>) {
        val o = JSONObject()
        val users = JSONArray()
        for (p in all) {
            val b = JSONObject()
            for ((k, v) in p.bindings) b.put(k, v.name)
            o.put(p.name, b)
            if (!p.builtIn) users.put(p.name)
        }
        prefs.edit().putString("profiles", o.toString()).putString("user_names", users.toString()).apply()
    }

    fun loadGestures(): List<CustomGesture> {
        val arr = try { JSONArray(prefs.getString("gestures", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val o = arr.getJSONObject(i)
                val t = o.getJSONArray("t")
                CustomGesture(
                    o.getString("id"), o.getString("name"),
                    List(t.length()) { j -> t.getDouble(j).toFloat() },
                    o.optDouble("tol", 0.5).toFloat(),
                )
            } catch (e: Exception) { null }
        }
    }

    fun saveGestures(list: List<CustomGesture>) {
        val arr = JSONArray()
        for (g in list) {
            arr.put(JSONObject().put("id", g.id).put("name", g.name)
                .put("tol", g.tolerance.toDouble()).put("t", JSONArray(g.template.map { it.toDouble() })))
        }
        prefs.edit().putString("gestures", arr.toString()).apply()
    }
}
