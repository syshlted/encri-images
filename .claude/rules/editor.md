---
paths:
  - "app/src/**/edit/**"
  - "app/src/**/vault/Copies*.kt"
  - "app/src/**/vault/Rights*.kt"
  - "app/src/**/vault/SaveFlow*.kt"
  - "app/src/**/vault/VaultEditStore*.kt"
  - "app/src/main/res/layout/activity_edit.xml"
  - "app/src/main/res/menu/activity_edit_toolbar.xml"
---

# Editor — component brief

*Crop and rotate*: `EditActivity` and what it is made of, in the `edit` package. `CropView` draws the picture and the
dotted frame and takes the fingers, with `Viewport` (zoom and pan), `Frames` (fitting and dragging the frame),
`FramePainter` and `Edit` (what is asked). `Geometry`'s `Placement` is a quarter turn or a mirror, EXIF's and the
user's alike. `PhotoEdits` makes a photo's copy and `VideoEdits` a video's, the latter into `MemoryOutput` through
`MemoryMuxer`; `VideoStage` plays a video under the frame, `Making` picks between them, and `Saving` makes and keeps a
copy. All of it reads and keeps through `EditStore` (I23). In the `vault` package, `VaultEditStore` is the vault's
store, `Copies` keeps a copy beside the original, `Rights` asks for a right with every right held, and `SaveFlow` asks
encri's leave before `Saving` starts.
This brief is level 1; full records are in `docs/decisions/`.

## Rules that hold here
- **Only `EditActivity` knows the vault.** Everything else in `edit` reads a photo or video and keeps a copy through
  `EditStore`, so it can go to Glimpse and come back with the vault's store in place of Glimpse's. `EditorSeamTest`
  fails when another file names the vault's package or encri's client; `LoopWhatLasts`, which knows nothing of the
  vault, is let through by name. Leave to keep copies is the vault's (`SaveFlow`), not the store's. (I23)
- **A copy, never the original.** An edit is a new document in the original's folder, `IMG_1 (edited).jpg`, with
  encri picking a free name where that is taken (`VaultSession.create`, `FLAG_FREE_NAME`). A copy of a copy is not
  marked twice. (I22)
- **`CREATE` and `WRITE` are asked for when a copy is first saved, before it is made**, with every right the grant
  already holds (`Rights.toAsk`), because a consent replaces the grant with exactly what it asks. Deletes ask the same
  way. After a consent, wait up to `Rights.ALLOWING_MILLIS` for a session that holds both. (I22, I11, I12)
- **Make the copy before encri is asked to keep it**, so a copy that cannot be made leaves nothing. A document made and
  not filled, or stopped while filling, is deleted again where the grant lets it delete. (I22)
- **No picture reaches a file.** A photo's copy is encoded into encri's pipe as it is written; a video's is muxed into
  `MemoryOutput`, ~~direct buffers~~ `SharedMemory` outside the Java heap, and then written in order. Never
  `InAppMp4Muxer` or `DefaultMuxer`, which open the path Transformer names; `MemoryMuxer.Factory` ignores it. (I22,
  I3)
- **Only the part kept of a photo is decoded**, a band of `BAND_ROWS` at a time, each drawn turned into the one bitmap
  the copy is; sampled down by powers of two past `MOST_PIXELS`, and again on an `OutOfMemoryError`. Each reader gets
  a descriptor of its own (`Making.open`), because a descriptor's position is shared. (I22, I4)
- **The crop is in the turned picture's pixels**; `Edit.cropOfSource` is it on the picture as shown, which a video's
  `Crop` effect is given, made before the turn. A turn alone is a `ScaleAndRotateTransformation` alone, so Transformer
  writes it as the orientation and decodes nothing. (I22)
- **EXIF orientation is applied as shown**: `Placement.ofExif` then the user's turns, inverted to find the photo's own
  pixels. What a copy carries of the original's metadata is its store's `Carried`: the vault's carries nothing, no
  EXIF and nothing of a video's but its orientation. A store whose copy is a file may carry `ALL_BUT_ORIENTATION`,
  whose photo tags `Made.exif` takes to the store to write, and a video's are `MemoryMuxer`'s. (I22, I23)
- **The vault's store never takes EXIF.** `ExifInterface` writes only through a temporary copy of the picture, onto a
  file that seeks; `VaultEditStore.keep` refuses a copy offered tags rather than drop them unsaid. (I23, I3)
- **Nothing is reported to the platform**: `setUsePlatformDiagnostics(false)` on every `ExoPlayer` and `Transformer`
  this app builds, the viewer's too, since Google may collect `MediaMetricsManager`'s reports. (I22, I3)
- **The editor closes at a lock or the session's end**, as the viewer does, and cancelling a save stops Transformer
  and drops its memory. Every dialog goes through `SecureWindows.secure`; a toast names nothing. (I22, I3)
- **A finger changes the edit through `CropView`, and so does a button** (`CropView.change`); both tell
  `onEditChanged`, which is what turns *Save a copy* on. (I22)
- **Guava's annotations are excluded from the Media3 declarations that bring them** (`withoutGuavaAnnotations`), as
  Media3 excludes them, so the APK ships nothing more than the notices name. (I22, I19)

## Traps already paid for
- **A direct buffer is in the Java heap on Android**, an array `newNonMovableArray` made, not outside it as on a
  desktop JVM. `MemoryOutput` held a video in them, and one past 512 MiB, the G6's and the A20's heap with
  `largeHeap`, ended the app: Transformer catches no `OutOfMemoryError`. The unit tests run on the JVM and could not
  see it; `EditMemoryOnPhone` does. Its pieces are `SharedMemory` now. (I22, amended)
- Two fingers began as a drag: the first finger landed inside the frame and took hold of it, and two fingers moving
  together, which never become a pinch, moved the frame. A second finger now ends whatever the first held, and two
  fingers not pinching move the picture. A test that spread the fingers did not catch it, since a pinch takes them at
  once; `CropViewTest` moves them together. (I22)
- `CropView.change` told nobody, so a change a button asked for left *Save a copy* off. Found by `EditActivityTest`.
  (I22)
- `kotlinx-coroutines-guava` brought back `jsr305`, `checker-qual` and `j2objc-annotations`, which Media3 had left out,
  and moved `error_prone_annotations`. (I22)
- Rejected: Media3's fragmented MP4 muxer, which writes in order and needs no memory, because its files have no index
  and ExoPlayer cannot seek in one; a file in this app's cache; and replacing the original. (I22)

## Measured, not assumed
- Under Robolectric, native graphics, a photo's quarters through a crop, a turn, both, EXIF orientation 6 on a JPEG, a
  photo of 2,500 rows decoded in three bands with nothing lost where they meet, a BMP decoded whole, and each format's
  copy. `Mp4Muxer` writes a location it is offered and `MemoryMuxer` does not; each of four turns is the track's
  matrix. (I22)
- ~~**Nothing on a phone.**~~ **Run by the user on the S24 (API 37), who reports it worked** (2026-11-18); what was
  edited was not said. Unmeasured still: encri's consent for `CREATE` and `WRITE`, Transformer on other phones'
  encoders, memory with large photos and long videos, HDR video and the platform's reports. (I22, amended)

- `EditMemoryOnPhone` (2026-11-18): 576 MiB held and read back whole, 64 MiB past a heap of 512 MiB, on the G6 in
  3.5 s and the A20 in 3.0 s. With direct buffers the G6 failed at its heap's 512 MiB. (I22, amended)

## Decisions
| | Why it matters here |
|---|---|
| I22 | Crop and rotate into a copy; the rights; memory, not files; no metadata; six veto flags |
| I23 | `EditStore`; only `EditActivity` knows the vault; what a copy carries; Glimpse later |
| I11 | `DELETE` asked when first used, which a consent for these rights would otherwise take away |
| I12 | A consent replaces the grant and ends the session; the wait for the next |
| I4 | A full photo and a video are decoded here; reading off the main thread |
| I3 | No picture on disk; the editor unexported and closing at a lock; secure dialogs |
| I19 | The notices name exactly what ships |

## Open here
- Glimpse: the photo editor in Glimpse's shape with a MediaStore store, then a video's, looking and working as the
  photo's does, then taken back. Deferred at the user's word. (I23, `ROADMAP.md`)
- Everything on a phone but the S24's run (above). The first is the A20 or the G6: a photo cropped, turned and saved
  after encri's consent screen; a turn alone of a clip, and a crop of one, played back in the viewer; and the app's
  data directory after both, which should hold no picture.
- A video is held whole in memory, up to an eighth of the phone's memory or 1 GiB, and a larger one is not edited; a
  turn alone of one is refused before it starts, a crop only when its memory runs out. Flagged. (I22)
- A copy keeps no EXIF, so no location and no time taken, and reads as today's in Reels (I4, I22). Flagged. In the
  vault it cannot, as EXIF (I23).
- An empty document stays in the vault when a copy could not be filled and the grant cannot delete. (I22)

## Read next
- Related briefs: `.claude/rules/viewer.md`, `.claude/rules/share-and-delete.md`, `.claude/rules/vault-link.md`
- Full records: `docs/decisions/INDEX.md`
