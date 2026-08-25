package com.callflow.app.domain.usecase

import com.callflow.app.core.model.DispositionInput
import com.callflow.app.core.model.DispositionOption
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Test

class DispositionValidatorTest {
    @Test fun enforcesServerConfiguredRequirements() {
        val option = DispositionOption("follow", "FOLLOW_UP", "Follow-up", requiresNote = true, requiresFollowUp = true, targetStageId = null)
        assertTrue(DispositionValidator.validate(DispositionInput("call", "lead", option, "", null)) is DispositionValidation.Invalid)
        assertTrue(DispositionValidator.validate(DispositionInput("call", "lead", option, "Pricing requested", Instant.now().plusSeconds(60))) is DispositionValidation.Valid)
    }
    @Test fun rejectsFollowUpsScheduledInThePast() {
        val option = DispositionOption("callback", "CALLBACK_REQUESTED", "Callback requested", false, true, null)
        assertTrue(DispositionValidator.validate(DispositionInput("call", "lead", option, "", Instant.now().minusSeconds(60))) is DispositionValidation.Invalid)
    }
}
