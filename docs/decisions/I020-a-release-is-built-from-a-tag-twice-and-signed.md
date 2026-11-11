# I20 — A release is built from a tag, twice, and signed from a kit of its own, as Encri Files' is
*2026-11-05 · accepted at the user's direction ("Release signing should be done in the same manner as encri files", and
CI "set up in the same way"). **Amends I2**, which left the signing key open. Encri Files' F9, carried over whole.*

**The keys are Encri Files' arrangement**: the app is signed with encri's app signing key and its bundle carries code
transparency signed with encri's transparency key (encri D115, D140), both of which encri-api's `FINGERPRINTS.md`
publishes and `config/release-certificates` names; the upload key is this app's own, `encri-images-upload`, which is
owed. Until its certificate is named there, `kit.sh` refuses to make a kit. encri D142 had said D115's shared key does
not extend to this app by itself; the user's word extends it.

**Built twice from one commit, and the same both times.** `scripts/release/reproduce.sh` and `compare-builds.py` are
Encri Files', with this app's names: two clones at different paths, under different umask, timezone, locale and Gradle
home, each with encri-api cloned beside it at the commit `config/encri-api.pin` names, with the pinned Gradle and every
pinned dependency. It refuses a commit without the notices (I19), without the wrapper pinned to what gradle.org
publishes, or from before the encri-api pin. `.github/workflows/reproducible.yml` runs it on every `v*` tag, weekly and
by hand, and keeps the unsigned APK and bundle for 90 days; the maintainer's run must give the same SHA-256s.

**Signed from a kit, on the key machine.** `kit.sh`, `sign.sh` and `bundletool.init.gradle.kts` are Encri Files', with
this app's names: the kit packs the reproduced build with apksigner and bundletool, each pinned, and rehearses itself
with throwaway keys before it is kept; `sign.sh` asks for each password at a terminal, signs, holds the signed files to
the build, and keeps nothing signed by a key `config/release-certificates` does not name; `sign.sh --verify`, here,
checks what comes back. On Play, the bundle needs the listing's automatic protection off, because it carries code
transparency (encri D144).

**CI is Encri Files'**: `ci.yml` and `commit-policy.yml` already were (I1); the notices step (I19) and `reproducible.yml`
were what it lacked.

**The upload key is made by `scripts/release/upload-key.sh`**, on the key machine, into the directory that holds encri's
two keystores. Encri Files' repository holds no such script, its key having been made elsewhere, and encri's records are
not read here (I5), so this one is written to what `sign.sh` expects and rehearses with: RSA 4096 in PKCS#12, valid
10,000 days, under the alias `sign.sh` looks for. keytool asks for the password at the terminal, so the script never
holds it; an existing keystore is never written over; and it leaves the certificate as a `.pem` for Play and prints the
`upload=` line for `config/release-certificates`. `--rehearse` does it all with a throwaway key and keeps nothing.
*Measured:* the rehearsal passed and refused a second run over its key for that reason; a real run through a
pseudo-terminal, into a scratch directory, asked three times, left the keystore mode 600, and printed the SHA-256 that
`keytool -list` reads from the keystore, as `sign.sh` does; the scratch key was deleted.

*Flagged for veto:* **a kit of this app's own rather than one kit for all three apps**, Encri Files' flag, again: the
release scripts are now in three repositories, and a fix to one has to be made to the others.

**Measured, 2026-11-05.** `1d957f4` built the same twice here (APK `4c8c77fa…`, bundle `8213a9b4…`) against encri-api
`c27f139`. `kit.sh` refused it, naming no upload certificate. With a placeholder upload line, never committed, it made a
kit of 29 MB that rehearsed itself through every step with throwaway keys; the kit was then deleted and the line taken
out. The minified build was walked through on the G6 and the A20 (I18). **Not yet:** a real signing, which needs the
upload key, and `reproducible.yml` on GitHub, which needs the push.
