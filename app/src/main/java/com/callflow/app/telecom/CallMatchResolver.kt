package com.callflow.app.telecom

/** Refuses ambiguous associations so call activity is never attributed to the wrong lead or attempt. */
object CallMatchResolver {
    fun uniqueLeadId(candidateIds: List<String>): String? = candidateIds.distinct().singleOrNull()
    fun uniqueOpenCallId(candidateIds: List<String>): String? = candidateIds.distinct().singleOrNull()
}
