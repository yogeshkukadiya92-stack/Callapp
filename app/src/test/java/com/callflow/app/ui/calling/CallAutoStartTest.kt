package com.callflow.app.ui.calling
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test
class CallAutoStartTest {
 @Test fun backAndRecompositionCannotRepeatAutoCall() {
  val state = SavedStateHandle()
  assertTrue(consumeCallAutoStart(state))
  assertFalse(consumeCallAutoStart(state))
  val restored = SavedStateHandle(mapOf("auto_call_started" to state.get<Boolean>("auto_call_started")))
  assertFalse(consumeCallAutoStart(restored))
 }
 @Test fun newNavigationEntryAllowsOneExplicitNewCall() {
  assertTrue(consumeCallAutoStart(SavedStateHandle()))
  assertTrue(consumeCallAutoStart(SavedStateHandle()))
 }
}
