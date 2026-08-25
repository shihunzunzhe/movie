package com.earthvideo.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.earthvideo.app.data.model.Movie
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class LocalStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("earthvideo_local", Context.MODE_PRIVATE)
    private val gson = Gson()

    private companion object {
        private const val KEY_HISTORY = "history_movies"
        private const val KEY_FAVORITES = "favorite_movies"
        private const val KEY_NICKNAME = "user_nickname"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_KEEP_SCREEN = "keep_screen_on"
        private const val KEY_COMMENTS = "local_comments"
        private const val MAX_HISTORY = 200
    }

    // -------------------------------------------------------------------------
    // History
    // -------------------------------------------------------------------------

    fun getHistoryMovies(): List<Movie> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Movie>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHistoryMovie(movie: Movie) {
        val list = getHistoryMovies().toMutableList()
        // Remove duplicate if exists
        list.removeAll { it.id == movie.id }
        // Add to front
        list.add(0, movie)
        // Cap at max
        val trimmed = if (list.size > MAX_HISTORY) list.take(MAX_HISTORY) else list
        prefs.edit().putString(KEY_HISTORY, gson.toJson(trimmed)).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    fun getHistoryCount(): Int = getHistoryMovies().size

    // -------------------------------------------------------------------------
    // Favorites
    // -------------------------------------------------------------------------

    fun getFavoriteMovies(): List<Movie> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Movie>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toggleFavorite(movie: Movie): Boolean {
        val list = getFavoriteMovies().toMutableList()
        val existing = list.find { it.id == movie.id }
        return if (existing != null) {
            list.removeAll { it.id == movie.id }
            prefs.edit().putString(KEY_FAVORITES, gson.toJson(list)).apply()
            false // removed
        } else {
            list.add(0, movie)
            prefs.edit().putString(KEY_FAVORITES, gson.toJson(list)).apply()
            true // added
        }
    }

    fun isFavorite(movieId: String): Boolean {
        return getFavoriteMovies().any { it.id == movieId }
    }

    fun getFavoriteCount(): Int = getFavoriteMovies().size

    // -------------------------------------------------------------------------
    // User nickname (local login)
    // -------------------------------------------------------------------------

    fun getNickname(): String = prefs.getString(KEY_NICKNAME, null) ?: ""

    fun setNickname(name: String) {
        prefs.edit().putString(KEY_NICKNAME, name).apply()
    }

    fun isLoggedIn(): Boolean = getNickname().isNotEmpty()

    fun logout() {
        prefs.edit().remove(KEY_NICKNAME).apply()
    }

    // -------------------------------------------------------------------------
    // Settings persistence
    // -------------------------------------------------------------------------

    fun isWifiOnly(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, true)

    fun setWifiOnly(v: Boolean) = prefs.edit().putBoolean(KEY_WIFI_ONLY, v).apply()

    fun isKeepScreenOn(): Boolean = prefs.getBoolean(KEY_KEEP_SCREEN, true)

    fun setKeepScreenOn(v: Boolean) = prefs.edit().putBoolean(KEY_KEEP_SCREEN, v).apply()

    // -------------------------------------------------------------------------
    // Local comments (per movie, simple JSON persistence)
    // -------------------------------------------------------------------------

    data class Comment(val movieId: String, val text: String, val time: Long = System.currentTimeMillis())

    fun getComments(movieId: String): List<Comment> {
        val json = prefs.getString(KEY_COMMENTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Comment>>() {}.type
            val all: List<Comment> = gson.fromJson(json, type) ?: emptyList()
            all.filter { it.movieId == movieId }.sortedByDescending { it.time }
        } catch (e: Exception) { emptyList() }
    }

    fun addComment(movieId: String, text: String) {
        val all = try {
            val json = prefs.getString(KEY_COMMENTS, null) ?: "[]"
            val type = object : TypeToken<List<Comment>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf<Comment>()
        } catch (e: Exception) { mutableListOf<Comment>() }
        (all as MutableList).add(Comment(movieId, text))
        prefs.edit().putString(KEY_COMMENTS, gson.toJson(all)).apply()
    }
}
