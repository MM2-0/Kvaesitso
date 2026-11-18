#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
apk=$(find app/app/build/outputs/apk/default/debug -name '*.apk' -print -quit)
test -n "$apk"
adb install -r "$apk"
status=0
./gradlew :app:ui:connectedDefaultDebugAndroidTest -Ptelos.abi=x86_64 \
  -Pandroid.testInstrumentationRunnerArguments.class=de.mm20.launcher2.ui.aether.AetherEmbeddedClientTest \
  --no-daemon --max-workers=1 --console=plain -Dorg.gradle.parallel=false \
  -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx7g -Dfile.encoding=UTF-8' || status=$?
mkdir -p /tmp/aether-screenshots
adb pull /sdcard/Pictures/TelosAetherValidation /tmp/aether-screenshots/ || true
adb logcat -d -t 1000 > /tmp/aether-client-logcat.txt
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
files = list(Path('app/ui/build/outputs/androidTest-results').rglob('TEST-*.xml'))
count = sum(int(ET.parse(p).getroot().get('tests', 0)) for p in files)
if not count:
    raise SystemExit('No Aether UI tests executed')
PY
exit "$status"
