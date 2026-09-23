package com.callflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.PhoneCallback
import androidx.compose.material.icons.outlined.ContactPhone
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneId
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
import com.callflow.app.ui.home.DailyGoalPlan
import com.callflow.app.ui.leads.LeadDetailScreen
import com.callflow.app.ui.leads.LeadsScreen
import com.callflow.app.ui.calling.CallingScreen
import com.callflow.app.ui.calling.DispositionScreen
import com.callflow.app.ui.calling.ManualDialScreen
import com.callflow.app.ui.operations.CallsScreen
import com.callflow.app.ui.operations.CallsViewModel
import com.callflow.app.ui.operations.CallDetailsScreen
import com.callflow.app.ui.operations.PostCallNavigationViewModel
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
import com.callflow.app.core.model.CallDirection
import com.callflow.app.core.model.CallStatus
import com.callflow.app.core.model.status
import com.callflow.app.core.call.CallAnalysisCalculator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
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
    Destination("home", "Analytics", Icons.Outlined.Analytics), Destination("calls", "Calls", Icons.Outlined.Call),
    Destination("leads", "Leads", Icons.Outlined.People), Destination("followups", "Follow-ups", Icons.Outlined.Schedule),
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
            if (signedIn.deviceStatus == DeviceStatus.ACTIVE) MainNavigation(signedIn.employeeName, signedIn.employeePhone, sessionViewModel::logout)
            else DeviceAccessScreen(signedIn.deviceStatus, checkingDevice, deviceError, sessionViewModel::checkDevice, sessionViewModel::logout)
        }
    }
}

@Composable
private fun MainNavigation(employeeName: String, employeePhone: String?, onLogout: () -> Unit, postCallViewModel: PostCallNavigationViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val postCallTarget by postCallViewModel.target.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(postCallTarget) {
        postCallTarget?.let { target ->
            val route = "call-details/${target.callId}?postCall=true"
            nav.navigate(route) { launchSingleTop = true }
            postCallViewModel.consume(target)
        }
    }
    val topLevel = destinations.any { it.route == currentRoute } || currentRoute == "connected-calls" || currentRoute?.startsWith("filtered-calls") == true
    fun navigateTopLevel(route: String) { nav.navigate(route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }
    Scaffold(bottomBar = {
        if (topLevel) NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            destinations.forEach { item ->
                val selected = currentRoute == item.route || (item.route == "calls" && (currentRoute == "connected-calls" || currentRoute?.startsWith("filtered-calls") == true))
                NavigationBarItem(
                    modifier = Modifier.testTag("nav-${item.route}"),
                    selected = selected,
                    onClick = { navigateTopLevel(item.route) },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Indigo,
                        selectedTextColor = Indigo,
                        unselectedIconColor = Slate,
                        unselectedTextColor = Slate,
                        indicatorColor = Indigo.copy(alpha = 0.16f)
                    ),
                )
            }
        }
    }) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(employeeName = employeeName, onDialNumber = { nav.navigate("manual-dial") }, onOpenMetric = { nav.navigate("filtered-calls/$it") }) }
            composable("leads") { LeadsScreen(onLeadClick = { nav.navigate("lead/$it") }, onCallLead = { nav.navigate("call-now/$it") }) }
            composable("lead/{leadId}", arguments = listOf(navArgument("leadId") { type = NavType.StringType })) { LeadDetailScreen(onBack = { nav.navigateUp() }, onCall = { nav.navigate("call/$it") }) }
            composable("call/{leadId}", arguments = listOf(navArgument("leadId") { type = NavType.StringType })) { CallingScreen(onBack = { nav.navigateUp() }, onCallStarted = { _, callId -> nav.navigate("call-details/$callId?postCall=true") { launchSingleTop = true } }) }
            composable("call-now/{leadId}", arguments = listOf(navArgument("leadId") { type = NavType.StringType })) { CallingScreen(onBack = { nav.navigateUp() }, autoStart = true, onCallStarted = { _, callId -> nav.navigate("call-details/$callId?postCall=true") { launchSingleTop = true } }) }
            composable("disposition/{leadId}/{callId}", arguments = listOf(navArgument("leadId") { type = NavType.StringType }, navArgument("callId") { type = NavType.StringType })) { DispositionScreen(onBack = { nav.navigateUp() }, onSaved = { nav.navigate("calls") { popUpTo("home") } }, onSaveNext = { nextLeadId -> if (nextLeadId == null) navigateTopLevel("leads") else nav.navigate("call/$nextLeadId") { popUpTo("home") } }) }
            composable("calls") { CallsScreen(onDialNumber = { nav.navigate("manual-dial") }, onOpenCall = { nav.navigate("call-details/$it") }, onAddNote = { nav.navigate("call-details/$it?postCall=true") }) }
            composable("connected-calls") { CallsScreen(initialFilter = "Connected", onDialNumber = { nav.navigate("manual-dial") }, onOpenCall = { nav.navigate("call-details/$it") }, onAddNote = { nav.navigate("call-details/$it?postCall=true") }) }
            composable("filtered-calls/{kind}", arguments = listOf(navArgument("kind") { type = NavType.StringType })) { entry ->
                val kind = entry.arguments?.getString("kind").orEmpty()
                CallsScreen(
                    initialFilter = when (kind) { "missed" -> "Missed"; "not-connected" -> "Not connected"; "connected" -> "Connected"; else -> "All" },
                    initialDirectionFilter = when (kind) { "incoming" -> "Incoming"; "outgoing" -> "Outgoing"; else -> "All directions" },
                    uniqueOnly = kind == "unique",
                    onDialNumber = { nav.navigate("manual-dial") },
                    onOpenCall = { nav.navigate("call-details/$it") },
                    onAddNote = { nav.navigate("call-details/$it?postCall=true") },
                )
            }
            composable("call-details/{callId}?postCall={postCall}", arguments = listOf(navArgument("callId") { type = NavType.StringType }, navArgument("postCall") { type = NavType.BoolType; defaultValue = false })) { entry -> CallDetailsScreen(onBack = { nav.navigateUp() }, onOpenLead = { nav.navigate("lead/$it") }, showPostCallNote = entry.arguments?.getBoolean("postCall") == true, onAddResult = { leadId, callId -> nav.navigate("disposition/$leadId/$callId") }) }
            composable("manual-dial") { ManualDialScreen(onBack = { nav.navigateUp() }, onOpenLeadCall = { nav.navigate("call/$it") }) }
            composable("followups") { FollowUpsScreen(onOpenLead = { nav.navigate("lead/$it") }, onCallLead = { nav.navigate("call/$it") }) }
            composable("more") { MoreScreen(employeeName = employeeName, employeePhone = employeePhone, onProfile = { nav.navigate("profile") }, onReports = { nav.navigate("reports") }, onTeamContent = { nav.navigate("team-content") }, onSettings = { nav.navigate("settings") }, onLogout = onLogout) }
            composable("profile") { ProfileScreen(employeeName, employeePhone, onBack = { nav.navigateUp() }) }
            composable("reports") { ReportsScreen(onBack = { nav.navigateUp() }) }
            composable("team-content") { TeamContentScreen(onBack = { nav.navigateUp() }) }
            composable("settings") { SettingsScreen(onBack = { nav.navigateUp() }) }
        }
    }
}

@Composable
private fun HomeScreen(employeeName: String, onDialNumber: () -> Unit, onOpenMetric: (String) -> Unit, callsViewModel: CallsViewModel = hiltViewModel()) {
    val allCalls by callsViewModel.calls.collectAsStateWithLifecycle()
    var range by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("Today") }
    val rangeStart = when (range) {
        "7 days" -> LocalDate.now().minusDays(6)
        "30 days" -> LocalDate.now().minusDays(29)
        else -> LocalDate.now()
    }.atStartOfDay(ZoneId.systemDefault()).toInstant()
    val calls = allCalls.filter { it.startedAt >= rangeStart }
    val analysis = CallAnalysisCalculator.calculate(calls)
    val connectedIncoming = calls.count { it.direction == CallDirection.INCOMING && it.status == CallStatus.CONNECTED }
    val connectedOutgoing = calls.count { it.direction == CallDirection.OUTGOING && it.status == CallStatus.CONNECTED }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
                    Text("$greeting, ${employeeName.substringBefore(" ").ifBlank { "Partner" }}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Your calling overview & performance", color = Slate, style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(
                    onClick = onDialNumber,
                    modifier = Modifier.height(40.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = Indigo.copy(alpha = 0.16f),
                        contentColor = Indigo
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Outlined.Call, null, Modifier.size(17.dp))
                    Text("  Dial Pad", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Today", "7 days", "30 days").forEach { value ->
                    androidx.compose.material3.FilterChip(
                        selected = range == value,
                        onClick = { range = value },
                        label = { Text(value, fontWeight = if (range == value) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(width = 3.5.dp, height = 15.dp)
                        .background(Indigo, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                )
                Text("Call summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        item { AnalyticsMetricRow(
            AnalyticsMetric("Total calls", analysis.totalCalls, analysis.totalTalkTimeSeconds, Icons.Outlined.PhoneCallback, Indigo, "all"),
            AnalyticsMetric("Incoming", analysis.incomingCalls, calls.filter { it.direction == CallDirection.INCOMING }.sumOf(::callTalkSeconds), Icons.Outlined.CallReceived, Emerald, "incoming"),
            onOpenMetric,
        ) }
        item { AnalyticsMetricRow(
            AnalyticsMetric("Outgoing", analysis.outgoingCalls, calls.filter { it.direction == CallDirection.OUTGOING }.sumOf(::callTalkSeconds), Icons.Outlined.CallMade, androidx.compose.ui.graphics.Color(0xFFF59E0B), "outgoing"),
            AnalyticsMetric("Missed", analysis.missedCalls, 0, Icons.Outlined.CallMissed, MaterialTheme.colorScheme.error, "missed"),
            onOpenMetric,
        ) }
        item { AnalyticsMetricRow(
            AnalyticsMetric("Connected", analysis.connectedCalls, analysis.totalTalkTimeSeconds, Icons.Outlined.Call, Emerald, "connected"),
            AnalyticsMetric("Not connected", analysis.notConnectedCalls, 0, Icons.Outlined.CallMissed, MaterialTheme.colorScheme.error, "not-connected"),
            onOpenMetric,
        ) }
        item { AnalyticsMetricRow(
            AnalyticsMetric("Unique calls", analysis.uniqueNumbers, 0, Icons.Outlined.ContactPhone, Indigo, "unique"),
            AnalyticsMetric("Connect rate", analysis.connectionRatePercent, analysis.averageTalkTimeSeconds, Icons.Outlined.Analytics, Emerald, "connected", valueSuffix = "%", durationLabel = "avg talk"),
            onOpenMetric,
        ) }
        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Connected direction", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("$connectedIncoming in · $connectedOutgoing out", color = Slate, style = MaterialTheme.typography.labelSmall)
                    }
                    LinearProgressIndicator(
                        progress = { if (analysis.connectedCalls == 0) 0f else connectedOutgoing.toFloat() / analysis.connectedCalls },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Indigo,
                        trackColor = Indigo.copy(alpha = 0.16f),
                    )
                }
            }
        }
    }
}

private data class AnalyticsMetric(val label: String, val value: Int, val seconds: Long, val icon: ImageVector, val tint: androidx.compose.ui.graphics.Color, val route: String, val valueSuffix: String = "", val durationLabel: String = "talk time")

@Composable private fun AnalyticsMetricRow(left: AnalyticsMetric, right: AnalyticsMetric, onOpen: (String) -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    AnalyticsMetricCard(left, Modifier.weight(1f), onOpen)
    AnalyticsMetricCard(right, Modifier.weight(1f), onOpen)
}

@Composable private fun AnalyticsMetricCard(metric: AnalyticsMetric, modifier: Modifier, onOpen: (String) -> Unit) = PremiumCard(
    modifier = modifier
        .semantics { role = Role.Button; contentDescription = "${metric.label}, ${metric.value}${metric.valueSuffix}. Open filtered calls" }
        .clickable(role = Role.Button) { onOpen(metric.route) }
) {
    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                color = metric.tint.copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, metric.tint.copy(alpha = 0.25f)),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(metric.icon, null, tint = metric.tint, modifier = Modifier.size(18.dp))
                }
            }
            Text("›", color = Slate.copy(alpha = 0.6f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text("${metric.value}${metric.valueSuffix}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(metric.label, style = MaterialTheme.typography.labelMedium, color = Slate, fontWeight = FontWeight.Medium, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Outlined.Schedule, null, tint = Slate.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
            Text("${homeDuration(metric.seconds)} ${metric.durationLabel}", color = Slate.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun callTalkSeconds(call: com.callflow.app.core.model.CallRecord): Long = call.answeredAt?.let { start -> call.endedAt?.epochSecond?.minus(start.epochSecond) }?.coerceAtLeast(0) ?: 0

@Composable
private fun HomeHeader(employeeName: String) {
    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("$greeting,", color = Slate, style = MaterialTheme.typography.labelSmall)
            Text(employeeName.substringBefore(" ").ifBlank { "Sales partner" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Surface(shape = CircleShape, color = Emerald.copy(alpha = .12f)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(7.dp), shape = CircleShape, color = Emerald) {}
                Spacer(Modifier.width(6.dp))
                Text("LIVE", color = Emerald, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TodayCallHero(metrics: DailyMetrics, onViewCalls: () -> Unit) {
    val rate = if (metrics.calls == 0) 0 else metrics.connected * 100 / metrics.calls
    PremiumCard(Modifier.fillMaxWidth().semantics { role = Role.Button; contentDescription = "${metrics.calls} total calls today, $rate percent connected" }.clickable(role = Role.Button, onClick = onViewCalls)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(metrics.calls.toString(), style = MaterialTheme.typography.headlineMedium)
            Column(Modifier.weight(1f)) {
                Text("TODAY’S CALLS", color = Indigo, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("$rate% connected", color = Slate, style = MaterialTheme.typography.labelSmall)
            }
            Surface(shape = CircleShape, color = Indigo.copy(alpha = .12f), modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Call, contentDescription = null, tint = Indigo, modifier = Modifier.padding(7.dp))
            }
        }
    }
}

@Composable
private fun DailyTargetCard(performance: com.callflow.app.data.remote.TodayPerformanceResponse?, loading: Boolean, onRefresh: () -> Unit) {
    PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CompactSummaryHeading("Today’s targets", when { loading -> "Loading…"; performance != null -> "Live"; else -> "Unavailable" })
        if (performance == null) {
            Text("Targets unavailable. Call totals remain visible above.", color = Slate, style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.OutlinedButton(onClick = onRefresh) { Text("TRY AGAIN") }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactTarget("Calls", "${performance.calls} / ${performance.callTarget}", performance.callTargetPercent / 100f, Modifier.weight(1f))
                CompactTarget("Connected", "${performance.connected} / ${performance.connectedTarget}", performance.connectedTargetPercent / 100f, Modifier.weight(1f))
            }
            Text(buildList {
                add("${performance.connectionRate}% connection")
                if (performance.leaderboardSize > 0) add("Rank #${performance.leaderboardRank} of ${performance.leaderboardSize}")
                add("${performance.conversions} converted")
                add("${performance.followUpsDue} follow-ups due")
            }.joinToString(" · "), color = Slate, style = MaterialTheme.typography.labelSmall)
        }
    } }
}

@Composable
private fun DailyCoachCard(plan: DailyGoalPlan) {
    PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CompactSummaryHeading("Daily coach", "${plan.overallProgressPercent}% complete")
        LinearProgressIndicator(progress = { (plan.overallProgressPercent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Emerald)
        Text(plan.nextAction, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Indigo)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GoalRemaining("Calls left", plan.callsRemaining.toString(), Modifier.weight(1f))
            GoalRemaining("Connect left", plan.connectedRemaining.toString(), Modifier.weight(1f))
            GoalRemaining("Talk left", homeDuration(plan.talkTimeRemainingSeconds), Modifier.weight(1f))
        }
    } }
}

@Composable
private fun GoalRemaining(label: String, value: String, modifier: Modifier = Modifier) = Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) { Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall, color = Slate) }
}

@Composable
private fun CompactSummaryHeading(title: String, status: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(status, style = MaterialTheme.typography.labelSmall, color = Indigo)
    }
}

@Composable
private fun CompactTarget(label: String, value: String, progress: Float, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label · $value", style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp))
    }
}

@Composable
private fun MetricsGrid(metrics: DailyMetrics, onViewConnectedCalls: () -> Unit, onViewFollowUps: () -> Unit) {
    PremiumCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            CompactHomeMetric("Connected", metrics.connected.toString(), Modifier.weight(1f).clickable(role = Role.Button, onClick = onViewConnectedCalls))
            CompactHomeMetric("Talk time", homeDuration(metrics.talkTimeSeconds), Modifier.weight(1f))
            CompactHomeMetric("Follow-ups", metrics.followUpsDue.toString(), Modifier.weight(1f).clickable(role = Role.Button, onClick = onViewFollowUps))
            CompactHomeMetric("Converted", metrics.conversions.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactHomeMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Indigo)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Slate, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

private fun homeDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = safe % 3600 / 60
    return if (hours > 0) "${hours}h ${minutes}m" else if (minutes > 0) "${minutes}m ${safe % 60}s" else "${safe}s"
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable private fun LeadCard(item: PriorityLead, onOpen: () -> Unit, onCall: () -> Unit) = PremiumCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
    val lead = item.lead
    val badge = when (item.priority) {
        QueuePriority.OVERDUE -> "Overdue"
        QueuePriority.DUE_SOON -> "Follow-up"
        QueuePriority.HOT -> "Hot lead"
        QueuePriority.NEW -> "New"
        QueuePriority.STANDARD -> "Ready"
    }
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(lead.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(badge, Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Indigo)
                }
            }
            lead.company?.let { Text(it, color = Slate, style = MaterialTheme.typography.bodySmall) }
            Text(lead.displayPhone, color = Slate, style = MaterialTheme.typography.bodySmall)
        }
        FloatingActionButton(onClick = onCall, modifier = Modifier.size(48.dp), containerColor = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Outlined.Call, contentDescription = "Call ${lead.name}", tint = Indigo) }
    }
}
