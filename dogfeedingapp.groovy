/**
 *  Dog Feeding & Walk Reminder
 *
 *  Hubitat port of the webCoRE "Dog Fed Reminder" + "Dog Fed Notification" pistons,
 *  plus spoken announcements to take the dog out after he eats.
 *
 *  What it does
 *    1. Watches a bowl sensor (tilt / vibration / three-axis) or a virtual "Dog
 *       Fed" switch and records WHEN he was fed, as a message-template status
 *       string ("Rex was fed on Tuesday at 7:36 AM"). Published to the app label,
 *       a companion child device, and optionally a Hub Variable.
 *    2. Resets the status twice a day (default 12:01 AM / 12:01 PM) so the string
 *       always says which meal is still owed.
 *    3. Nags at configurable meal times if that meal has not been fed. If nobody
 *       is home it defers and nags when someone gets back.
 *    4. After a feeding, announces at two configurable offsets — default
 *       +15 minutes and +60 minutes. Cancelled when he actually goes out
 *       (motion, door contact, switch, or the child device's button).
 *
 *  Speech options, best first
 *    - Sonos / any music player: local, no cloud login, speaks the real text and
 *      restores whatever was playing. Nothing to re-authorize. Note the Sonos
 *      Player driver declares MusicPlayer (not AudioNotification) and offers
 *      playTextAndRestore() as a custom command, so speakOn() picks the best
 *      available command per device rather than trusting one capability.
 *    - Echo Speaks devices: also speak the real text, but depend on an Amazon
 *      cookie that expires periodically.
 *    - Amazon Echo Skill + Alexa Routines: the app can create three virtual
 *      contact sensors (one per phrase); each Routine says a fixed phrase.
 *      Nothing to maintain, but the text cannot be dynamic.
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
            if (state.lastAnnounce) {
                paragraph "<b>Last announcement:</b> ${state.lastAnnounce}<br>${state.lastAnnounceDetail ?: ''}"
            }
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
        section("<b>5. Speech output</b>") {
            input "musicDevices", "capability.musicPlayer",
                  title: "<b>Sonos and other music players</b> — spoken locally, no cloud login. Note the " +
                         "Sonos Player driver reports MusicPlayer, not AudioNotification, which is why Sonos " +
                         "does not appear in the next two fields.",
                  multiple: true, required: false
            input "audioDevices", "capability.audioNotification",
                  title: "Audio-notification speakers (Chromecast, etc.)", multiple: true, required: false
            input "speechDevices", "capability.speechSynthesis",
                  title: "TTS speakers — Echo Speaks devices, etc.", multiple: true, required: false
            input "useAnnouncement", "bool",
                  title: "Prefer playAnnouncement() when a device supports it (Echo Speaks)", defaultValue: true
            input "speechVolume", "number",
                  title: "Speak at this volume and restore what was playing (0 = do not touch the volume)",
                  defaultValue: 35, required: false
            paragraph "Picking the same speaker in more than one field is harmless — it is only spoken to once."
            input "releaseAfterSpeaking", "bool",
                  title: "<b>Release the speaker afterwards</b> — some drivers (Sonos Player included) leave the " +
                         "player parked on the TTS clip at announcement volume when nothing was playing before, " +
                         "because they record no source URI to return to. This puts the previous source and " +
                         "volume back.",
                  defaultValue: true
            input "releaseSeconds", "number",
                  title: "Wait this many seconds before releasing — must be longer than the spoken message",
                  defaultValue: 15, required: true
            input "btnTestSpeech", "button", title: "🔊 Test speech now"
            paragraph speakerReport()
            input "createAlertDevices", "bool",
                  title: "<b>Create three virtual contact sensors for Alexa Routines</b> — one per phrase " +
                         "(first walk, second walk, feed the dog). Share them to the Amazon Echo Skill, then " +
                         "build a Routine on each: <i>contact opens &rarr; Alexa Says</i>. No cloud login, " +
                         "nothing to add by hand.",
                  defaultValue: false
            if (createAlertDevices) {
                paragraph alertDeviceSummary()
            }
            input "firstAlertContacts", "capability.contactSensor",
                  title: "Extra first-announcement CONTACT(s) to open (optional)", multiple: true, required: false
            input "firstAlertSwitches", "capability.switch",
                  title: "First-announcement virtual SWITCH(es) to turn on — for Alexa/HomeKit", multiple: true, required: false
            input "secondAlertContacts", "capability.contactSensor",
                  title: "Extra second-announcement CONTACT(s) to open (optional)", multiple: true, required: false
            input "secondAlertSwitches", "capability.switch",
                  title: "Second-announcement virtual SWITCH(es) to turn on", multiple: true, required: false
            input "nagAlertContacts", "capability.contactSensor",
                  title: "Extra feed-the-dog nag CONTACT(s) to open (optional)", multiple: true, required: false
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
    manageAlertDevices()

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

private String alertDni(String key) { "dogfeeding-${app.id}-${key}" }

private def alertChild(String key) { getChildDevice(alertDni(key)) }

/** The three Alexa-Routine trigger devices this app can create for itself. */
private Map alertDeviceSpecs() {
    String who = dogName?.trim() ?: "Dog"
    return ["walk1": "${who} Out Reminder 1",
            "walk2": "${who} Out Reminder 2",
            "nag"  : "Feed ${who}"]
}

private String alertDeviceSummary() {
    return alertDeviceSpecs().collect { key, label ->
        def cd = alertChild(key)
        cd ? "&bull; <b>${cd.displayName}</b> — ready to share to Alexa"
           : "&bull; ${label} — will be created when you hit Done"
    }.join("<br>")
}

private void manageAlertDevices() {
    alertDeviceSpecs().each { key, label ->
        def cd = alertChild(key)
        if (createAlertDevices && !cd) {
            try {
                addChildDevice("hubitat", "Virtual Contact Sensor", alertDni(key),
                               [name: "Virtual Contact Sensor", label: label, isComponent: false])
                log.info "Created Alexa trigger device '${label}'."
            } catch (e) {
                log.error "Could not create '${label}': ${e.message}"
            }
        } else if (!createAlertDevices && cd) {
            deleteChildDevice(alertDni(key))
            log.info "Removed Alexa trigger device '${label}'."
        }
    }
}

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
    announce(render(firstMessage ?: "Time to take %dog% out."),
             withAlertChild(firstAlertContacts, "walk1"), firstAlertSwitches)
    publishStatus(state.fedStatus)
}

def secondReminder() {
    state.walksPending = 0
    announce(render(secondMessage ?: "Time to take %dog% out."),
             withAlertChild(secondAlertContacts, "walk2"), secondAlertSwitches)
    publishStatus(state.fedStatus)
}

/** The user's chosen contacts plus this app's own trigger device for that slot. */
private List withAlertChild(def contacts, String key) {
    List out = (contacts ?: []) as List
    def cd = alertChild(key)
    if (cd) out << cd
    return out
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
    announce(render(nagMessage ?: "Don't forget to feed %dog%"),
             withAlertChild(nagAlertContacts, "nag"), nagAlertSwitches)
}

def backHomeHandler(evt) { deferredNag() }

def modeChangeHandler(evt) { deferredNag() }

private void deferredNag() {
    if (!state.remindWhenBack) return
    if (!someoneAround()) return
    if (!state.pendingMeal) { state.remindWhenBack = false; return }
    state.remindWhenBack = false
    announce(render(nagMessage ?: "Don't forget to feed %dog%"),
             withAlertChild(nagAlertContacts, "nag"), nagAlertSwitches)
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

/** Every selected speaker, each one only once even if picked in several fields. */
private List speakerPool() {
    return ((musicDevices ?: []) + (audioDevices ?: []) + (speechDevices ?: [])).unique { it.id }
}

/**
 * Speak on one device using the best command it actually has, and report back
 * which one was used. Drivers disagree wildly here: Sonos Player offers
 * playTextAndRestore(text, volume) as a custom command (it restores whatever was
 * playing), Echo Speaks offers playAnnouncement()/setVolumeSpeakAndRestore(), and
 * the bare SpeechSynthesis contract only guarantees speak().
 *
 * Returns a short description of what happened, for the app page and the log.
 */
private String speakOn(def d, String msg) {
    try {
        Integer vol = volumeSetting()
        rememberSource(d)
        if (vol && d.hasCommand("playTextAndRestore")) {
            d.playTextAndRestore(msg, vol)
            return "playTextAndRestore at volume ${vol}"
        } else if (vol && d.hasCommand("setVolumeSpeakAndRestore")) {
            d.setVolumeSpeakAndRestore(vol, msg)
            return "setVolumeSpeakAndRestore at volume ${vol}"
        } else if (useAnnouncement && d.hasCommand("playAnnouncement")) {
            d.playAnnouncement(msg)
            return "playAnnouncement"
        } else if (d.hasCommand("playText")) {
            d.playText(msg)
            return "playText"
        } else if (d.hasCommand("speak")) {
            d.speak(msg)
            return "speak"
        }
        log.warn "${d.displayName} has no usable speech command — skipped."
        return "NO USABLE SPEECH COMMAND"
    } catch (e) {
        log.warn "Speech failed on ${d.displayName}: ${e.message}"
        return "FAILED — ${e.message}"
    }
}

// ------------------------------------------------------- releasing speakers

/**
 * Note what a speaker was playing, and how loudly, just before we talk over it.
 *
 * The Hubitat Sonos Player driver stores a restore level and track number but
 * not a source URI (restoreURI stays 0), so when nothing was playing before the
 * announcement it has nothing to return to: the player is left sitting on the
 * hub's /tts/*.mp3 file at announcement volume, and on a soundbar that means the
 * TV audio never comes back. Capturing the URI ourselves is what makes the
 * release in releaseSpeakers() possible.
 */
private void rememberSource(def d) {
    if (!releaseAfterSpeaking) return
    try {
        String uri = null
        def td = d.currentValue("trackData")
        if (td) {
            def j = new groovy.json.JsonSlurper().parseText(td.toString())
            uri = j.transportUri ?: j.uri ?: j.trackUri
        }
        if (uri && uri.contains("/tts/")) uri = null      // already parked on a clip
        Integer level = (d.currentValue("level") ?: d.currentValue("volume")) as Integer
        Map pending = (state.pendingRelease ?: [:]) as Map
        pending["${d.id}"] = [uri: uri, level: level]
        state.pendingRelease = pending
        runIn(((releaseSeconds ?: 15) as Integer), "releaseSpeakers", [overwrite: true])
    } catch (e) {
        if (logEnable) log.debug "Could not read the current source on ${d.displayName}: ${e.message}"
    }
}

/**
 * Put each speaker back, but only if it is still parked on the announcement. A
 * driver that restored playback on its own is left alone.
 */
def releaseSpeakers() {
    Map pending = (state.pendingRelease ?: [:]) as Map
    state.pendingRelease = [:]
    pending.each { idStr, saved ->
        def d = speakerPool().find { "${it.id}" == "${idStr}" }
        if (!d) return
        try {
            if (!parkedOnClip(d)) {
                if (logEnable) log.debug "${d.displayName} resumed on its own — leaving it alone."
                return
            }
            String uri = saved?.uri
            if (uri && d.hasCommand("restoreTrack")) {
                d.restoreTrack(uri)
                log.info "Released ${d.displayName} back to its previous source."
            } else if (d.hasCommand("stop")) {
                d.stop()
                log.info "Released ${d.displayName} (stopped the clip; it had no previous source)."
            }
            Integer level = saved?.level as Integer
            if (level != null && volumeSetting() && d.hasCommand("setLevel")) d.setLevel(level)
        } catch (e) {
            log.warn "Could not release ${d.displayName}: ${e.message}"
        }
    }
}

/** True when the speaker's current track is still the hub's TTS clip. */
private boolean parkedOnClip(def d) {
    try {
        def td = d.currentValue("trackData")
        return td ? td.toString().contains("/tts/") : false
    } catch (ignored) {
        return false
    }
}

/** Configured volume, or the 35 default. 0 means "do not touch the volume". */
private Integer volumeSetting() {
    Integer vol = (speechVolume != null) ? (speechVolume as Integer) : 35
    return vol > 0 ? vol : null
}

// -------------------------------------------------------- speech self-test

def appButtonHandler(String btn) {
    if (btn == "btnTestSpeech") testSpeech()
}

private void testSpeech() {
    String msg = render(firstMessage ?: "Time to take %dog% out.")
    List pool = speakerPool()
    if (!pool) {
        state.speechTest = "No speakers selected — nothing to test."
        log.warn "Speech test: no speakers selected."
        return
    }
    List lines = pool.collect { d -> "&bull; <b>${d.displayName}</b> — ${speakOn(d, msg)}${activityNote(d)}" }
    state.speechTest = "Tested at ${nowText()} with \"${msg}\"<br>${lines.join('<br>')}"
    log.info "Speech test on ${pool.size()} speaker(s): ${lines.join(' | ')}"
}

/**
 * A speaker that has never reported, or has not reported in days, is almost
 * certainly why an announcement was silent — the command succeeds and nothing
 * comes out. Worth showing right next to the picker.
 */
private String activityNote(def d) {
    try {
        Date la = d.getLastActivity()
        if (la == null) return " <span style='color:red'>(no activity ever recorded — is this device alive?)</span>"
        Long days = ((now() - la.time) / 86400000L) as Long
        if (days >= 2) {
            return " <span style='color:red'>(last activity ${days} days ago — probably offline)</span>"
        }
        return " <span style='color:gray'>(last active ${la.format("MMM d h:mm a", location.timeZone)})</span>"
    } catch (ignored) {
        return ""
    }
}

private String speakerReport() {
    List pool = speakerPool()
    String head = pool ? "<b>Selected speakers:</b><br>" +
                         pool.collect { "&bull; ${it.displayName}${activityNote(it)}" }.join("<br>")
                       : "<span style='color:red'><b>No speakers selected</b> — announcements will be silent.</span>"
    return state.speechTest ? "${head}<br><br><b>Last test:</b><br>${state.speechTest}" : head
}

private String nowText() {
    return new Date().format("EEE MMM d, h:mm:ss a", location.timeZone)
}

private void announce(String msg, def contacts, def switches) {
    log.info "Announce: ${msg}"
    List pool = speakerPool()
    if (!pool) log.warn "Nothing to speak on — no speakers selected in section 5."
    List results = pool.collect { d -> "&bull; <b>${d.displayName}</b> — ${speakOn(d, msg)}${activityNote(d)}" }
    state.lastAnnounce = "${nowText()} — \"${msg}\""
    state.lastAnnounceDetail = results.join("<br>")
    if (results) log.info "Spoke on ${pool.size()} speaker(s): ${results.join(' | ')}"
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
    List children = alertDeviceSpecs().keySet().collect { alertChild(it) }.findAll { it }
    return ((firstAlertContacts ?: []) + (secondAlertContacts ?: []) + (nagAlertContacts ?: []) + children)
           .unique { it.id }
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
