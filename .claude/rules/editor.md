---
paths:
  - "app/src/**/edit/**"
  - "app/src/**/vault/Copies*.kt"
  - "app/src/**/vault/Rights*.kt"
  - "app/src/main/res/layout/activity_edit.xml"
  - "app/src/main/res/menu/activity_edit_toolbar.xml"
---

# Editor — component brief

*Crop and rotate*: `EditActivity` and what it is made of, in the `edit` package. `CropView` draws the picture and the
dotted frame and takes the fingers, with `Viewport` (zoom and pan), `Frames` (fitting and dragging the frame),
`FramePainter` and `Edit` (what is asked). `Geometry`'s `Placement` is a quarter turn or a mirror, EXIF's and the
user's alike. `PhotoEdits` makes a photo's copy and `VideoEdits` a video's, the latter into `MemoryOutput` through
`MemoryMuxer`; `VideoStage` plays a video under the frame, `Making` picks between them, and `SaveFlow` asks and saves.
In the `vault` package, `Copies` keeps a copy beside the original, and `Rights` asks for a right with every right held.
This brief is level 1; full records are in `docs/decisions/`.

## Rules that hold here
- **A copy, never the original.** An edit is a new document in the original's folder, `IMG_1 (edited).jpg`, with
  encri picking a free name where that is taken (`VaultSession.create`, `FLAG_FREE_NAME`). A copy of a copy is not
  marked twice. (I22)
- **`CREATE` and `WRITE` are asked for when a copy is first saved, before it is made**, with every right the grant
  already holds (`Rights.toAsk`), because a consent replaces the grant with exactly what it asks. Deletes ask the same
  way. After a consent, wait up to `Rights.ALLOWING_MILLIS` for a session that holds both. (I22, I11, I12)
- **Make the copy before encri is asked to keep it**, so a copy that cannot be made leaves nothing. A document made and
  not filled, or stopped while filling, is deleted again where the grant lets it delete. (I22)
- **No picture reaches a file.** A photo's copy is encoded into encri's pipe as it is written; a video's is muxed into
  `MemoryOutput`, direct buffers, and then written in order. Never `InAppMp4Muxer` or `DefaultMuxer`, which open the
  path Transformer names; `MemoryMuxer.Factory` ignores it. (I22, I3)
- **Only the part kept of a photo is decoded**, a band of `BAND_ROWS` at a time, each drawn turned into the one bitmap
  the copy is; sampled down by powers of two past `MOST_PIXELS`, and again on an `OutOfMemoryError`. Each reader gets
  a descriptor of its own (`Making.open`), because a descriptor's position is shared. (I22, I4)
- **The crop is in the turned picture's pixels**; `Edit.cropOfSource` is it on the picture as shown, which a video's
  `Crop` effect is given, made before the turn. A turn alone is a `ScaleAndRotateTransformation` alone, so Transformer
  writes it as the orientation and decodes nothing. (I22)
- **EXIF orientation is applied as shown**: `Placement.ofExif` then the user's turns, inverted to find the photo's own
  pixels. The copy carries no EXIF and a video's copy no metadata but its orientation (`MemoryMuxer`). (I22)
- **Nothing is reported to the platform**: `setUsePlatformDiagnostics(false)` on every `ExoPlayer` and `Transformer`
  this app builds, the viewer's too, since Google may collect `MediaMetricsManager`'s reports. (I22, I3)
- **The editor closes at a lock or the session's end**, as the viewer does, and cancelling a save stops Transformer
  and drops its memory. Every dialog goes through `SecureWindows.secure`; a toast names nothing. (I22, I3)
- **A finger changes the edit through `CropView`, and so does a button** (`CropView.change`); both tell
  `onEditChanged`, which is what turns *Save a copy* on. (I22)
- **Guava's annotations are excluded from the Media3 declarations that bring them** (`withoutGuavaAnnotations`), as
  Media3 excludes them, so the APK ships nothing more than the notices name. (I22, I19)

## Traps already paid for
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
- **Nothing on a phone.** The editor, encri's consent for `CREATE` and `WRITE`, Transformer on real encoders, memory
  with large photos and long videos, HDR video and the platform's reports are unmeasured. (I22)

## Decisions
| | Why it matters here |
|---|---|
| I22 | Crop and rotate into a copy; the rights; memory, not files; no metadata; six veto flags |
| I11 | `DELETE` asked when first used, which a consent for these rights would otherwise take away |
| I12 | A consent replaces the grant and ends the session; the wait for the next |
| I4 | A full photo and a video are decoded here; reading off the main thread |
| I3 | No picture on disk; the editor unexported and closing at a lock; secure dialogs |
| I19 | The notices name exactly what ships |

## Open here
- Everything on a phone (above). The first is the A20 or the G6: a photo cropped, turned and saved after encri's
  consent screen; a turn alone of a clip, and a crop of one, played back in the viewer; and the app's data directory
  after both, which should hold no picture.
- A video is held whole in memory, up to an eighth of the phone's memory or 1 GiB, and a larger one is not edited; a
  turn alone of one is refused before it starts, a crop only when its memory runs out. Flagged. (I22)
- A copy keeps no EXIF, so no location and no time taken, and reads as today's in Reels (I4, I22). Flagged.
- An empty document stays in the vault when a copy could not be filled and the grant cannot delete. (I22)

## Read next
- Related briefs: `.claude/rules/viewer.md`, `.claude/rules/share-and-delete.md`, `.claude/rules/vault-link.md`
- Full records: `docs/decisions/INDEX.md`
