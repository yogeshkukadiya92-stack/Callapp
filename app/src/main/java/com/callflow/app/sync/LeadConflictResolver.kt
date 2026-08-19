package com.callflow.app.sync

sealed interface VersionResolution {
    data object IgnoreStaleServerValue : VersionResolution
    data object ApplyServerValue : VersionResolution
    data object RecordConflict : VersionResolution
}

object LeadConflictResolver {
    fun resolve(localVersion: Long?, serverVersion: Long, hasPendingLocalMutation: Boolean): VersionResolution = when {
        localVersion == null -> VersionResolution.ApplyServerValue
        serverVersion <= localVersion -> VersionResolution.IgnoreStaleServerValue
        hasPendingLocalMutation -> VersionResolution.RecordConflict
        else -> VersionResolution.ApplyServerValue
    }
}
