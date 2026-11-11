# I18 — The release build after I13 to I16, and API 27 at last
*2026-11-04 · accepted (`ROADMAP.md`'s *Measure a real library*, the API 27 part). **Amends I4 and I6** on what has
run where.*

**The minified release build, signed with the debug key as in I4**, 3.3 MB against the debug build's 12, not
debuggable. R8 shrinks and renames, and Glide and ZoomImage find code at run time, so everything added since I4 was
walked through on it. **On the G6 (API 28)**, over seven photos `SeedLibrary` made: the gallery and the settings screen,
both switches drawn with their words; screenshots allowed, then kept out again; the 8000x6000 photo's zoom measured
11.62 of detail against 1.75 unzoomed, as the debug build did (I15); two chosen, *what is in them*, the platform's
chooser, and after encri allowed it, *Delete these 2 photos and videos?* and a fresh walk at four of six (I16). **On the
A20 (API 30)**: the 8-second clip still playing at 12 seconds, so looped, and the clip of no length stopped, two
decoders started rather than 132 (I4, amended). No crash, and no vault URI in the log, on either.

**API 27, the floor (I1), now starts**: under an emulator made for it from the API 27 image, with no encri, the gate test
showed *encri is not installed*, and the release build started in 536 ms with its window `SECURE`. Lint cannot see an
attribute's value against an API level (I6), so this is the check below API 28. Not measured there: anything with a
vault, which needs encri on the emulator.
