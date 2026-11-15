package com.halovoid.bunori.data.scheduler.jobs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MaintenanceActivity {
    NONE,
    BACKUP,
    RESTORE,
    INDEXING
}

/**
 * activities not started by the user and does not affect the reading experience directly
 * consists of backup, restore, indexing operations
 *
 * these activity are required to be run one at a time since all of them access the SAF
 * mutex locks are taken on when one o them is started and any other activity can not be started while one activity is running
 */
object MaintenanceScheduler {
    private val _currentActivity = MutableStateFlow(MaintenanceActivity.NONE)
    val currentActivity: StateFlow<MaintenanceActivity> = _currentActivity.asStateFlow()

    val isRunning: Boolean
        get() = _currentActivity.value != MaintenanceActivity.NONE

    fun acquire(activity: MaintenanceActivity): Boolean {
        synchronized(this) {
            if (_currentActivity.value != MaintenanceActivity.NONE) {
                return false
            }
            _currentActivity.value = activity
            return true
        }
    }

    fun release(activity: MaintenanceActivity) {
        synchronized(this) {
            if (_currentActivity.value == activity) {
                _currentActivity.value = MaintenanceActivity.NONE
            }
        }
    }
}
