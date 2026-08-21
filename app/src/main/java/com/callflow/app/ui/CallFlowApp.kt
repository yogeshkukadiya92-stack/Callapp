package com.callflow.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.callflow.app.core.model.DailyMetrics
import com.callflow.app.core.model.Lead
import com.callflow.app.ui.home.HomeViewModel
import com.callflow.app.ui.leads.LeadDetailScreen
import com.callflow.app.ui.leads.LeadsScreen
import com.callflow.app.ui.calling.CallingScreen
import com.callflow.app.ui.calling.DispositionScreen
import com.callflow.app.ui.operations.CallsScreen
import com.callflow.app.ui.operations.FollowUpsScreen
import com.callflow.app.ui.operations.MoreScreen
import com.callflow.app.ui.auth.AppSessionViewModel
import com.callflow.app.ui.auth.LoginScreen
import com.callflow.app.ui.auth.DeviceAccessScreen
import com.callflow.app.core.model.DeviceStatus
import com.callflow.app.core.model.SessionState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import com.callflow.app.ui.onboarding.OnboardingScreen
import com.callflow.app.ui.theme.CallFlowTheme
import com.callflow.app.ui.theme.ActivityChart
import com.callflow.app.ui.theme.Emerald
import com.callflow.app.ui.theme.Indigo
import com.callflow.app.ui.theme.KpiCard
import com.callflow.app.ui.theme.PremiumCard
import com.callflow.app.ui.theme.SectionHeader
import com.callflow.app.ui.theme.Slate

private data class Destination(val route: String, val label: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("home", "Home", Icons.Outlined.Home), Destination("leads", "Leads", Icons.Outlined.People),
    Destination("calls", "Calls", Icons.Outlined.Call), Destination("followups", "Follow-ups", Icons.Outlined.Schedule),
    Destination("more", "More", Icons.Outlined.MoreHoriz),
)

@Composable
fun CallFlowApp(sessionViewModel: AppSessionViewModel = hiltViewModel()) = CallFlowTheme {
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val checkingDevice by sessionViewModel.checkingDevice.collectAsStateWithLifecycle()
    val deviceError by sessionViewModel.deviceError.collectAsStateWithLifecycle()
    val onboardingComplete by sessionViewModel.onboardingComplete.collectAsStateWithLifecycle()
    if (onboardingComplete == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return@CallFlowTheme
    }
    if (onboardingComplete == false) {
        OnboardingScreen(sessionViewModel::completeOnboarding)
        return@CallFlowTheme
    }
    when (session) {
        SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        SessionState.SignedOut -> LoginScreen()
        is SessionState.SignedIn -> {
            val signedIn = session as SessionState.SignedIn
            if (signedIn.deviceStatus == DeviceStatus.ACTIVE) MainNavigation()
            else DeviceAccessScreen(signedIn.deviceStatus, checkingDevice, deviceError, sessionViewModel::checkDevice, sessionViewModel::logout)
        }
    }
}

@Composable
private fun MainNavigation() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    Scaffold(bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            destinations.forEach { item ->
                NavigationBarItem(
                    selected = backStack?.destination?.route == item.route,
                    onClick = { nav.navigate(item.route) { launchSingleTop = true } },
                    icon = { Icon(item.icon, null) }, label = { Text(item.label) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                )
            }
        }
    }) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(onStartCalling = { nav.navigate("leads") }) }
            composable("leads") { LeadsScreen(onLeadClick = { nav.navigate("lead/$it") }) }
            composable("lead/{leadId}", arguments = listOf(navArgument("leadId") { type = NavType.StringType })) { LeadDetailScreen(onCall = { nav.navigate("call/$it") }) }
            composable("call/{leadId}", arguments = listOf(navArgument("leadId") { type = NavType.StringType })) { CallingScreen(onCallStarted = { leadId, callId -> nav.navigate("disposition/$leadId/$callId") }) }
            composable("disposition/{leadId}/{callId}", arguments = listOf(navArgument("leadId") { type = NavType.StringType }, navArgument("callId") { type = NavType.StringType })) { DispositionScreen(onSaved = { nav.navigate("calls") { popUpTo("home") } }, onSaveNext = { nav.navigate("leads") { popUpTo("home") } }) }
            composable("calls") { CallsScreen() }
            composable("followups") { FollowUpsScreen() }
            composable("more") { MoreScreen() }
        }
    }
}

@Composable
private fun HomeScreen(onStartCalling: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Good morning, Yogesh", style = MaterialTheme.typography.headlineMedium); Text("Ready to make an impact?", color = Slate) }
                FloatingActionButton(onClick = {}, modifier = Modifier.size(44.dp), containerColor = MaterialTheme.colorScheme.surface) { Icon(Icons.Outlined.Notifications, null, tint = Indigo) }
            }
        }
        item { MetricsGrid(state.metrics) }
        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Call activity", "7 days")
                    ActivityChart(listOf(12f, 18f, 16f, 25f, 20f, 29f, 32f))
                }
            }
        }
        item { Button(onClick = onStartCalling, modifier = Modifier.fillMaxWidth().height(58.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) { Icon(Icons.Outlined.Call, null); Text("  START CALLING") } }
        item { SectionHeader("Priority follow-ups", "View all") }
        if (state.queue.isEmpty()) item { Text("Your queue is clear.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.queue.take(4), key = Lead::id) { LeadCard(it) }
    }
}

@Composable
private fun MetricsGrid(metrics: DailyMetrics) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KpiCard("Calls", metrics.calls.toString(), Indigo, Modifier.weight(1f)); KpiCard("Connected", metrics.connected.toString(), Emerald, Modifier.weight(1f)); KpiCard("Follow-ups", metrics.followUpsDue.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
    }
}

@Composable private fun LeadCard(lead: Lead) = PremiumCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(lead.name, fontWeight = FontWeight.SemiBold); lead.company?.let { Text(it, color = Slate) }; Spacer(Modifier.height(4.dp)); Text(lead.displayPhone, color = Indigo) }; FloatingActionButton(onClick = {}, modifier = Modifier.size(42.dp), containerColor = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Outlined.Call, null, tint = Indigo) } } }
@Composable private fun PlaceholderScreen(title: String, body: String) = Column(Modifier.fillMaxSize().padding(24.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(12.dp)); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant) }
