# I9 — The S9 at 5,000, a grant of one folder, and a grant whose folder is gone
*2026-11-03 · accepted at the user's direction ("Let's test the other 2 phones as well"). **Amends I5** on where a test
phone's encri comes from, and **raises a question for encri-api** (below).*

**encri on the S9 and the G6 is its release build, 0.1.0**, signed by the certificate `EncriTrust.RELEASE_CERTIFICATES`
pins, which the user named and adb installed. Nothing of it is in this repository. **Their vaults are test vaults**,
made on encri's own first screen with passphrases generated for them and kept outside this repository, as the user
asked ("These are tests. You can generate and manage the passphrases."). Creating a vault asks for the phone's own
fingerprint or pattern, which only the user can give, so a new phone's vault takes the user once. The G6's waits on
that, and is not measured here. *Amended 2026-11-03: the user gave it the same day, and the G6 is measured below.*

**The S9 (API 29) against the A20 (API 30, I8)**, with the same two libraries, seeded and removed the same way, and
the G6 (API 28) added the same day, and the S24 (API 37) on 2026-11-04:

| | A20 | S9 | G6 | S24 |
|---|---|---|---|---|
| Walk of about 2,080 photos in about 50 folders, again | 648–672 ms | 285–337 ms | 947–1,356 ms | 277–353 ms |
| Walk of about 5,080, with 3,000 in one folder, again | 1,056–1,164 ms | 509–515 ms | 513–602 ms | 125–127 ms |
| The folder of 3,000, listed | 362–442 ms | 173–196 ms | 169–253 ms | 35–37 ms |
| A folder of about 50, listed | 11 ms | 5 ms | 14–24 ms, or 5 with the clock up | 3–5 ms, or 1 |
| A 180x180 preview, one at a time (median) | 62–68 ms | 46–50 ms | 41–55 ms | 11–35 ms |
| Previews, four at a time | 54–56 a second | 62–64 a second | 65–71 a second | 206–212 a second |
| A 4000x3000 photo read | 15–16 MiB/s | 23 MiB/s | 14–20 MiB/s | 30 MiB/s |
| Twenty flings up Reels: janky, 99th percentile | 7–10%, 30–48 ms | 0.6–2%, 14–24 ms | 59–88%, 57–65 ms | 0.1%, 21–23 ms |

On the S9 the app held 188 MB during the flings at 5,082 photos, 66 MB of it graphics at its 1080x2220. The walk's
shape is the A20's at about half the cost, so I8's decision stands.

**On the G6 a walk costs what the CPU's clock allows.** The library of 2,080 walked slower than on the A20, and the
one of 5,080 in half the time. A walk of small folders is a string of short requests to encri with a wait between
each, and the G6's `interactive` governor leaves the clock low through it; the folder of 3,000 keeps a core busy for
200 ms, and the folders after it list at the clock that raised. With nothing changed but a shell loop on the phone
reading the clock every 200 ms, the same walk of 2,080 took 325 to 471 ms and a folder of fifty 5 ms, the S9's
figures, and alone again 0.95 to 1.36 seconds. A walk with a finger on the screen may be quicker, since the governor
boosts on touch; not measured. Listing a few folders at a time would overlap the waits (`ROADMAP.md`; *amended by I14:
it does, and the walk lists four*); I8's decision
stands. The 5,080 were measured with the home screen behind, whose live wallpaper may have raised the clock too, and
the 2,080 both ways, alike.

**Reels drops most frames on the G6**: 59 to 88% janky over twenty flings, the median frame 19 to 24 ms, at its
panel's own 1440x2880. At a temporary 1080x2160, near the S9's size, 48% were. In the frames the platform kept, the UI
thread's 90th percentile was 24.5 ms and drawing waited 13.4 ms for a buffer: a 2017 phone's CPU and GPU at its own
resolution, both, rather than something the app does on this phone alone. The app held 254 to 276 MB, 127 to 132 MB
of it graphics.

**The S24 (API 37) walks 5,080 photos in an eighth of a second, and draws Reels at 120 Hz missing about one frame in
a thousand.** That janky count is Android's own since API 31, frames that missed the deadline the compositor gave
them. The older phones count against a fixed budget, and by that count the S24's frames are 64 to 87% janky at 120 Hz,
which compares with nothing. Its walk has the G6's shape at a smaller scale: the library of 2,080 walked slower than
the one of 5,080, its folders of fifty listing in 3 to 5 ms against 1. Previews came three times as fast as on any
other phone. The app held 292 to 342 MB during the flings, 140 to 166 MB of it graphics at 1440x3120. Walks were
measured with Settings in front, as on the G6. Nothing here argues against I8's decision.

**A grant of one folder reaches that folder and nothing else.** Asked on encri's consent screen for *one folder only*
and given the first library, the walk found its 2,082 photos in 43 albums across its 50 folders, and nothing of the
3,000 beside it. **Turning the app out takes the gallery away**: with the gallery in the background, encri's *Turn out
Encri Images*, and back in the same process, the gate asked for permission.

**Found: a grant whose folder is deleted.** encri lets the app in no longer: a cold start answers *not authorized*.
But a walk took the missing root for a folder deleted under it and showed an empty gallery, which says the folder is
empty. **The root not found is now the session's end**, so the link asks encri again and the gate says why there is
nothing; seen on the S9 after the fix. **And a gallery in front hears nothing at all**, because the change is to the
root's parent, which the grant does not reach, so it goes on showing the deleted folder's albums until something walks
again. `PROTOCOL.md` says a session ends when the app ends it, its process dies, the user turns it out or turns access
off, and not when its folder goes. **That is a question for encri-api, not decided here**: whether the session should
end, or the app be told. Polling for it was rejected in I7. *Amended by I12: what encri does was seen, and the
question stands.*

**`scripts/device/scroll` drew no frames on the S9 at first.** It draws at an override of 1080x2220 on a 1440x2960
panel, and input is given in the override, so every fling began below the screen. The script takes the override now.

**Not measured, and not to be assumed:** the G6, until its vault exists (*amended: measured above*); API 27 and 33+;
video; 20,000 photos.

<!-- amendments: generated by scripts/docs/decision-index.py; do not edit -->

## Amended since, as recorded in `amendments.md`

- **I4 and I9's walk of one folder at a time is amended by I14 (2026-11-04).** The walk lists a level of the tree at a
  time, four folders of it at once. I8's decision, to walk rather than ask encri-api for a query across folders,
  stands.
- **I9's question for encri-api, what becomes of a session whose folder is deleted, is amended by I12 (2026-11-03).**
  What encri does was seen, and the question stands.
- **I9's G6, which waited on its vault, is amended the same day (2026-11-03).** The user gave it, and the G6 is
  measured in I9.

<!-- /amendments -->
