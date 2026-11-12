---
paths:
  - "app/src/**/vault/VaultDataSource*.kt"
  - "app/src/**/vault/VaultLibrary*.kt"
  - "app/src/**/vault/WalkLibrary*.kt"
  - "app/src/**/vault/VaultUris*.kt"
  - "app/src/**/vault/VaultGlide*.kt"
  - "app/src/main/java/**/datasources/**"
  - "app/src/main/java/**/repository/**"
  - "app/src/main/java/**/models/**"
  - "app/src/androidTest/**/WalkMeasurement.kt"
---

# Vault library — component brief

The vault as Glimpse's data source: `VaultDataSource` behind Glimpse's `MediaDataSource` seam, `walkLibrary` and
`VaultLibrary` (the walk, and the library held in memory), `VaultUris` (the URIs a document and a folder are known by
inside this app) and `VaultGlide` (Glide with nowhere to write, encri's previews in the grid, and a full image read
from the vault), with Glimpse's repository and models over them. `WalkMeasurement` measures the walk on a phone.
This brief is level 1; full records are in `docs/decisions/`.

## Rules that hold here
- **Glimpse's `MediaDataSource` is the seam.** `VaultDataSource` answers Reels, Albums and an album from the vault;
  `LocalDataSource`, `Query` and everything over MediaStore are gone. (I4)
- **The vault is walked, because the contract has no query across folders**: a level of the tree at a time, four
  folders of it at once (`FOLDERS_AT_ONCE`, as many as Glide asks previews of at once). The library is sorted once it
  is whole, so the order answers come in does not matter, and a failure anywhere ends the walk and says so. (I4, I14)
- **When it walks**: once per session, on every return to the gallery, since encri's change notices are best
  effort, and after a burst of those notices has been quiet for 500 ms (`CHANGES_QUIET_MILLIS`). A walk that finds
  the same vault publishes nothing, so a return does not redraw. (I4)
- **A walk is published whole.** Reels is newest first across every folder, so a walk published a folder at a time
  would reorder itself as it finished. (I8)
- **An album is a folder that directly holds a photo or a video.** A folder deleted mid-walk is not a failure; the
  grant's root not found is the session's end, which the link hears. (I4, I9)
- **Dates are the vault's**, a document's last change, not when a photo was taken: reading EXIF for every photo is a
  cost a walk cannot carry. (I4)
- **Inside this app a document is a `Uri`, `encri-vault://document/<id>`**, because Glimpse's screens identify
  everything by one. No provider answers it and no other app could open it. It carries encri's opaque id (encri D15),
  and it is the only thing a saved playback position stores. (I4)
- **A grid cell is encri's preview, and never a decode here** (`VaultGlide.PREVIEW`), so a grid of unknown images
  parses nothing in this process. A full image is read through a descriptor and decoded here (`viewer`). (I4, encri
  D112)
- **Nowhere to write a picture.** `VaultGlide` installs a no-op `DiskCacheAdapter`, and its fetcher answers
  `DataSource.LOCAL`, since Glide writes what is `REMOTE` to its data cache. Never disable the cache per request,
  where one forgotten call is a leak. (I3, encri D112)
- **The library forgets everything when the vault locks or the session goes**, and Glide's memory is cleared. (I3)
- **What this app deleted stays out** (`VaultLibrary.deleted`), of every screen at once and of a walk already on its
  way, which may have listed its folder first. The set clears only with the session: encri never gives an id to
  another document. (I11)
- **With previews off, no preview is asked of encri.** `VaultModelLoader` builds no load for one, so the request
  fails before encri hears of it; a preview request skips Glide's memory while previews are off; and the gallery,
  drawn again when it comes back with the setting changed, empties that memory. (I13)
- **Glide logs at `ERROR`.** At `WARN` it logs a failed load with its model, a document's URI. (I8, encri D8, D85)

## Traps already paid for
- Glide logged every failed load with its `encri-vault://` URI, and ZoomImage made one for every photo opened, so
  each photo put its id in logcat. A unit test now fails a load and reads the log. (I8)
- The first try at *Show previews* off showed every preview still, from Glide's memory, to which the old screens had
  handed theirs (seen on the A20). (I13)
- A walk took a grant's missing root for a folder deleted under it, and showed an empty gallery. (I9)
- On the G6 a walk of small folders was a string of short requests whose waits kept the CPU's clock low. Four at
  once fixed it; eight measured no better, each listing slower for the company. (I9, I14)
- Rejected: a `ContentProvider` here so Glimpse's loading worked unchanged, an exported surface or a confusing
  unexported one, for about 150 lines; and `Media` carrying a document id rather than a `Uri`, which touches every
  screen. (I4)

## Measured, not assumed
- On the A20 (API 30), one folder at a time: 5 photos and videos in 230 ms; 2,087 in 45 albums 1,117 ms, then 648 and
  672; 5,087 with 3,000 in one folder 1,521 ms, then 1,056 and 1,164. About 11 ms a folder and 0.12 ms an entry, and
  about 320 bytes a photo held. 20,000 photos in 200 folders in four to five seconds is an estimate. (I8)
- Walks of about 5,080 again, one folder at a time: A20 1,056–1,164 ms, S9 509–515, G6 513–602, S24 125–127. A
  180x180 preview, median: 62–68, 46–50, 41–55 and 11–35 ms; four at a time, 54–56, 62–64, 65–71 and 206–212 a
  second. (I8, I9)
- Twenty flings up Reels, janky frames: A20 7–10%, S9 0.6–2%, G6 59–88%, S24 0.1% by Android's own count since API
  31. (I8, I9)
- Four at once, on the G6 over 2,000 photos in 50 folders: 440 ms, then 274–299, against 1,354, then 903–1,214, one at
  a time; eight at once 535, then 226–343. (I14)
- Four at once on the A20, 2026-11-12, the same way: 414 ms, then 259–294, against 978, then 624–685, one at a time;
  eight at once 456, then 319, a folder listed in 27–29 ms against 11 at four. A 180x180 preview of those 160x120
  photos, median 21–25 ms; four at a time, about 175 a second. (I14, amended)
- After browsing, playing and the info sheet on the A20, the app's data directory held five empty directories and no
  file (I3); with an 8000x6000 photo open, one file, ProfileInstaller's note (I8).
- *Show previews* on the A20: a photo cell's brightness spread 44.9 with previews on, 0.0 off, 44.9 again. (I13)

## Decisions
| | Why it matters here |
|---|---|
| I4 | The seam, the walk, the URIs, previews from encri; two veto flags |
| I8 | 5,000 on the A20: the walk stays, published whole; Glide's log level; one veto flag |
| I9 | The other phones; the root gone is the session's end |
| I14 | Four folders at once (amends I4) |
| I3 | No disk cache, `DataSource.LOCAL`, forgetting at a lock; three veto flags |
| I11 | What was deleted stays out of a walk |
| I13 | *Show previews*, and Glide's memory; one veto flag |
| I15 | Zoom's tiles are held in Glide's memory |

## Open here
- Reels sorts by last change, not by when a photo was taken, so a photo copied in today reads as today's; flagged
  (I4), and `ROADMAP.md`'s *A date a photo was taken*.
- A first walk of a large library shows nothing until it ends: a second and a half at 5,000 on the A20; flagged.
  (I8)
- Unmeasured: 20,000 photos; four folders at once on ~~any phone but the G6~~ the S9 and the S24, the A20 measured
  2026-11-12; video (I8, I9, I14). I4's own *Not measured* list predates I8, I9, I15 and I18, which measured most of
  it without a mark there; a motion photo and a large video over the proxy descriptor remain unmeasured.

## Read next
- Related briefs: `.claude/rules/vault-link.md`, `.claude/rules/viewer.md`, `.claude/rules/privacy-and-settings.md`
- Full records: `docs/decisions/INDEX.md`
