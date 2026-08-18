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

## Getting it spoken out loud

**Option A — Sonos or any music player (recommended).** Fully local: no Amazon
login, no cookie, nothing that expires. Pick the players under *Speech output →
Sonos and other music players*. The app calls `playTextAndRestore()`, so the real
text is spoken and whatever was playing comes back. Set *Speak at this volume and
restore* to be heard over music.

> Sonos is selected by the **MusicPlayer** capability, not AudioNotification. The
> Hubitat *Sonos Player* driver declares MusicPlayer and exposes
> `playTextAndRestore(text, volume)` as a custom command, so a
> `capability.audioNotification` picker comes up empty on a hub full of Sonos
> gear. Every selected speaker is spoken to exactly once — `speakOn()` picks the
> best command that device actually has, so mixed Sonos/Echo/Chromecast setups
> work without per-device configuration.

**Option B — Echo Speaks.** Each Echo becomes a `speechSynthesis` device; select
them under *TTS speakers*. Same dynamic text, but Echo Speaks authenticates with
a scraped Amazon cookie that breaks every few months and has to be refreshed — if
Echo Speaks shows no child devices, that is what happened.

**Option C — Amazon Echo Skill + Alexa Routines.** Zero maintenance, fixed text.
Switch on *Create three virtual contact sensors for Alexa Routines* and the app
creates `<Dog> Out Reminder 1`, `<Dog> Out Reminder 2` and `Feed <Dog>` for you.
Then, once:

1. Hubitat → **Apps → Amazon Echo Skill** → add those three devices → Done.
2. Alexa app → **More → Routines → +** → *When: Smart Home → <that contact
   sensor> → opens* → *Add action: Alexa Says* (or **Announcement**) → type the
   phrase → pick the Echos → Save. Repeat for the other two.

They reset themselves after *Auto-reset those virtual devices after (seconds)*,
so they are ready to fire again.

Any combination can run at once — A for the real wording, C as the backup that
keeps working when a cookie dies. Push notifications are independent of all three.

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

The app creates every device it needs — you do not have to add virtual devices by
hand.

- [ ] Bowl sensor and/or an existing `Dog Fed` virtual switch as the trigger
- [ ] Breakfast/dinner reset times (webCoRE used 12:01 AM and 12:01 PM)
- [ ] Nag times (webCoRE used 7:30 AM and 6:00 PM)
- [ ] Presence sensors and/or skipped modes for the "nobody home" case
- [ ] Motion sensor on the way out (garage) as the let-out signal
- [ ] Speakers — Sonos preferred, see the options above
- [ ] If using Alexa Routines: share the three created contacts to the Amazon
      Echo Skill and build one Routine each

## Notes / gotchas

- Whether a meal is still owed is tracked as app state (`pendingMeal`), not by
  string-matching the status text, so you can reword any message field at any
  time without breaking the nags.
- The three-axis input subscribes to every `threeAxis` event, which is how the
  webCoRE piston's "orientation changes" trigger behaved. The cooldown is what
  keeps a jostled bowl from re-arming the timers.
- `runIn` is used with `overwrite: true`, so a second feeding inside the same
  cycle reschedules rather than stacking announcements.
