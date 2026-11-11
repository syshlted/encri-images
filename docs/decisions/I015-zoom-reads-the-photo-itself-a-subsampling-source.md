# I15 — Zoom reads the photo itself: a subsampling source of this app's own
*2026-11-04 · accepted (`ROADMAP.md`'s *Subsampling for large images*). **Answers I8** on zoom.*

**`VaultSubsampling` is the viewer's first `GlideSubsamplingImageGenerator`.** For a vault document it gives ZoomImage a
source, `VaultImageSource`, that opens the document through the link every time ZoomImage asks, so ZoomImage cuts the
photo into tiles and decodes those on screen at the zoom asked, from the vault, in this process, as the full image
already was (I4). Any other model is left to ZoomImage, which has none here. **So ZoomImage no longer asks Glide for
the photo as a file** (I8's S4), because a generator that answers comes before its own.

**The tiles are held in Glide's memory** (ZoomImage's `GlideTileImageCache`), which the app clears at every lock (I3),
and nowhere else.

**A source's key is made up** (`encri-vault-source-` and a random UUID), because ZoomImage keys its tiles by it and may
log it, and nothing that names a document may reach a log (encri D8, D85). So a photo opened again is a new source,
and its tiles are decoded again.

*Rejected:* a `content://` provider of this app's own for ZoomImage to open, for I4's reason. *Rejected:* a key derived
from the document's id, which a log would carry as well as the id.

**Verified.** Under Robolectric: a model not the vault's is left to ZoomImage; a vault photo's source reads it through
encri, afresh each time; its key names no document; a refused open fails and names nothing. **On the G6 (API 28)**, an
8000x6000 photo `SeedLibrary` made, with screenshots allowed for the run (I13): the mean difference between
neighbouring pixels in a central crop, the seed's grain, was 1.88 unzoomed and 0.60 after a double tap with the build
before, a magnified screen decode; with this one, 1.88 and **11.84**, tiles drawn from the photo itself. Logcat held no
vault URI and no ZoomImage error, and the app's data directory held nothing of the vault. The app held 254 MB, 153 MB of
it graphics, zoomed.

**Not measured:** a photo with an EXIF rotation, which ZoomImage reads from the source; memory over a long session of
zooming.
