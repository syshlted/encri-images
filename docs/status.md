# encri-images — status history

> Moved out of `AGENTS.md` on 2026-11-11 (I21). The text below is unchanged; it was the
> *Status* section of that file.

## Status

**The first version reads and shows, and nothing else (2026-11-01).** It connects to encri, asks to read, unlocks
through encri's screen, shows Reels, Albums and Library from the vault, opens photos, and plays videos with seeking.
It hands a photo or video to another app only through encri's handoff, once the user has agreed (I10), and deletes
one the user chose, asking encri for that right the first time (I11); it changes nothing else in the vault. Checked
by hand on the A20 (API 30); see I4. Since 2026-11-03 it starts on API 28 and 29 as well (I6), and it was seen on
API 37 on 2026-11-04, and its gate on API 33 under Waydroid and on API 27 under an emulator; the A20, the S9, the G6
and the S24 are measured at 5,000 photos (I8, I9). **The minified release build** was walked through again on
2026-11-04, after I13 to I16 (below).
The fork on GitHub still holds only Glimpse's history, and nothing is released. `ROADMAP.md` says what is next.
