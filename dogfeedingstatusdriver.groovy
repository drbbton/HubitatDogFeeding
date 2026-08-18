/**
 *  Dog Feeding Status
 *
 *  Companion child device for the "Dog Feeding & Walk Reminder" app. The app
 *  creates it automatically; you do not pair or add it by hand.
 *
 *  Why it exists
 *    - Gives you a real device with an event history and Dashboard tiles
 *      (Attribute tile on `status`), which Hub Variables cannot do.
 *    - Exposes Switch capability, so it can be shared to Alexa/HomeKit and
 *      turned on by voice ("Alexa, turn on Dog Fed") to record a feeding.
 *    - Buttons for the two things you do by hand: he ate, he went out.
 */

metadata {
    definition(
        name: "Dog Feeding Status",
        namespace: "drbbton",
        author: "drbbton",
        importUrl: "https://raw.githubusercontent.com/drbbton/HubitatDogFeeding/main/dogfeedingstatusdriver.groovy"
    ) {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"          // on() = record a feeding, auto-returns to off

        attribute "status", "string"        // "Rex was fed on Tuesday at 7:36 AM"
        attribute "lastFed", "string"       // ISO-ish local timestamp, or "never"
        attribute "pendingMeal", "enum", ["breakfast", "dinner", "none"]
        attribute "walksPending", "number"  // take-him-out announcements still scheduled

        command "fed"
        command "letOut"
        command "speakStatus"
    }
    preferences {
        input "txtEnable", "bool", title: "Enable descriptive text logging", defaultValue: true
    }
}

def installed() {
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "status", value: "unknown")
    sendEvent(name: "lastFed", value: "never")
    sendEvent(name: "pendingMeal", value: "none")
    sendEvent(name: "walksPending", value: 0)
}

def updated() { }

// -------------------------------------------------- commands (user / voice)

def on() {
    sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} feeding recorded")
    fed()
    runIn(2, "autoOff")
}

def off() {
    sendEvent(name: "switch", value: "off")
}

def autoOff() { off() }

def fed() {
    if (txtEnable) log.info "${device.displayName}: fed"
    parent?.childFed(device.displayName)
}

def letOut() {
    if (txtEnable) log.info "${device.displayName}: let out"
    parent?.childLetOut(device.displayName)
}

def speakStatus() {
    parent?.childSpeakStatus()
}

// ------------------------------------------------ called by the parent app

def setStatus(String status, Long lastFedMs, String pendingMeal, Integer walksPending) {
    sendEvent(name: "status", value: status, descriptionText: "${device.displayName} status: ${status}")
    sendEvent(name: "lastFed",
              value: lastFedMs ? new Date(lastFedMs).format("yyyy-MM-dd h:mm a", location.timeZone) : "never")
    sendEvent(name: "pendingMeal", value: pendingMeal ?: "none")
    sendEvent(name: "walksPending", value: walksPending ?: 0)
    if (txtEnable) log.info "${device.displayName}: ${status}"
}
