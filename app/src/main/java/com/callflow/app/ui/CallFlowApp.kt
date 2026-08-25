package com.callflow.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.LinearProgressIndicator
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
import com.callflow.app.core.model.PriorityLead
import com.callflow.app.core.model.QueuePriority
import com.callflow.app.ui.home.HomeViewModel
import com.callflow.app.ui.leads.LeadDetailScreen
import com.callflow.app.ui.leads.LeadsScreen
import com.callflow.app.ui.calling.CallingScreen
import com.callflow.app.ui.calling.DispositionScreen
import com.callflow.app.ui.operations.CallsScreen
import com.callflow.app.ui.operations.FollowUpsScreen
import com.callflow.app.ui.operations.MoreScreen
import com.callflow.app.ui.operations.ProfileScreen
import com.callflow.app.ui.operations.ReportsScreen
import com.callflow.app.ui.operations.SettingsScreen
import com.callflow.app.ui.operations.TeamContentScreen
import com.callflow.app.ui.auth.AppSessionViewModel
import com.callflow.app.ui.auth.LoginScreen
import com.callflow.app.ui.auth.DeviceAccessScreen
import com.callflow.app.core.model.DeviceStatus
import com.callflow.app.core.model.SessionState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.callflow.app.ui.onboarding.OnboardingScreen
import com.callflow.app.ui.theme.CallFlowTheme
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
            if (signedIn.deviceStatus == DeviceStatus.ACTIVE) MainNavigation(signedIn.employeeName, signedIn.employeePhone)
            else DeviceAccessScreen(signedIn.deviceStatus, checkingDevice, deviceError, sessionViewModel::checkDevice, sessionViewModel::logout)
        }
    }
}

@Composable
private fun MainNavigation(employeeName: String, employeePhone: String?) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val topLevel = destinations.any { it.route == currentRoute } || currentRoute == "connected-calls"
    fun navigateTopLevel(route: String) { nav.navigate(route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }
    Scaffold(bottomBar = {
        if (topLevel) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            destinations.forEach { item ->
                NavigationBarItem(
                    selected = currentRoute == item.route || (item.route == "calls" && currentRoute == "connected-calls"),
                    onClick = { navigateTopLevel(item.route) },
                    icon = { Icon(item.icon, null) }, label = { Text(item.label) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                )
            }
        }
    }) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(onStartCalling = { navigateTopLevel("leads") }, onLeadClick = { nav.navigate("lead/$it") }, onCallLead = { nav.navigate("call/$it") }, onViewCalls = { navigateTopLevel("calls") }, onViewConnectedCalls = { navigateTopLevel("connected-calls") }, onViewFollowUps = { navigateTopLevel("followups") }) }
            composable("leads") { LeadsScreen(onLeadClick = { nav.navigate("lead/$it") }) }
            composable("lead/{leadId}", arguments = listOf(navArgument("leadId") { type = NavType.StringType })) { LeadDetailScreen(onBack = { nav.navigateUp() }, onCall = { nav.navigate("call/$it") }) }
            composable("call/{leadId}", arguments = listOf(navArgument("leadId") { type = NavType.StringType })) { CallingScreen(onBack = { nav.navigateUp() }, onCallStarted = { leadId, callId -> nav.navigate("disposition/$leadId/$callId") }) }
            composable("disposition/{leadId}/{callId}", arguments = listOf(navArgument("leadId") { type = NavType.StringType }, navArgument("callId") { type = NavType.StringType })) { DispositionScreen(onBack = { nav.navigateUp() }, onSaved = { nav.navigate("calls") { popUpTo("home") } }, onSaveNext = { nav.navigate("leads") { popUpTo("home") } }) }
            composable("calls") { CallsScreen() }
            composable("connected-calls") { CallsScreen(initialFilter = "Connected") }
            composable("followups") { FollowUpsScreen(onOpenLead = { nav.navigate("lead/$it") }, onCallLead = { nav.navigate("call/$it") }) }
            composable("more") { MoreScreen(employeeName = employeeName, employeePhone = employeePhone, onProfile = { nav.navigate("profile") }, onReports = { nav.navigate("reports") }, onTeamContent = { nav.navigate("team-content") }, onSettings = { nav.navigate("settings") }) }
            composable("profile") { ProfileScreen(employeeName, employeePhone, onBack = { nav.navigateUp() }) }
            composable("reports") { ReportsScreen(onBack = { nav.navigateUp() }) }
            composable("team-content") { TeamContentScreen(onBack = { nav.navigateUp() }) }
            composable("settings") { SettingsScreen(onBack = { nav.navigateUp() }) }
        }
    }
}

@Composable
private fun HomeScreen(onStartCalling: () -> Unit, onLeadClick: (String) -> Unit, onCallLead: (String) -> Unit, onViewCalls: () -> Unit, onViewConnectedCalls: () -> Unit, onViewFollowUps: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column { Text("Your call workspace", style = MaterialTheme.typography.headlineMedium); Text("Today’s progress and priority queue", color = Slate) }
        }
        item { MetricsGrid(state.metrics, onViewCalls, onViewConnectedCalls, onViewFollowUps) }
        item { DailyTargetCard(state.performance, state.performanceLoading, viewModel::refreshPerformance) }
        item { Button(onClick = onStartCalling, modifier = Modifier.fillMaxWidth().height(58.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) { Icon(Icons.Outlined.Call, null); Text("  START CALLING") } }
        item { SectionHeader("Priority queue", "View follow-ups", onViewFollowUps) }
        if (state.queue.isEmpty()) item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Your queue is clear", fontWeight = FontWeight.SemiBold); Text("Assigned leads and due follow-ups will appear here after sync.", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        items(state.queue.take(4), key = { it.lead.id }) { item -> LeadCard(item, { onLeadClick(item.lead.id) }, { onCallLead(item.lead.id) }) }
    }
}

@Composable
private fun DailyTargetCard(performance: com.callflow.app.data.remote.TodayPerformanceResponse?, loading: Boolean, onRefresh: () -> Unit) {
    PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Today’s targets", when { loading -> "Loading…"; performance != null -> "Live from dashboard"; else -> "Dashboard unavailable" })
        if (performance == null) {
            Text("Target data is temporarily unavailable. Your local call totals remain visible above.", color = Slate)
            androidx.compose.material3.OutlinedButton(onClick = onRefresh) { Text("TRY AGAIN") }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Calls", fontWeight = FontWeight.SemiBold); Text("${performance.calls} / ${performance.callTarget}", fontWeight = FontWeight.Bold) }
            LinearProgressIndicator(progress = { performance.callTargetPercent / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Connected", fontWeight = FontWeight.SemiBold); Text("${performance.connected} / ${performance.connectedTarget}", fontWeight = FontWeight.Bold) }
            LinearProgressIndicator(progress = { performance.connectedTargetPercent / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Emerald)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${performance.connectionRate}% connection", color = Slate); if (performance.leaderboardSize > 0) Text("Rank #${performance.leaderboardRank} of ${performance.leaderboardSize}", color = Indigo, fontWeight = FontWeight.Bold) }
            Text("${performance.conversions} converted · ${performance.followUpsDue} follow-ups due", color = Slate)
        }
    } }
}

@Composable
private fun MetricsGrid(metrics: DailyMetrics, onViewCalls: () -> Unit, onViewConnectedCalls: () -> Unit, onViewFollowUps: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KpiCard("Calls", metrics.calls.toString(), Indigo, Modifier.weight(1f).semantics { role = Role.Button; contentDescription = "${metrics.calls} calls today. Open call history" }.clickable(role = Role.Button, onClick = onViewCalls))
        KpiCard("Connected", metrics.connected.toString(), Emerald, Modifier.weight(1f).semantics { role = Role.Button; contentDescription = "${metrics.connected} connected calls today. Open connected calls" }.clickable(role = Role.Button, onClick = onViewConnectedCalls))
        KpiCard("Follow-ups", metrics.followUpsDue.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f).semantics { role = Role.Button; contentDescription = "${metrics.followUpsDue} follow-ups due. Open follow-ups" }.clickable(role = Role.Button, onClick = onViewFollowUps))
    }
}

@Composable private fun LeadCard(item: PriorityLead, onOpen: () -> Unit, onCall: () -> Unit) = PremiumCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) { val lead = item.lead; Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(lead.name, fontWeight = FontWeight.SemiBold); Text(when (item.priority) { QueuePriority.OVERDUE -> "OVERDUE FOLLOW-UP"; QueuePriority.DUE_SOON -> "FOLLOW-UP DUE SOON"; QueuePriority.HOT -> "HOT LEAD"; QueuePriority.NEW -> "NEW · CALL FIRST"; QueuePriority.STANDARD -> "READY TO CALL" }, color = when (item.priority) { QueuePriority.OVERDUE -> MaterialTheme.colorScheme.error; QueuePriority.HOT -> MaterialTheme.colorScheme.error; else -> Indigo }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); lead.company?.let { Text(it, color = Slate) }; Spacer(Modifier.height(4.dp)); Text(lead.displayPhone, color = Indigo) }; FloatingActionButton(onClick = onCall, modifier = Modifier.size(48.dp), containerColor = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Outlined.Call, contentDescription = "Call ${lead.name}", tint = Indigo) } } }
