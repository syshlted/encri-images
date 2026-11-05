#!/usr/bin/env bash
# SPDX-FileCopyrightText: SystemHalted
# SPDX-License-Identifier: Apache-2.0
#
# Makes Encri Images' upload key, on the key machine (I20): the one of a release's three keys that is this app's
# own. The app signing and code transparency keys are encri's and stay so (encri D115, D140); this makes neither.
#
#   bash upload-key.sh <directory>    make <directory>/encri-images-upload.p12, alias encri-images-upload
#   bash upload-key.sh --rehearse     the same in a temporary directory with a throwaway password, keeping nothing
#
# The key is what sign.sh expects and rehearses with: RSA 4096, in a PKCS#12 keystore, valid for 10,000 days (27
# years), under the alias sign.sh looks for by default. keytool asks for the password at the terminal, twice to make
# the key and once more to read its certificate back, so this script never holds it. The keystore is made under
# umask 077, and one that exists is never written over: a lost upload key can be reset on Play, an overwritten one
# cannot be got back.
#
# What it leaves beside the keystore, and prints, is public:
#   encri-images-upload.pem   the certificate, for Play's upload key registration
#   upload=<sha256>           the line for config/release-certificates, committed with why the key was made (I20)
#
# Run it on the key machine, offline, into the directory that holds encri's two keystores, since sign.sh is given
# that one directory. Copy the .pem and the printed line back; the .p12 never leaves.
#
# Exit status: 0 the key was made; 2 a refusal.
set -euo pipefail
umask 077

refuse() { echo "REFUSED: $*" >&2; exit 2; }

ALIAS=encri-images-upload
DNAME="CN=Encri Images upload key, O=SystemHalted"
JAVA_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}
command -v "${JAVA_BIN}keytool" > /dev/null 2>&1 || refuse "no keytool: the key machine needs a JDK 17 or later"

PASS=()
case "${1:-}" in
    --rehearse)
        MODE=rehearse
        DIR=$(mktemp -d "${TMPDIR:-/tmp}/encri-images-upload-key.XXXXXX") || refuse "no temporary directory"
        trap 'rm -rf "$DIR"' EXIT
        head -c 24 /dev/urandom | base64 > "$DIR/password"
        PASS=(-storepass:file "$DIR/password")
        ;;
    "" | -*)
        refuse "usage: upload-key.sh <directory> | --rehearse"
        ;;
    *)
        MODE=real
        DIR=$1
        [ -d "$DIR" ] || refuse "no directory $DIR"
        ;;
esac

KEYSTORE=$DIR/$ALIAS.p12
PEM=$DIR/$ALIAS.pem
[ ! -e "$KEYSTORE" ] || refuse "$KEYSTORE exists, and this never writes over a key"
[ ! -e "$PEM" ] || refuse "$PEM exists; move it aside, or the certificate printed would not be the key's"
if [ "$MODE" = real ]; then
    [ -t 0 ] && [ -t 1 ] || refuse "keytool asks for the password on a terminal; run this in one, unpiped"
fi

if [ "$MODE" = real ]; then
    echo "== making $KEYSTORE: keytool asks for its password twice, then once more to read the certificate back"
fi
"${JAVA_BIN}keytool" -genkeypair -noprompt -keystore "$KEYSTORE" -storetype PKCS12 -alias "$ALIAS" \
    -keyalg RSA -keysize 4096 -validity 10000 -dname "$DNAME" ${PASS[@]+"${PASS[@]}"} \
    || { rm -f "$KEYSTORE"; refuse "keytool made no key"; }
"${JAVA_BIN}keytool" -exportcert -rfc -keystore "$KEYSTORE" -alias "$ALIAS" -file "$PEM" ${PASS[@]+"${PASS[@]}"} \
    > /dev/null || refuse "the key was made, but its certificate could not be read back; $KEYSTORE is kept"
chmod 644 "$PEM"

# Read from the certificate alone, which needs no password, the way sign.sh reads a key it is given.
CERT=$("${JAVA_BIN}keytool" -printcert -file "$PEM")
fingerprint() { echo "$CERT" | sed -n "s/^[[:space:]]*$1: //p" | head -n 1; }
SHA256=$(fingerprint SHA256)
SHA1=$(fingerprint SHA1)
LINE=upload=$(echo "$SHA256" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
[[ $LINE =~ ^upload=[0-9a-f]{64}$ ]] || refuse "keytool printed no SHA-256 for $PEM"
echo "$CERT" | grep -q 'RSA[ ,].*4096\|4096-bit RSA' || refuse "the key made is not RSA 4096"

if [ "$MODE" = rehearse ]; then
    # The same directory twice: what a real run would do on a second try must be a refusal.
    again=$(bash "$0" "$DIR" < /dev/null 2>&1) && refuse "a second run over the same key made one"
    [[ $again == *"exists, and this never writes over a key"* ]] || refuse "a second run over the same key was not refused for it"
    echo "== rehearsal: a 4096-bit RSA key made, its certificate read back as $LINE; nothing is kept"
    exit 0
fi

cat << EOF
== made $KEYSTORE, alias $ALIAS: it stays on this machine
   certificate SHA-256: $SHA256
   certificate SHA-1:   $SHA1   (Play shows this one)
   valid:               $(echo "$CERT" | sed -n 's/^Valid from: //p' | head -n 1)

Carry back, both public:
   $PEM                      for Play's upload key registration
   $LINE
                            for config/release-certificates, in a commit that says why the key was made (I20)
EOF
