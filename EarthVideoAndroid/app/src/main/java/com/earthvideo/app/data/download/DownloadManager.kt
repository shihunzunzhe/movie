package com.earthvideo.app.data.download

import android.content.Context
import android.content.SharedPreferences
import com.earthvideo.app.data.model.Movie
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

data class DownloadTask(
    val movie: Movie,
    val episode: Int,
    val dirName: String,
    val state: String,          // queued | downloading | done | failed
    val progress: Int = 0,      // 0..100
    val sizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val error: String = ""
) {
    companion object {
        const val STATE_QUEUED = "queued"
        const val STATE_DOWNLOADING = "downloading"
        const val STATE_DONE = "done"
        const val STATE_FAILED = "failed"
    }
}

/**
 * Application-wide download manager.
 *
 * - Batch enqueue: episodes are added to a FIFO queue processed by ONE worker
 *   coroutine at a time (concurrency = 1), so bandwidth is never split across
 *   several concurrent downloads.
 * - Completed entries persist across restarts.
 */
object DownloadManager {

    private const val PREFS = "earthvideo_downloads"
    private const val KEY_DONE = "done_tasks"

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var rootDir: File
    private lateinit var prefs: SharedPreferences

    // Single serializer worker: batches download one episode at a time.
    private data class QueuedJob(val task: DownloadTask, val url: String)
    private val queue = ArrayDeque<QueuedJob>()
    private var workerRunning = false

    private val downloader = HlsDownloader(
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    )

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _running = mutableSetOf<String>()

    fun init(context: Context) {
        if (this::rootDir.isInitialized) return
        rootDir = File(context.filesDir, "downloads").apply { mkdirs() }
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _tasks.value = loadDoneTasks()
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Enqueue a batch of episodes. [episodes] = list of (episodeNumber, playUrl). */
    fun enqueueBatch(movie: Movie, episodes: List<Pair<Int, String>>) {
        val seen = _tasks.value.map { it.dirName }.toSet()
        val valid = episodes.filter { (_, url) ->
            url.isNotBlank() && !url.startsWith("file://")
        }
        if (valid.isEmpty()) return

        for ((ep, url) in valid) {
            val dirName = dirNameFor(movie.id, ep)
            if (dirName in seen || dirName in _running) continue
            val task = DownloadTask(
                movie = movie,
                episode = ep,
                dirName = dirName,
                state = DownloadTask.STATE_QUEUED
            )
            seen.plus(dirName)
            _running.add(dirName)
            upsert(task)
            queue.addLast(QueuedJob(task, url))
        }
        ensureWorker()
    }

    fun enqueue(movie: Movie, episode: Int, url: String) {
        enqueueBatch(movie, listOf(episode to url))
    }

    /** Cancel a queued/running download and remove its (partial) files. */
    fun cancel(dirName: String) {
        _running.remove(dirName)
        queue.removeAll { it.task.dirName == dirName }
        File(rootDir, dirName).deleteRecursively()
        _tasks.value = _tasks.value.filterNot { it.dirName == dirName }
        persistDone(mapDone())
    }

    fun delete(dirName: String) = cancel(dirName)

    fun clearAll() {
        _running.clear()
        queue.clear()
        rootDir.listFiles()?.forEach { it.deleteRecursively() }
        _tasks.value = emptyList()
        prefs.edit().remove(KEY_DONE).apply()
    }

    fun isQueuedOrRunning(dirName: String): Boolean = _running.contains(dirName)

    fun localIndexPath(dirName: String): String? {
        val f = File(rootDir, "$dirName/index.m3u8")
        return if (f.exists() && f.length() > 0) f.absolutePath else null
    }

    fun movieForDir(dirName: String): Movie? =
        _tasks.value.firstOrNull { it.dirName == dirName }?.movie

    fun episodeForDir(dirName: String): Int =
        _tasks.value.firstOrNull { it.dirName == dirName }?.episode ?: 1

    // ------------------------------------------------------------------
    // Worker: processes the FIFO queue strictly serially (concurrency = 1)
    // ------------------------------------------------------------------

    private fun ensureWorker() {
        if (workerRunning) return
        workerRunning = true
        scope.launch {
            try {
                while (queue.isNotEmpty()) {
                    if (queue.isEmpty()) break
                    val job = queue.removeFirst()
                    if (!_running.contains(job.task.dirName)) continue
                    runJob(job)
                }
            } finally {
                workerRunning = false
            }
        }
    }

    private suspend fun runJob(job: QueuedJob) {
        val task = job.task
        upsert(task.copy(state = DownloadTask.STATE_DOWNLOADING, progress = 0))
        val dest = File(rootDir, task.dirName)
        try {
            val result = downloader.download(job.url, dest) { done, total ->
                if (!_running.contains(task.dirName)) {
                    false // cancelled
                } else {
                    val p = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 99) else 0
                    upsert(task.copy(state = DownloadTask.STATE_DOWNLOADING, progress = p))
                    true
                }
            }
            if (_running.contains(task.dirName)) {
                upsert(
                    task.copy(
                        state = DownloadTask.STATE_DONE,
                        progress = 100,
                        sizeBytes = result.bytes
                    )
                )
                persistDone(mapDone())
            } else {
                // cancelled mid-download
                File(rootDir, task.dirName).deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            upsert(
                task.copy(
                    state = DownloadTask.STATE_FAILED,
                    error = e.message ?: "下载失败",
                    progress = 0
                )
            )
        } finally {
            _running.remove(task.dirName)
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun dirNameFor(movieId: String, episode: Int): String {
        val safe = movieId.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        return "${safe}__ep${episode}"
    }

    private fun upsert(task: DownloadTask) {
        _tasks.value = (_tasks.value.filterNot { it.dirName == task.dirName } + task)
            .sortedByDescending { it.createdAt }
    }

    private fun mapDone(): List<DownloadTask> =
        _tasks.value.filter { it.state == DownloadTask.STATE_DONE }

    private fun loadDoneTasks(): List<DownloadTask> {
        val json = prefs.getString(KEY_DONE, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DownloadTask>>() {}.type
            val saved: List<DownloadTask> = gson.fromJson(json, type) ?: emptyList()
            saved.filter { l ->
                val f = File(rootDir, "${l.dirName}/index.m3u8")
                f.exists() && f.length() > 0
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistDone(list: List<DownloadTask>) {
        prefs.edit().putString(KEY_DONE, gson.toJson(list)).apply()
    }
}