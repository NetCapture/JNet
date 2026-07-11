#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: $0 <version>" >&2
    echo "Example: $0 3.6.0" >&2
}

if [[ $# -ne 1 ]]; then
    usage
    exit 2
fi

version="${1#v}"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z._-]+)?$ ]]; then
    echo "Invalid version: $1" >&2
    exit 2
fi

replace() {
    local file="$1"
    local expression="$2"
    [[ -f "$file" ]] || return 0
    sed -E -i.bak "$expression" "$file"
    rm -f "$file.bak"
}

replace pom.xml "s#<revision>[^<]+</revision>#<revision>${version}</revision>#"
replace README.md "s#<version>[0-9A-Za-z._-]+</version>#<version>${version}</version>#g"
replace README.md "s#(com\\.netcapture:jnt:)[0-9A-Za-z._-]+#\\1${version}#g"
replace docs/data.json "s#\"version\"[[:space:]]*:[[:space:]]*\"[^\"]+\"#\"version\": \"${version}\"#"
replace docs/data.json "s#\"releaseName\"[[:space:]]*:[[:space:]]*\"[^\"]+\"#\"releaseName\": \"Release ${version}\"#"
replace docs/index.html "s#(<span data-version>)[^<]+#\\1${version}#g"
replace docs/index.html "s#(<span id=\"footerVersion\">)[^<]+#\\1${version}#"
replace src/main/java/com/jnet/core/Request.java "s#JNet/[0-9A-Za-z._-]+#JNet/${version}#g"
replace src/main/java/com/jnet/rtsp/RtspClient.java "s#JNet/[0-9A-Za-z._-]+#JNet/${version}#g"
replace src/main/java/com/jnet/rtsp/RtspRequest.java "s#JNet/[0-9A-Za-z._-]+#JNet/${version}#g"

cat <<EOF
Updated project version to ${version}.

Review the diff, run ./build.sh verify, then commit using the repository's Lore commit format.
No commit, tag, push, or release was performed.
EOF
