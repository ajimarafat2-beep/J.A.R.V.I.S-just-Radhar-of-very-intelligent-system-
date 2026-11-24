package com.example.queue

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.example.connector.UniversalConnector
import com.example.security.JarvisSecurityEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OfflineTaskMetrics(
    val pendingCount: Int = 0,
    val runningCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val totalCount: Int = 0
)

/**
 * Manages the local persistent Room task queue, priority scheduling, retry policies,
 * and automatic synchronization when internet connectivity returns.
 */
class OfflineTaskManager(
    context: Context,
    private val connector: UniversalConnector,
    private val onExecuteQueuedAiTask: (suspend (OfflineTaskEntity) -> String)? = null,
    private val onTaskAutoCompleted: (OfflineTaskEntity) -> Unit
) {
    private val appContext = context.applicationContext
    private val dao = JarvisLocalDatabase.getInstance(appContext).offlineTaskDao()
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()
    private var callbackRegistered = false

    @Volatile
    var offlineIsolationEnabled: Boolean = false

    private val _isOnline = MutableStateFlow(checkCurrentInternet())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    val tasks: StateFlow<List<OfflineTaskEntity>> = dao.observeAllTasks()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val metrics: StateFlow<OfflineTaskMetrics> = tasks
        .map { list ->
            OfflineTaskMetrics(
                pendingCount = list.count { it.status == TaskStatus.PENDING_NETWORK },
                runningCount = list.count { it.status == TaskStatus.RUNNING },
                completedCount = list.count { it.status == TaskStatus.COMPLETED },
                failedCount = list.count { it.status == TaskStatus.FAILED },
                totalCount = list.size
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = OfflineTaskMetrics()
        )

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateOnlineState(checkCurrentInternet())
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasNet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            updateOnlineState(hasNet)
        }

        override fun onLost(network: Network) {
            updateOnlineState(checkCurrentInternet())
        }
    }

    init {
        registerConnectivityCallback()
    }

    fun canExecuteOnlineNow(): Boolean = _isOnline.value && !offlineIsolationEnabled

    suspend fun enqueuePendingTask(
        prompt: String,
        taskType: String,
        targetValue: String,
        priority: Int = TaskPriority.NORMAL,
        intentName: String = "UNKNOWN",
        confidence: Float = 0f
    ): OfflineTaskEntity {
        val entity = OfflineTaskEntity(
            prompt = prompt,
            taskType = taskType,
            targetValue = targetValue,
            status = TaskStatus.PENDING_NETWORK,
            priority = priority,
            intentName = intentName,
            confidence = confidence
        )
        val id = dao.insertTask(entity)
        return entity.copy(id = id)
    }

    suspend fun recordCompletedOfflineTask(
        prompt: String,
        targetValue: String,
        summary: String,
        intentName: String = "OFFLINE_ACTION",
        confidence: Float = 1.0f
    ) {
        dao.insertTask(
            OfflineTaskEntity(
                prompt = prompt,
                taskType = TaskType.OFFLINE_ACTION,
                targetValue = targetValue,
                status = TaskStatus.COMPLETED,
                priority = TaskPriority.NORMAL,
                intentName = intentName,
                confidence = confidence,
                resultSummary = summary,
                completedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun retryTask(id: Long) {
        val existing = dao.getTaskById(id) ?: return
        dao.updateTask(
            existing.copy(
                status = TaskStatus.PENDING_NETWORK,
                retryCount = 0,
                resultSummary = "Re-queued manually for next network sync."
            )
        )
        triggerPendingFlush()
    }

    suspend fun cancelTask(id: Long) {
        val existing = dao.getTaskById(id) ?: return
        dao.updateTask(
            existing.copy(
                status = TaskStatus.CANCELLED,
                resultSummary = "Cancelled by user."
            )
        )
    }

    suspend fun promotePriority(id: Long) {
        val existing = dao.getTaskById(id) ?: return
        val nextPriority = when (existing.priority) {
            TaskPriority.LOW -> TaskPriority.NORMAL
            TaskPriority.NORMAL -> TaskPriority.HIGH
            else -> TaskPriority.LOW
        }
        dao.updateTask(existing.copy(priority = nextPriority))
    }

    fun triggerPendingFlush() {
        if (!canExecuteOnlineNow()) return
        scope.launch { flushPendingTasks() }
    }

    suspend fun deleteTask(id: Long) {
        dao.deleteTaskById(id)
    }

    suspend fun clearCompleted() {
        dao.clearTasksByStatus(TaskStatus.COMPLETED)
    }

    private fun updateOnlineState(online: Boolean) {
        _isOnline.value = online
        if (online && !offlineIsolationEnabled) {
            scope.launch { flushPendingTasks() }
        }
    }

    private suspend fun flushPendingTasks() = flushMutex.withLock {
        if (!canExecuteOnlineNow()) return@withLock
        val pending = dao.getTasksByStatus(TaskStatus.PENDING_NETWORK)
        for (task in pending) {
            if (!canExecuteOnlineNow()) break
            val running = task.copy(status = TaskStatus.RUNNING)
            dao.updateTask(running)

            try {
                val summary = executeNetworkTask(running)
                val done = running.copy(
                    status = TaskStatus.COMPLETED,
                    resultSummary = summary,
                    completedAtMillis = System.currentTimeMillis()
                )
                dao.updateTask(done)
                onTaskAutoCompleted(done)
            } catch (e: Exception) {
                val nextAttempt = running.retryCount + 1
                val exhausted = nextAttempt >= running.maxRetries
                dao.updateTask(
                    running.copy(
                        status = if (exhausted) TaskStatus.FAILED else TaskStatus.PENDING_NETWORK,
                        retryCount = nextAttempt,
                        resultSummary = if (exhausted) {
                            "Failed after $nextAttempt attempts: ${e.localizedMessage ?: "Error"}"
                        } else {
                            "Attempt $nextAttempt/${running.maxRetries} failed — will retry automatically."
                        }
                    )
                )
            }
        }
    }

    private suspend fun executeNetworkTask(task: OfflineTaskEntity): String {
        return when (task.taskType) {
            TaskType.WEB_SEARCH -> {
                connector.openWebSearch(task.targetValue)
                "Internet restored — opened web search for \"${task.targetValue}\"."
            }
            TaskType.WEB_CONNECT -> {
                val saved = connector.saveWebsite("", task.targetValue)
                val report = JarvisSecurityEngine.inspectWebsite(saved.url, isOfflineIsolated = false)
                if (report.safeToOpen) {
                    connector.openWebsite(report.normalizedUrl)
                    "Internet restored — verified TLS (${report.riskLevel.name}) and connected to ${report.normalizedUrl}."
                } else {
                    "Internet restored — blocked auto-launch of ${report.host}: ${report.findings.joinToString(", ")}."
                }
            }
            TaskType.WEBSITE_AUDIT -> {
                val report = JarvisSecurityEngine.inspectWebsite(task.targetValue, isOfflineIsolated = false)
                "Website audit complete for ${report.host}: ${report.riskLevel.name} (HTTP ${report.httpStatusCode ?: "N/A"})."
            }
            TaskType.AI_QUERY,
            TaskType.WEB_BUILD,
            TaskType.APP_DISCOVERY,
            TaskType.PRICE_FINDER -> {
                onExecuteQueuedAiTask?.invoke(task)
                    ?: "Internet restored — ready to execute ${task.taskType} for \"${task.targetValue}\"."
            }
            else -> "Completed task: ${task.prompt}"
        }
    }

    private fun checkCurrentInternet(): Boolean {
        val cm = connectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun registerConnectivityCallback() {
        if (callbackRegistered) return
        val cm = connectivityManager ?: return
        try {
            cm.registerDefaultNetworkCallback(networkCallback)
            callbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register default network callback", e)
        }
    }

    fun release() {
        if (callbackRegistered) {
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
            callbackRegistered = false
        }
        scope.cancel()
    }

    companion object {
        private const val TAG = "OfflineTaskManager"
    }
}
