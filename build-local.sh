#!/data/data/com.termux/files/usr/bin/bash
# 在 Termux 本机构建 App(无需 NDK/原生工具链,原生库用 native/out 里的预编译件)
# 用法: ./build-local.sh            -> :apk:assembleDebug
set -e
cd "$(dirname "$0")/app"
TASK="${1:-:apk:assembleDebug}"
~/gradle-9.7.1/bin/gradle "$TASK" \
  --console=plain --no-daemon --no-parallel --max-workers=1 \
  --init-script "$HOME/mirror.init.gradle" \
  -Pandroid.aapt2FromMavenOverride="$PREFIX/bin/aapt2" \
  -Dorg.gradle.jvmargs="-Xmx2304m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC" \
  -Dkotlin.compiler.execution.strategy=in-process
