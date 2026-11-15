---
paths:
  - "app/src/**/vault/VaultLink*.kt"
  - "app/src/**/vault/*VaultSession.kt"
  - "app/src/**/vault/ClientOpener.kt"
  - "app/src/**/vault/VaultHost.kt"
  - "app/src/**/vault/Opening.kt"
  - "app/src/**/vault/VaultGate*.kt"
  - "app/src/main/java/**/MainActivity.kt"
  - "app/src/main/java/**/ImagesApplication.kt"
  - "app/src/androidTest/**/GateOnPhoneTest.kt"
---

# Vault link and gate — component brief

The app's one session with encri, and what the gallery shows while it has none: `VaultLink` (the link, its
`LinkState`, and how it hears an answer), `VaultSession` (the part of an encri session this app uses, which the unit
tests fake as `FakeVaultSession`), `ClientOpener` (which asks the encri installed on the phone through encri-client),
`Opening` (what asking came to) and `VaultGate` (the gate's message and action for each state), drawn by Glimpse's
`MainActivity` and wired in `ImagesApplication`, which is the `VaultHost` the editor reaches the link through (I22).
This brief is level 1; full records are in `docs/decisions/`.

## Rules that hold here
- **One session for the life of the process, held by the application**, not by a screen, so a rotation does not end
  it. A session encri ends is asked for again at once, so the gate says why there is none rather than showing a
  vault that has gone. (I7, encri-files F2)
- **Every request to encri goes through the link, and its answer is heard as well as encri's events.** `LOCKED` is
  the lock; `ENDED` or `DISCONNECTED` is the end, which closes the session and asks for another; any other answer, a
  failure included, is a vault open enough to answer. encri's events alone can be missed: the client drops its
  oldest past 64 (encri-api L12). (I7)
- **An answer counts only if the link's state has not changed since it was asked** (the link's `generation`). A
  preview asked before a lock cannot unlock the gate by arriving after it, nor one asked before an unlock lock it
  again. A walk is asked of the session it began for, and publishes nothing once the link has moved on. (I7)
- **A link that believes the vault locked asks again.** `open()` runs on every return to the gallery and after
  encri's unlock screen answers; for a locked session it asks `grant()`, encri's `refreshGrant`, one small read. It
  never polls while the gate shows. (I7)
- **The gate asks encri for `READ` alone.** `DELETE`, and `CREATE` and `WRITE` for a copy, are asked for when first
  used, each with every right the grant holds (`Rights`; `share-and-delete`, `editor`), so the gate never asks every
  user for a right most may never use. (I3, I11, I22)
- **Nothing is shown while locked.** `MainActivity` hides the whole gallery behind the gate, with one message and
  one button. At a lock or the session's end the library forgets everything, and `ImagesApplication` clears Glide's
  memory, zoom's tiles with it (`VaultGlide.forget`). The viewer closes itself (`viewer`). (I3, I15)
- **Each state has one message and one action** (`gateOf`): connecting; locked, *Unlock*; not let in and access
  off, *Allow*, encri's consent screen; not installed, not trusted, too old and unreachable, *Retry*. (I3, I7)
- **encri's screens are started for a result**: encri learns which app asked from `getCallingPackage`, which only a
  start for a result sets. (encri D120, I11)
- **A consent the user allows replaces the grant with exactly what was asked, and ends the session.** The link asks
  for the next at once; whoever asked waits for it, up to five seconds (`Rights.ALLOWING_MILLIS`). (I11, I12, I22,
  encri-api L23)
- **A grant's root not found is the session's end**, not a folder deleted mid-walk, so the link asks encri again and
  the gate says why there is nothing. (I9, I12)
- **Trust is the client's default**: an encri signed by encri's release key, or by this app's own key, which is how
  a debug build finds a debug encri made on the same machine. Nothing of encri's repository is used to test against
  it. (I5)

## Traps already paid for
- A missed lock left the gallery showing until the next walk. A missed unlock left the gate on *The vault is locked*
  until the process died, because `open()` did nothing for a session it already held, and encri's unlock screen
  answers at once when the vault is already open, so *Unlock* changed nothing. (I7)
- A grant whose folder was deleted showed an empty gallery, which says the folder is empty, where encri no longer
  lets the app in. (I9)
- Polling while locked was rejected: it would ask encri something every few seconds for as long as the gate shows.
  So were events alone. (I7)
- The app did not start below API 30 at all; the gate's own device test found it (`glimpse-screens`). (I6)

## Measured, not assumed
- On the A20 (API 30), debug build, against encri 0.1.0: the gate through locked, not allowed and open; encri's
  consent screen; `force-stop` of encri during playback and *Lock the vault* with a photo open, each closing the
  viewer and showing the gate. (I4)
- `GateOnPhoneTest` passes on the G6 (API 28) and the S9 (API 29), which then had no encri, and on the A20 (I6);
  under Waydroid at API 33 (I3, amended); and on an API 27 emulator, showing *encri is not installed* (I18).
- The gate's words of 2026-11-11 (`CHANGELOG.md`), on a device: *The vault is locked.* with *Unlock* under Waydroid,
  encri installed and no vault made yet (2026-11-11), and on the A20 before its vault was made again; and on the A20
  with its new vault (2026-11-12), `vault_gate_not_authorized`, *Encri Images has not been let into your vault yet,
  …*, with *Allow in encri*. Each read as `strings.xml` has it.
- With the gallery in the background, encri's *Turn out Encri Images*, and back in the same process: the gate asked
  for permission. (I9)
- An allowed consent: the old session hears `Ended` within three seconds and answers `ENDED` to everything, its grant
  included; a refusal or a back-out changes nothing. On the G6, the A20 and the S24. (I12)
- A grant whose folder another app deletes: over sixty seconds the session heard nothing and still counted itself
  open; its root then answered `NOT_FOUND`, `refreshGrant` `NOT_AUTHORIZED`, and a new session was not let in. (I12)
- I7's handling is checked by unit tests alone, ten then and 71 in all; not on a phone, where a real lock would need
  the vault's passphrase to undo. (I7)

## Decisions
| | Why it matters here |
|---|---|
| I7 | An answer is heard as an event; an answer counts only if nothing changed; a locked session is asked again |
| I3 | `READ` alone at the gate; nothing shown while locked; three veto flags |
| I12 | A consent allowed replaces the grant and ends the session; a deleted folder's session is told nothing |
| I9 | A grant of one folder reaches that folder alone; its root gone is the end |
| I11 | `DELETE` asked for when first used, and the five-second wait for the next session; two veto flags |
| I5 | Tested against an installed encri, never encri's repository |
| I6 | `GateOnPhoneTest`; the app's tests reach encri through the app's own grant |
| I4 | The gate checked by hand on the A20 |
| I18 | The gate on API 27 |
| I15 | Zoom's tiles are in Glide's memory, cleared at a lock |
| I22 | `create` and `write` on the session; `VaultHost`; every right held asked for again |

## Open here
- encri 0.1.0 does not end the session of a grant whose folder is deleted, which encri-api L23 promises and encri
  files as encri B143. Until an encri does, a gallery in front shows the folder until something walks again
  (`ROADMAP.md`'s S2). (I9, I12)
- A lock or an unlock reaching the link through an answer rather than an event is unmeasured on a phone. (I7)
- The gate asks for `READ` alone, so a user turned out and let in again loses `DELETE` until they next delete;
  flagged for veto. (I11)
- What each state's message says is to read the same in every companion (the suite's `docs/documentation.md`,
  *Text in the apps*); the words are in `strings.xml`, which `glimpse-screens` covers. Of the words of 2026-11-11,
  *locked* and *not let in yet* are seen on a device; *access turned off*, *not installed*, *not trusted* and *too
  old* (`vault_gate_access_off`, `_not_installed`, `_not_trusted`, `_too_old`) are not.

## Read next
- Related briefs: `.claude/rules/vault-library.md`, `.claude/rules/share-and-delete.md`,
  `.claude/rules/device-testing.md`
- Full records: `docs/decisions/INDEX.md`; the contract: encri-api's `PROTOCOL.md` and its records (L12, L23)
