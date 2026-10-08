# Automation

Other apps (Tasker, MacroDroid, Automate, ...) can control OpenDrop with
broadcast intents. It is **off by default**: turn on Settings → Automation →
Allow other apps. While it's off, OpenDrop ignores these commands and sends
no state broadcasts.

## Commands

Send a broadcast with the package set to `org.opendrop.app` (Android only
delivers custom broadcasts to an app's manifest receivers when the package
or component is named).

| Action | Extras | Does |
|---|---|---|
| `org.opendrop.app.action.SET_EQ` | `preset`: `reference`, `basshead`, `monitor` (or id `0`, `1`, `2`) | Switches the EQ preset |
| `org.opendrop.app.action.NEXT_EQ` | | Switches to the next preset |
| `org.opendrop.app.action.CONNECT` | | Connects to the remembered earbuds |
| `org.opendrop.app.action.DISCONNECT` | | Disconnects (and pauses auto-connect, like the button) |

Commands that can't apply right now (not connected, a model without
switchable presets) do nothing. Like the app, OpenDrop only sends EQ
commands the connected model is known to accept.

## State

OpenDrop broadcasts `org.opendrop.app.event.STATE` whenever the connection,
battery or EQ preset changes, and once when automation is turned on.

| Extra | Type | Value |
|---|---|---|
| `connection` | string | `connected`, `connecting`, `reconnecting`, `disconnected` or `failed` |
| `device` | string | Bluetooth name of the earbuds |
| `battery` | int | Percent, `-1` if unknown or not connected |
| `preset` | string | `reference`, `basshead`, `monitor`, or empty |

## Tasker example

Switch to Basshead when you open a music app:

1. Profile → Application → your music app.
2. Task → Add action → System → Send Intent:
   - Action: `org.opendrop.app.action.SET_EQ`
   - Extra: `preset:basshead`
   - Package: `org.opendrop.app`
   - Target: Broadcast Receiver

React to the battery: Profile → Event → System → Intent Received, action
`org.opendrop.app.event.STATE`; the extras are Tasker variables
(`%battery`, `%preset`, `%connection`).
