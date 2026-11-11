# I13 — Settings of its own: screenshots allowed or not, and previews shown or not
*2026-11-04 · accepted (`ROADMAP.md`'s *Settings of its own*); **flagged for veto on one point** (below). **Amends I3**
on screenshots.*

**Two switches at the top of the settings screen, under *Privacy***, kept in the app's own preferences, which nothing
backs up or transfers (I3), and read by `PrivacySettings`.

**Allow screenshots, off by default**, as encri-files' (F3), with its reasons and its words: off, every window this
app draws carries `FLAG_SECURE`; on, none does, and the switch says what a screenshot costs and that encri's own
screens are not changed by it. `SecureWindows` applies it when a window is made and again whenever a screen comes to
the front, so a change reaches the gallery behind the settings without a restart, and the settings screen follows at
once. Every dialog follows it through `SecureWindows.secure`, as before.

**Show previews, on by default** (encri D112 gives each app its own, as encri-files' F4 does). Off, no grid cell, album
cover or video's still asks encri for a preview: `VaultModelLoader` builds no load for one, so the request fails before
encri hears of it, and the cell shows its empty background. A photo opened is still read and shown, which is not a
preview. **Glide's memory is the catch**: the gallery's old screens hand their previews to Glide's memory cache as they
go, and a new request for the same cell takes it from there without asking the loader, which is how the first try
showed every preview still (seen on the A20). So while previews are off a preview request skips Glide's memory, and
the gallery, drawn again when it comes back with the setting changed, empties that memory once the old screens have
let go of it.

*Rejected:* a setting per screen, for F3's reason. *Rejected:* applying the previews setting only at the next start,
which leaves the switch looking broken.

**Verified.** Under Robolectric: kept out by default, allowed once chosen, applied on a screen's return, which fails its
test without the re-apply, and at once on the screen it was changed on; the settings screen's keys and defaults are
`PrivacySettings`'; with previews off no preview is asked for, a full image still is, and one Glide already holds is
not shown, which fails without the memory skip. **On the A20 (API 30):** `screencap` refused, then allowed on the
settings screen at once and on the gallery behind it, then refused again on both; a photo cell's brightness spread
44.9 with previews on, 0.0 with them off, and 44.9 again.

**Flagged for veto:**

1. **Screenshots are blocked by default** (encri-files F3's flag, again).
