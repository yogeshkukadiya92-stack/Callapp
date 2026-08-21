package com.callflow.app.ui.auth

import com.callflow.app.BuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Call, null, tint = MaterialTheme.colorScheme.primary)
        Text("CallFlow", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Sign in to your business calling workspace", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(state.identity, viewModel::identity, label = { Text("Mobile number or email") }, singleLine = true, enabled = !state.loading, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(state.password, viewModel::password, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !state.loading, modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
        Button(onClick = viewModel::login, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 12.dp)) { if (state.loading) CircularProgressIndicator(modifier = Modifier.height(20.dp)) else Text("SIGN IN") }
        Text(
            if (BuildConfig.USE_FAKE_BACKEND) "Demo mode: use any email/mobile and password."
            else "Connected to Coach For Life CRM. Use your Sales Access email/mobile and password.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}
