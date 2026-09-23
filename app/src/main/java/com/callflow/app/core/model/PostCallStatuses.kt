package com.callflow.app.core.model

/** Stable codes retain compatibility with existing call outcomes. */
val postCallStatuses: List<DispositionOption> = listOf(
    DispositionOption("intro_attended", "INTRO_ATTENDED", "Intro Attendees", false, false, "qualified"),
    DispositionOption("warm", "WARM", "Warm", false, false, "qualified"),
    DispositionOption("hot", "HOT", "Hot", false, false, "hot"),
    DispositionOption("generate_meeting", "GENERATE_MEETING", "Meeting Generate", false, false, "contacted"),
    DispositionOption("online", "ONLINE", "Online", false, false, "contacted"),
    DispositionOption("not_eligible", "NOT_ELIGIBLE", "Not Eligible", false, false, "lost"),
    DispositionOption("invite_intro", "INVITE_INTRO", "Invite in intro", false, false, "contacted"),
    DispositionOption("next_time_attend", "NEXT_TIME_ATTEND", "Invite next intro", false, false, "contacted"),
    DispositionOption("online_intro", "ONLINE_INTRO", "Invite online intro", false, false, "contacted"),
    DispositionOption("busy", "BUSY", "Busy", false, false, null),
    DispositionOption("out_of_network", "OUT_OF_NETWORK", "Out of network", false, false, null),
    DispositionOption("wrong_number", "WRONG_NUMBER", "Wrong Number", false, false, "lost"),
    DispositionOption("not_connected", "NOT_CONNECTED", "Not Connected", false, false, null),
    DispositionOption("no_answer", "NO_ANSWER", "Not Pickup Call", false, false, null),
    DispositionOption("negative", "NEGATIVE", "Negative", false, false, "lost"),
    DispositionOption("callback", "CALLBACK_REQUESTED", "Call Back", false, false, "follow_up"),
    DispositionOption("online_meeting", "ONLINE_MEETING", "Online Meeting", false, false, "contacted"),
    DispositionOption("not_interested", "NOT_INTERESTED", "Not interested", false, false, "lost"),
    DispositionOption("other_workshop", "OTHER_WORKSHOP", "Other workshop", false, false, "contacted"),
    DispositionOption("uyp_registered", "UYP_REGISTERED", "UYP Registered", false, false, "won")
)
