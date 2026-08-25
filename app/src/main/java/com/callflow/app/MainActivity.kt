package com.callflow.app

import android.os.Bundle
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.callflow.app.ui.CallFlowApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.callflow.app.telecom.CallIntegrationManager
import com.callflow.app.core.model.Outcome
import com.callflow.app.ui.theme.CallFlowTheme
import com.callflow.app.telecom.CallLogImporter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var callIntegration: CallIntegrationManager
    @Inject lateinit var callLogImporter: CallLogImporter
    private var dialNumber by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialNumber = intent.takeIf { it.action == Intent.ACTION_DIAL }?.data?.schemeSpecificPart.orEmpty().takeIf(String::isNotEmpty)
        enableEdgeToEdge()
        setContent {
            val externalDial = intent.action == Intent.ACTION_DIAL
            if (externalDial) CallFlowTheme { DialEntryScreen(dialNumber.orEmpty(), onCall = callIntegration::initiateCall, onClose = ::finish) } else CallFlowApp()
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); dialNumber = intent.data?.schemeSpecificPart }

    override fun onResume() {
        super.onResume()
        // Reconcile only confirmed Android call-log rows. Opening a dial pad alone never creates history.
        lifecycleScope.launch { callLogImporter.importNewCalls() }
    }
}

@androidx.compose.runtime.Composable
private fun DialEntryScreen(initial: String, onCall: (String) -> Outcome<Unit>, onClose: () -> Unit) {
    var number by androidx.compose.runtime.saveable.rememberSaveable(initial) { mutableStateOf(initial) }
    var error by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("CallFlow Phone", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(number, { number = it.filter { char -> char.isDigit() || char == '+' || char == ' ' || char == '-' } }, label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { if (number.filter(Char::isDigit).length < 7) error = "Enter a valid phone number" else when (onCall(number)) { is Outcome.Success -> onClose(); is Outcome.Failure -> error = "Unable to place this call" } }, modifier = Modifier.fillMaxWidth()) { Text("CALL") }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("CANCEL") }
    }
}
