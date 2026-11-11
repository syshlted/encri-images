# I7 — An answer says as much as an event: the link hears a lock, an unlock or the end in what encri answers
*2026-11-03 · accepted (step 2 of the integration plan, "harden reading")*

**encri's events can be missed.** The client drops its oldest past 64 rather than hold encri's binder thread
(encri-api L12), so a `Locked`, an `Unlocked` or an `Ended` could go unsaid, and the link believed whatever it heard
last. Two holes followed. A missed lock left the gallery showing until the next walk. A missed unlock left the gate on
*The vault is locked* until the process died, because `open()` did nothing for a session it already held, and encri's
unlock screen answers at once when the vault is already open, so pressing *Unlock* changed nothing.

**So every request through the link is heard as well.** `LOCKED` is the lock; `ENDED` or `DISCONNECTED` is the end,
which closes the session and asks for another; any other answer, a failure included, is a vault open enough to answer.
**An answer counts only if the link's state has not changed since it was asked**, so a preview asked before a lock
cannot unlock the gate by arriving after it, nor one asked before an unlock lock it again. The walk is asked through the
link too, so a walk that finds the vault locked is the gate's to show rather than the grid's error, and a walk whose
session the link has moved on from publishes nothing.

**A link that believes the vault locked asks again.** `open()` runs on every return to the gallery and after encri's
unlock screen answers; for a locked session it asks for the grant (`VaultSession.grant`, encri's `refreshGrant`, one
small read), and that answer is heard like any other. The interface still only reads.

*Rejected:* polling while locked, which would ask encri something every few seconds for as long as the gate shows.
*Rejected:* events alone, as before.

**Checked by unit tests only** (ten new, 71 in all). Not on a phone, where a real lock would need the vault's passphrase
to undo, and these runs have not been given it.
