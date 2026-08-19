package com.callflow.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class LeadConflictResolverTest {
    @Test fun newRemoteLeadIsApplied() = assertEquals(VersionResolution.ApplyServerValue, LeadConflictResolver.resolve(null, 1, false))
    @Test fun staleServerValueIsIgnored() = assertEquals(VersionResolution.IgnoreStaleServerValue, LeadConflictResolver.resolve(4, 4, false))
    @Test fun newerServerValueAppliesWithoutLocalMutation() = assertEquals(VersionResolution.ApplyServerValue, LeadConflictResolver.resolve(4, 5, false))
    @Test fun newerServerValueConflictsWithPendingMutation() = assertEquals(VersionResolution.RecordConflict, LeadConflictResolver.resolve(4, 5, true))
}
