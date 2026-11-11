---
paths:
  - "app/src/**/vault/VaultMediaSource*.kt"
  - "app/src/**/vault/VaultSubsampling*.kt"
  - "app/src/**/vault/LoopWhatLasts*.kt"
  - "app/src/main/java/**/ViewActivity.kt"
  - "app/src/main/java/**/MediaViewerAdapter.kt"
  - "app/src/main/java/**/LocalPlayerViewModel.kt"
  - "app/src/main/java/**/MediaInfoBottomSheetDialog.kt"
  - "app/src/main/java/**/utils/**"
  - "app/src/main/java/androidx/**"
---

# Viewer — component brief

The viewer: Glimpse's `ViewActivity`, its pager (`MediaViewerAdapter`), its player (`LocalPlayerViewModel`, Media3's
controls) and its info sheet (`MediaInfoBottomSheetDialog`), and what this app put under them: `VaultMediaSource`
(a video read through a descriptor encri serves), `VaultSubsampling` (zoom in tiles from the photo itself) and
`LoopWhatLasts`. The viewer's *Share*, *Open with* and *Delete* are `share-and-delete`'s. This brief is level 1;
full records are in `docs/decisions/`.

## Rules that hold here
- **The viewer is not exported, and takes its request from this app's own extras.** Glimpse's `VIEW` and `REVIEW`
  filters would have been a way to probe the vault, and `ACTION_REVIEW_SECURE`, which showed media over the keyguard,
  is gone. (I3)
- **It opens at the item tapped**, read from the request itself (`initialPositionIn`), not from a separate flow. (I4)
- **It closes itself when the vault locks or the session goes**, because an open descriptor goes on serving across a
  lock (encri D103), so a video would otherwise play on. (I3)
- **A full image is read through a descriptor and decoded here, and a video's stream is played by ExoPlayer over
  `VaultMediaSource`, seeking by position.** This process holds no keys, so a malicious image reaches this app's
  memory and not the vault's; encri D110's isolated decoder protects encri, not this. (I4)
- **Reading is off the main thread.** The viewer reads the file before the info sheet is made, not in the dialog's
  constructor, because a read of a descriptor encri serves can take a while. (I4)
- **Zoom reads the photo itself.** `VaultSubsampling` gives ZoomImage, for a vault document, `VaultImageSource`,
  which opens the document through the link every time ZoomImage asks, so ZoomImage cuts the photo into tiles and
  decodes those at the zoom asked. Any other model is left to ZoomImage, and ZoomImage no longer asks Glide for the
  photo as a file. (I15)
- **The tiles are held in Glide's memory and nowhere else** (`GlideTileImageCache`), which the app clears at every
  lock. (I15, I3)
- **A source's key is made up**, `encri-vault-source-` and a random UUID, because ZoomImage keys its tiles by it and
  may log it. A photo opened again is a new source, decoded again. Never derive it from the document's id. (I15, encri
  D8, D85)
- **A video reads on across the end of its session.** When a read fails, or an open finds no session,
  `VaultMediaSource` opens the document again where the read stopped, through the link's session or the next one,
  waiting up to the five seconds I11 waits. (I12)
- **A clip loops only once it is ready and lasts** (`LoopWhatLasts`); every clip starts unlooped. (I4, amended)
- **ExoPlayer has no cache, and Media3's DASH, HLS, RTSP and SmoothStreaming modules are gone.** (I3)
- **The info sheet shows a location as numbers and nothing else**: nothing goes to `Geocoder`, which on most phones
  is a network service, or to a maps app. Like every dialog, it goes through `SecureWindows.secure`. (I3)
- **A deleted video's saved playback position is removed**, before the pager moves on and would save it again. (I11)

## Traps already paid for
- The viewer opened at the newest item rather than the one tapped: the library, already in memory, beat a separate
  flow carrying the starting point, a race MediaStore's slower load had hidden. Found by hand on the A20, not by
  the unit tests. (I4)
- A clip whose duration is 0 spun ExoPlayer's repeat mode: on the A20 it started a decoder 132 times in six seconds.
  (I4, amended)
- Before I15, ZoomImage asked Glide for a vault photo as a file with `onlyRetrieveFromCache`; the disk cache is empty
  by design, so zoom magnified the screen-sized decode, and every photo opened put its id in the log. (I8)
- A video behind *Allow in encri* lost its descriptor: an allowed consent ends the session, and the descriptor gave
  the 16 KiB it had buffered and then an `IOException`. (I12)
- The info sheet's dialog had no `FLAG_SECURE`, so its name, size, date and coordinates would have been in a screen
  recording (its flags on the S9 were `0x81810102`). (I3, amended)

## Measured, not assumed
- On the A20: a video playing to 00:07 of 00:08, and a seek to 00:03 showing a different frame. (I4)
- A 4000x3000 photo opened, read and decoded for the screen on the A20: 16–24 ms, 188–209 ms (15–16 MiB/s), 177–183
  ms; an 8000x6000 photo: about 20 ms, 0.94–1.02 s (12–13 MiB/s), 456–464 ms (I8). The 4000x3000 read: S9 23 MiB/s, G6
  14–20, S24 30 (I9).
- With the 8000x6000 photo open, the A20's app held 115–133 MB (I8); zoomed on the G6, 254 MB, 153 MB of it graphics
  (I15).
- On the G6, the mean difference between neighbouring pixels in a central crop of the 8000x6000 photo: 1.88 unzoomed;
  0.60 after a double tap before I15, a magnified screen decode; **11.84** with it. Logcat held no vault URI and no
  ZoomImage error. (I15) The minified release build: 11.62 against 1.75. (I18)
- On the A20, release build: the 8-second clip still playing at 12 seconds, so looped; the clip of no length stopped,
  two decoders started rather than 132. (I18)
- `VaultMediaSource`'s reopening is checked by unit tests, not with a video on a phone, which would need one longer
  than the player's buffer. (I12)

## Decisions
| | Why it matters here |
|---|---|
| I4 | A full image and a video decoded here; reading off the main thread; the start from the request; two veto flags |
| I15 | Zoom from the photo itself; tiles in Glide's memory; a made-up key |
| I12 | A descriptor fails after an allowed consent, and the source opens it again |
| I8 | Why ZoomImage did not subsample; document ids in the log |
| I3 | The viewer unexported and closing at a lock; the location as numbers; the dialog secure; three veto flags |
| I11 | A deleted video's saved position |
| I18 | The release build zooms and loops |

## Open here
- A full image is decoded in this app's process, not an isolated one. It holds no keys, but it does hold whatever
  else the user has opened; flagged. (I4)
- Unmeasured: a photo with an EXIF rotation, which ZoomImage reads from the source; memory over a long session of
  zooming (I15); a motion photo; a large video over the proxy descriptor (I4); deleting a video on a phone (I11).

## Read next
- Related briefs: `.claude/rules/share-and-delete.md`, `.claude/rules/vault-library.md`,
  `.claude/rules/glimpse-screens.md`
- Full records: `docs/decisions/INDEX.md`
