package com.callflow.app.domain.usecase

import com.callflow.app.core.model.DispositionInput

sealed interface DispositionValidation {
    data object Valid : DispositionValidation
    data class Invalid(val message: String) : DispositionValidation
}

object DispositionValidator {
    fun validate(input: DispositionInput): DispositionValidation = when {
        input.disposition.requiresNote && input.note.isBlank() -> DispositionValidation.Invalid("A note is required for this result")
        input.disposition.requiresFollowUp && input.followUpAt == null -> DispositionValidation.Invalid("Choose a follow-up date and time")
        else -> DispositionValidation.Valid
    }
}
