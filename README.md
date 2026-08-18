# Dog Feeding & Walk Reminder (Hubitat)

A Hubitat app that replaces two webCoRE pistons ("Dog Fed Reminder" + "Dog Fed
Notification") and adds Alexa announcements to take the dog out after he eats.

## Personalized messages

Set the **dog's name** in section 0 and use these tokens in any message or
status field. Every spoken announcement, push, nag and status string runs
through the same substitution:

| Token | Becomes |
|---|---|
| `%dog%` | The dog's name, or `the dog` if the field is blank |
| `%day%` | Day of week of the last feeding (e.g. `Tuesday`) |
| `%time%` | Clock time of the last feeding (e.g. `7:36 AM`) |

Defaults: `%dog% was fed on %day% at %time%`, `%dog% has not had breakfast`,
`Don't forget to feed %dog%`, `Time to take %dog% out. First trip after eating.`
With two dogs, type `Rex and Sadie` as the name and edit the verbs in the
message fields to match — every string is editable.

## What it does

1. **Records the feeding.** Any of a bowl tilt/contact sensor, bowl vibration
   sensor, three-axis orientation sensor, virtual "Dog Fed" switch (Alexa/Siri/
   dashboard), or a button sets the status to `Fed on Tuesday at 7:36 AM`.
   Repeat triggers inside the cooldown window are ignored.
2. **Resets the status twice a day.** At the breakfast reset time the status
   becomes `Dogs have not had breakfast`; at the dinner reset time,
   `Dogs have not had dinner`. The string always says what is still owed.
3. **Nags at meal times.** If the meal has not happened by the nag time, the nag
   is spoken on the same Echo Speaks / TTS devices as the walk announcements
   (plus push, plus its own optional virtual contact/switch for an Alexa
   Routine). If nobody is home — or the hub is in a skipped mode — it defers and
   fires as soon as someone returns or the mode changes back.
4. **Announces to take him out.** After a feeding, two announcements at
   configurable offsets — default **+15 min** and **+60 min**. Anything pending
   is cancelled when he actually goes out, detected by a **motion sensor**
   (e.g. the garage, if that is the way out), a door contact, or a switch.

## The companion device

The app creates one child device using the **Dog Feeding Status** driver — no
pairing, no manual add. It gives you what a Hub Variable cannot:

| | |
|---|---|
| `status` attribute | `Rex was fed on Tuesday at 7:36 AM` — use a Dashboard **Attribute** tile |
| `lastFed`, `pendingMeal`, `walksPending` | for tiles, rules, and event history |
| `Fed` / `Let out` / `Speak status` commands | Dashboard buttons for the three manual actions |
| Switch capability | share it to Alexa/HomeKit — "Alexa, turn on Rex Feeding Status" records a feeding, and "Alexa, is Rex Feeding Status on?" answers |

Turn it off in section 6 if you would rather use only a Hub Variable.

### Motion as the let-out signal

Garage motion is a good proxy for "the dog went out" but also fires whenever a
person walks through. Two guards handle that: the signal is ignored inside the
**grace window** (default 5 minutes after feeding) and ignored entirely when no
walk reminder is pending. The device's `Let out` button always counts, grace
window or not.

## Getting Alexa to speak

**Option A — Echo Speaks (recommended).** Install Echo Speaks from HPM and pair
it. Each Echo becomes a `speechSynthesis` device; select them under
*Alexa / speech output → TTS speakers*. The app sends the real message text and
uses `playAnnouncement()` when the device supports it. Set *Speak at this volume
and restore* if you want announcements louder than whatever is playing.

**Option B — Alexa Routines (no Echo Speaks).** Create a **Virtual Contact
Sensor** per message (e.g. `Dog Out Reminder 1`, `Dog Out Reminder 2`,
`Feed The Dogs`), expose them in the Hubitat Amazon Alexa Skill app, then in the
Alexa app build a Routine: *When → contact opens → Alexa Says → "Take the dog
out."* Select each virtual in the matching field of section 5. They auto-reset
after *Auto-reset those virtual devices after (seconds)*.

Both options can run at once. Push notifications to the Hubitat mobile app are
independent of either.

## Install

Via **HPM → Install → Import a ZIP or custom repository**, using:

```
https://raw.githubusercontent.com/drbbton/HubitatDogFeeding/main/packageManifest.json
```

HPM installs both files. Manually, install the driver first
(**Drivers Code → New Driver → Import** `dogfeedingstatusdriver.groovy`), then
the app (**Apps Code → New App → Import** `dogfeedingapp.groovy` → **Add User
App**) — the child device cannot be created until the driver exists.

## Setup checklist

- [ ] Virtual switch `Dog Fed` (Virtual Switch driver) — for "Alexa, turn on Dog Fed"
- [ ] Virtual switch `Dog Let Out` — optional, cancels pending announcements
- [ ] Virtual contacts for Alexa Routines — only if not using Echo Speaks
- [ ] Hub Variable (String) named e.g. `DogLastFed` — optional, for dashboards
- [ ] Breakfast/dinner reset times (webCoRE used 12:01 AM and 12:01 PM)
- [ ] Nag times (webCoRE used 7:30 AM and 6:00 PM)
- [ ] Presence sensors and/or skipped modes for the "nobody home" case

## Notes / gotchas

- Whether a meal is still owed is tracked as app state (`pendingMeal`), not by
  string-matching the status text, so you can reword any message field at any
  time without breaking the nags.
- The three-axis input subscribes to every `threeAxis` event, which is how the
  webCoRE piston's "orientation changes" trigger behaved. The cooldown is what
  keeps a jostled bowl from re-arming the timers.
- `runIn` is used with `overwrite: true`, so a second feeding inside the same
  cycle reschedules rather than stacking announcements.
