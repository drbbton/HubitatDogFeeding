/**
 *  Dog Feeding & Walk Reminder
 *
 *  Hubitat port of the webCoRE "Dog Fed Reminder" + "Dog Fed Notification" pistons,
 *  plus Alexa announcements to take the dog out after he eats.
 *
 *  What it does
 *    1. Watches a bowl sensor (tilt / vibration / contact) or a virtual "Dog Fed"
 *       switch and records WHEN the dog was fed, as a human-readable status string
 *       ("Fed on Tuesday at 7:36 AM"). Status is published to the app label, an
 *       optional Hub Variable (for dashboards), and an optional switch/contact.
 *    2. Resets the status twice a day ("Dogs have not had breakfast" at 12:01 AM,
 *       "Dogs have not had dinner" at 12:01 PM) so the string always says what is
 *       still owed.
 *    3. Nags at configurable meal times (7:30 AM / 6:00 PM) if that meal has not
 *       been fed. If nobody is home it defers and nags when someone gets back.
 *    4. After a feeding, announces on Alexa (or any TTS speaker) at two
 *       configurable offsets — default +15 minutes and +60 minutes — to take the
 *       dog out. Optionally cancelled if the dog is let out first.
 *
 *  Alexa options
 *    - Echo Speaks devices: picked up as TTS speakers; the app speaks the real
 *      message text (playAnnouncement() used when the device supports it).
 *    - No Echo Speaks: pick a virtual contact/switch per reminder and build an
 *      Alexa Routine ("When contact opens -> Alexa says ...").
 */

definition(
    name: "Dog Feeding & Walk Reminder",
    namespace: "drbbton",
    author: "drbbton",
    description: "Track when the dog was fed, nag at meal times, and announce on Alexa to take him out afterward.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/drbbton/HubitatDogFeeding/main/dogfeedingapp.groovy",
    singleInstance: false
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Dog Feeding &amp; Walk Reminder</b>", install: true, uninstall: true) {
        section {
            paragraph "<b>Current status:</b> ${state.fedStatus ?: 'unknown'}"
        }
        section("<b>0. The dog</b>") {
            input "dogName", "text",
                  title: "Dog's name — use <b>%dog%</b> in any message below and it is swapped in",
                  required: false
            paragraph "Tokens available in every message field: <b>%dog%</b> (name, or \"the dog\" if blank), " +
                      "<b>%day%</b> (day of week) and <b>%time%</b> (clock time) of the last feeding."
        }
        section("<b>1. Feeding triggers</b>") {
            input "bowlContacts", "capability.contactSensor",
                  title: "Bowl tilt/contact sensor(s) — feeding = open", multiple: true, required: false
            input "bowlAccels", "capability.accelerationSensor",
                  title: "Bowl vibration sensor(s) — feeding = active", multiple: true, required: false
            input "bowlTilts", "capability.threeAxis",
                  title: "Bowl orientation sensor(s) — feeding = any three-axis change", multiple: true, required: false
            input "fedSwitches", "capability.switch",
                  title: "Manual 'Dog Fed' switch(es) — turn on via Alexa/Siri/dashboard", multiple: true, required: false
            input "fedButtons", "capability.pushableButton",
                  title: "Button(s) — any push means fed", multiple: true, required: false
            input "cooldownMinutes", "number",
                  title: "Ignore repeat feeding triggers for this many minutes", defaultValue: 60, required: true
        }
        section("<b>2. Meal windows &amp; status reset</b>") {
            input "breakfastResetTime", "time",
                  title: "Start of the breakfast window — status resets to 'not had breakfast'", required: true
            input "dinnerResetTime", "time",
                  title: "Start of the dinner window — status resets to 'not had dinner'", required: true
            input "fedStatusText", "text", title: "Status text once fed",
                  defaultValue: "%dog% was fed on %day% at %time%", required: true
            input "breakfastPendingText", "text", title: "Breakfast-owed status text",
                  defaultValue: "%dog% has not had breakfast", required: true
            input "dinnerPendingText", "text", title: "Dinner-owed status text",
                  defaultValue: "%dog% has not had dinner", required: true
        }
        section("<b>3. Feed-the-dog nags</b>") {
            input "breakfastNagTime", "time", title: "Nag if breakfast has not happened by", required: false
            input "dinnerNagTime", "time", title: "Nag if dinner has not happened by", required: false
            input "nagMessage", "text", title: "Nag message (spoken on the same Echo/TTS devices as section 5)",
                  defaultValue: "Don't forget to feed %dog%", required: true
            input "presenceSensors", "capability.presenceSensor",
                  title: "Only nag when at least one of these is present (optional)", multiple: true, required: false
            input "skipModes", "mode", title: "Never nag in these modes (optional)", multiple: true, required: false
            input "remindWhenBack", "bool",
                  title: "If nobody is home / mode is skipped, nag as soon as someone returns",
                  defaultValue: true
        }
        section("<b>4. Take-him-out announcements</b>") {
            input "firstMinutes", "number", title: "First announcement, minutes after feeding",
                  defaultValue: 15, required: true
            input "secondMinutes", "number", title: "Second announcement, minutes after feeding",
                  defaultValue: 60, required: true
            input "firstMessage", "text", title: "First announcement text",
                  defaultValue: "Time to take %dog% out. First trip after eating.", required: true
            input "secondMessage", "text", title: "Second announcement text",
                  defaultValue: "Time to take %dog% out again. Second trip after eating.", required: true
            input "outMotions", "capability.motionSensor",
                  title: "'Dog was let out' MOTION sensor(s) — e.g. the garage, since that is the way out",
                  multiple: true, required: false
            input "outContacts", "capability.contactSensor",
                  title: "'Dog was let out' door/contact sensor(s)", multiple: true, required: false
            input "outSwitches", "capability.switch",
                  title: "'Dog was let out' switch(es) — voice or dashboard", multiple: true, required: false
            input "letOutGraceMinutes", "number",
                  title: "Ignore let-out signals for this many minutes right after feeding (keeps someone " +
                         "walking through the garage while the bowl is still full from cancelling the reminders)",
                  defaultValue: 5, required: true
        }
        section("<b>5. Alexa / speech output</b>") {
            input "speechDevices", "capability.speechSynthesis",
                  title: "TTS speakers — Echo Speaks devices, Sonos, Chromecast, etc.", multiple: true, required: false
            input "useAnnouncement", "bool",
                  title: "Use playAnnouncement() when the device supports it (Echo Speaks)", defaultValue: true
            input "speechVolume", "number",
                  title: "Speak at this volume and restore (blank = leave volume alone)", required: false
            input "firstAlertContacts", "capability.contactSensor",
                  title: "First-announcement virtual CONTACT(s) to open — for an Alexa Routine", multiple: true, required: false
            input "firstAlertSwitches", "capability.switch",
                  title: "First-announcement virtual SWITCH(es) to turn on — for Alexa/HomeKit", multiple: true, required: false
            input "secondAlertContacts", "capability.contactSensor",
                  title: "Second-announcement virtual CONTACT(s) to open", multiple: true, required: false
            input "secondAlertSwitches", "capability.switch",
                  title: "Second-announcement virtual SWITCH(es) to turn on", multiple: true, required: false
            input "nagAlertContacts", "capability.contactSensor",
                  title: "Feed-the-dog nag virtual CONTACT(s) to open", multiple: true, required: false
            input "nagAlertSwitches", "capability.switch",
                  title: "Feed-the-dog nag virtual SWITCH(es) to turn on", multiple: true, required: false
            input "alertSeconds", "number", title: "Auto-reset those virtual devices after (seconds)",
                  defaultValue: 30, required: true
            input "notifyDevices", "capability.notification",
                  title: "Push notification devices (Hubitat mobile app)", multiple: true, required: false
        }
        section("<b>6. Status readout</b>") {
            input "createStatusDevice", "bool",
                  title: "Create a <b>Dog Feeding Status</b> device — Dashboard tile, event history, " +
                         "'Fed'/'Let out' buttons, and a switch you can share to Alexa/HomeKit",
                  defaultValue: true
            if (createStatusDevice) {
                def cd = getChildDevice(statusDni())
                paragraph cd ? "Device: <b>${cd.displayName}</b> (status: ${cd.currentValue('status')})"
                             : "Device will be created when you hit Done."
            }
            input "statusSwitches", "capability.switch",
                  title: "Other switch(es) that speak the current fed status when turned on, then turn themselves off",
                  multiple: true, required: false
            input "statusVarName", "string",
                  title: "Hub Variable (String) to also write the status into (optional)", required: false
            input "updateLabel", "bool", title: "Show the status in this app's name", defaultValue: true
        }
        section("<b>Options</b>") {
            input "logEnable", "bool", title: "Enable debug logging", defaultValue: true
        }
    }
}

// ---------------------------------------------------------------- lifecycle

def installed() {
    Integer hr = new Date().format("H", location.timeZone) as Integer
    state.pendingMeal = hr < 12 ? "breakfast" : "dinner"
    state.fedStatus = pendingTextForNow()
    updated()
}

def uninstalled() {
    removeAllInUseGlobalVar()
    getChildDevices()?.each { deleteChildDevice(it.deviceNetworkId) }
}

def updated() {
    unsubscribe()
    unschedule()
    removeAllInUseGlobalVar()

    bowlContacts?.each { subscribe(it, "contact.open", "fedHandler") }
    bowlAccels?.each  { subscribe(it, "acceleration.active", "fedHandler") }
    bowlTilts?.each   { subscribe(it, "threeAxis", "fedHandler") }
    fedSwitches?.each { subscribe(it, "switch.on", "fedHandler") }
    fedButtons?.each  { subscribe(it, "pushed", "fedHandler") }

    outSwitches?.each  { subscribe(it, "switch.on", "letOutHandler") }
    outContacts?.each  { subscribe(it, "contact.open", "letOutHandler") }
    outMotions?.each   { subscribe(it, "motion.active", "letOutHandler") }
    statusSwitches?.each { subscribe(it, "switch.on", "statusRequestHandler") }

    manageStatusDevice()

    if (remindWhenBack) {
        presenceSensors?.each { subscribe(it, "presence.present", "backHomeHandler") }
        subscribe(location, "mode", "modeChangeHandler")
    }

    if (statusVarName) {
        if (!addInUseGlobalVar(statusVarName)) {
            log.warn "Hub Variable '${statusVarName}' not found — create it under Settings > Hub Variables (String)."
        }
    }

    scheduleDaily(breakfastResetTime, "breakfastReset")
    scheduleDaily(dinnerResetTime, "dinnerReset")
    scheduleDaily(breakfastNagTime, "breakfastNag")
    scheduleDaily(dinnerNagTime, "dinnerNag")

    publishStatus(state.fedStatus ?: pendingTextForNow())
    if (logEnable) log.debug "Updated. Announcements at +${firstMinutes}/+${secondMinutes} min, cooldown ${cooldownMinutes} min."
}

private String statusDni() { "dogfeeding-${app.id}" }

/** Create or remove the companion child device to match the preference. */
private void manageStatusDevice() {
    def cd = getChildDevice(statusDni())
    if (createStatusDevice && !cd) {
        try {
            cd = addChildDevice("drbbton", "Dog Feeding Status", statusDni(),
                                [name: "Dog Feeding Status",
                                 label: dogName?.trim() ? "${dogName.trim()} Feeding Status" : "Dog Feeding Status",
                                 isComponent: false])
            log.info "Created child device ${cd.displayName}."
        } catch (e) {
            log.error "Could not create the status device — install the 'Dog Feeding Status' driver " +
                      "(Drivers Code) first. ${e.message}"
        }
    } else if (!createStatusDevice && cd) {
        deleteChildDevice(statusDni())
        log.info "Removed the status child device."
    }
}

private void scheduleDaily(String timeStr, String handler) {
    if (!timeStr) return
    Date d = toDateTime(timeStr)
    schedule("0 ${d.format("m", location.timeZone)} ${d.format("H", location.timeZone)} * * ?", handler)
    if (logEnable) log.debug "Scheduled ${handler} daily at ${d.format("h:mm a", location.timeZone)}."
}

// ----------------------------------------------------------------- feeding

def fedHandler(evt) {
    Long nowMs = now()
    Long cooldownMs = ((cooldownMinutes ?: 60) as Long) * 60000L
    if (state.lastFed && (nowMs - state.lastFed) < cooldownMs) {
        if (logEnable) log.debug "Feeding trigger from ${evt?.displayName} ignored (within cooldown)."
        resetFedSwitches()
        return
    }
    state.lastFed = nowMs
    state.remindWhenBack = false
    state.pendingMeal = null

    publishStatus(render(fedStatusText ?: "%dog% was fed on %day% at %time%", new Date(nowMs)))

    state.walksPending = 2
    runIn(((firstMinutes ?: 15) as Integer) * 60, "firstReminder", [overwrite: true])
    runIn(((secondMinutes ?: 60) as Integer) * 60, "secondReminder", [overwrite: true])
    resetFedSwitches()
    publishStatus(state.fedStatus)
    log.info "Dog fed (${evt?.displayName}). Announcements at +${firstMinutes} and +${secondMinutes} minutes."
}

def letOutHandler(evt) {
    Long graceMs = ((letOutGraceMinutes ?: 5) as Long) * 60000L
    if (state.lastFed && (now() - state.lastFed) < graceMs) {
        if (logEnable) log.debug "Let-out signal from ${evt?.displayName} ignored (inside the ${letOutGraceMinutes}-minute grace window)."
        return
    }
    if (!state.walksPending) {
        if (logEnable) log.debug "Let-out signal from ${evt?.displayName} ignored (nothing pending)."
        return
    }
    unschedule("firstReminder")
    unschedule("secondReminder")
    state.walksPending = 0
    log.info "Dog let out (${evt?.displayName}) — pending take-him-out announcements cancelled."
    outSwitches?.each { if (it.currentValue("switch") == "on") it.off() }
    publishStatus(state.fedStatus)
}

def firstReminder() {
    state.walksPending = ((state.walksPending ?: 1) as Integer) - 1
    announce(render(firstMessage ?: "Time to take %dog% out."), firstAlertContacts, firstAlertSwitches)
    publishStatus(state.fedStatus)
}

def secondReminder() {
    state.walksPending = 0
    announce(render(secondMessage ?: "Time to take %dog% out."), secondAlertContacts, secondAlertSwitches)
    publishStatus(state.fedStatus)
}

// ------------------------------------------------ child device callbacks

def childFed(String source) {
    fedHandler([displayName: source ?: "status device"])
}

def childLetOut(String source) {
    // A manual "he went out" press should always count, grace window or not.
    unschedule("firstReminder")
    unschedule("secondReminder")
    state.walksPending = 0
    log.info "Dog let out (${source ?: 'status device'}) — pending take-him-out announcements cancelled."
    publishStatus(state.fedStatus)
}

def childSpeakStatus() {
    announce(state.fedStatus ?: render("I do not know when %dog% was last fed."), null, null)
}

// -------------------------------------------------------- resets and nags

def breakfastReset() {
    state.pendingMeal = "breakfast"
    state.remindWhenBack = false
    publishStatus(breakfastPending())
}

def dinnerReset() {
    state.pendingMeal = "dinner"
    state.remindWhenBack = false
    publishStatus(dinnerPending())
}

def breakfastNag() { nagIfUnfed("breakfast") }

def dinnerNag()    { nagIfUnfed("dinner") }

private String breakfastPending() { render(breakfastPendingText ?: "%dog% has not had breakfast") }

private String dinnerPending()    { render(dinnerPendingText ?: "%dog% has not had dinner") }

private void nagIfUnfed(String meal) {
    if (state.pendingMeal != meal) {
        if (logEnable) log.debug "Nag skipped — ${meal} already handled (pending meal: ${state.pendingMeal ?: 'none'})."
        return
    }
    if (!someoneAround()) {
        if (remindWhenBack) {
            state.remindWhenBack = true
            log.info "Nobody home / skipped mode — will nag when someone returns."
        }
        return
    }
    announce(render(nagMessage ?: "Don't forget to feed %dog%"), nagAlertContacts, nagAlertSwitches)
}

def backHomeHandler(evt) { deferredNag() }

def modeChangeHandler(evt) { deferredNag() }

private void deferredNag() {
    if (!state.remindWhenBack) return
    if (!someoneAround()) return
    if (!state.pendingMeal) { state.remindWhenBack = false; return }
    state.remindWhenBack = false
    announce(render(nagMessage ?: "Don't forget to feed %dog%"), nagAlertContacts, nagAlertSwitches)
}

private boolean someoneAround() {
    if (skipModes && location.mode in skipModes) return false
    if (presenceSensors) return presenceSensors.any { it.currentValue("presence") == "present" }
    return true
}

// ------------------------------------------------------------ status echo

def statusRequestHandler(evt) {
    String msg = state.fedStatus ?: render("I do not know when %dog% was last fed.")
    announce(msg, null, null)
    statusSwitches?.each { if (it.currentValue("switch") == "on") it.off() }
}

private void publishStatus(String status) {
    state.fedStatus = status
    if (statusVarName) setGlobalVar(statusVarName, status)
    getChildDevice(statusDni())?.setStatus(status,
                                           state.lastFed as Long,
                                           state.pendingMeal as String ?: "none",
                                           (state.walksPending ?: 0) as Integer)
    if (updateLabel) {
        app.updateLabel("${app.name.replaceAll(/ <span.*/, "")} <span style='color:gray'>&mdash; ${status}</span>")
    }
    if (logEnable) log.debug "Status: ${status}"
}

// ---------------------------------------------------------------- outputs

private void announce(String msg, def contacts, def switches) {
    log.info "Announce: ${msg}"
    speechDevices?.each { d ->
        try {
            if (speechVolume != null && d.hasCommand("setVolumeSpeakAndRestore")) {
                d.setVolumeSpeakAndRestore(speechVolume as Integer, msg)
            } else if (useAnnouncement && d.hasCommand("playAnnouncement")) {
                d.playAnnouncement(msg)
            } else {
                d.speak(msg)
            }
        } catch (e) {
            log.warn "Speech failed on ${d.displayName}: ${e.message}"
        }
    }
    notifyDevices?.each { it.deviceNotification(msg) }
    contacts?.each { d ->
        if (d.hasCommand("open")) d.open()
        else log.warn "${d.displayName} has no open() command — use the built-in Virtual Contact Sensor driver."
    }
    switches?.each { it.on() }
    if ((contacts || switches) && (alertSeconds ?: 30) > 0) {
        state.pendingResetContacts = contacts?.collect { it.id }
        state.pendingResetSwitches = switches?.collect { it.id }
        runIn((alertSeconds ?: 30) as Integer, "resetAlerts", [overwrite: true])
    }
}

def resetAlerts() {
    allAlertContacts().each {
        if (it.id in (state.pendingResetContacts ?: []) && it.hasCommand("close")) it.close()
    }
    allAlertSwitches().each {
        if (it.id in (state.pendingResetSwitches ?: [])) it.off()
    }
    state.pendingResetContacts = []
    state.pendingResetSwitches = []
    if (logEnable) log.debug "Alert devices reset."
}

private List allAlertContacts() {
    return ((firstAlertContacts ?: []) + (secondAlertContacts ?: []) + (nagAlertContacts ?: [])).unique { it.id }
}

private List allAlertSwitches() {
    return ((firstAlertSwitches ?: []) + (secondAlertSwitches ?: []) + (nagAlertSwitches ?: [])).unique { it.id }
}

private void resetFedSwitches() {
    fedSwitches?.each { if (it.currentValue("switch") == "on") it.off() }
}

private String pendingTextForNow() {
    Integer hr = new Date().format("H", location.timeZone) as Integer
    return hr < 12 ? breakfastPending() : dinnerPending()
}

/**
 * Swap the message tokens: %dog% (name, or "the dog"), and %day% / %time% of
 * the supplied date (defaults to the last recorded feeding).
 */
private String render(String template, Date when = null) {
    if (!template) return ""
    Date d = when ?: (state.lastFed ? new Date(state.lastFed as Long) : null)
    String out = template.replace("%dog%", (dogName?.trim() ?: "the dog"))
    if (d) {
        out = out.replace("%day%", d.format("EEEE", location.timeZone))
                 .replace("%time%", d.format("h:mm a", location.timeZone))
    }
    return out
}

// Hub Variable rename/removal hook
def renameInUseGlobalVar(Map data) {
    if (statusVarName == data?.oldName) {
        app.updateSetting("statusVarName", [value: data.newName, type: "string"])
    }
}
