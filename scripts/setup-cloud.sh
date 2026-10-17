#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/cloud-env.sh
mkdir -p "$WHEELAA_TOOLS_DIR" "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"
wheelaa_temp="$(mktemp -d /tmp/wheelaa-setup.XXXXXX)"
trap 'rm -rf "$wheelaa_temp"' EXIT
if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
  curl -fL --retry 2 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.17%2B10/OpenJDK17U-jdk_x64_linux_hotspot_17.0.17_10.tar.gz' -o "$wheelaa_temp/jdk.tar.gz"
  printf '%s  %s\n' 992f96e7995075ac7636bb1a8de52b0c61d71ed3137fafc979ab96b4ab78dd75 "$wheelaa_temp/jdk.tar.gz" | sha256sum -c -
  tar -xzf "$wheelaa_temp/jdk.tar.gz" -C "$WHEELAA_TOOLS_DIR"
fi
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  curl -fL --retry 2 https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$wheelaa_temp/sdk.zip"
  printf '%s  %s\n' 7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee "$wheelaa_temp/sdk.zip" | sha256sum -c -
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q "$wheelaa_temp/sdk.zip" -d "$wheelaa_temp/sdk"
  mv "$wheelaa_temp/sdk/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
# Reuse OS-provided cloud proxy trust. Never disable TLS/certificate checks.
python3 - <<'PY'
import os, pathlib, subprocess, urllib.parse
proxy = urllib.parse.urlparse(os.environ.get('HTTPS_PROXY', os.environ.get('https_proxy', '')))
props = {'org.gradle.workers.max': '4'}
trust = pathlib.Path('/etc/ssl/certs/java/cacerts')
if trust.exists(): props['systemProp.javax.net.ssl.trustStore'] = str(trust)
args = [os.environ['ANDROID_HOME'] + '/cmdline-tools/latest/bin/sdkmanager']
if proxy.hostname:
    if proxy.username or proxy.password:
        raise SystemExit('Use platform-injected proxy authentication; do not store proxy credentials in Gradle settings.')
    for protocol in ('http', 'https'):
        props[f'systemProp.{protocol}.proxyHost'] = proxy.hostname
        props[f'systemProp.{protocol}.proxyPort'] = str(proxy.port or 80)
    props['systemProp.http.nonProxyHosts'] = 'localhost|127.*|[::1]'
    args += ['--proxy=http', '--proxy_host=' + proxy.hostname, '--proxy_port=' + str(proxy.port or 80)]
p = pathlib.Path(os.environ['GRADLE_USER_HOME']) / 'gradle.properties'
lines = p.read_text().splitlines() if p.exists() else []
lines = [line for line in lines if line.partition('=')[0] not in props]
p.write_text('\n'.join(lines + [f'{k}={v}' for k, v in props.items()]) + '\n')
sdk_env = dict(os.environ)
if trust.exists(): sdk_env['JAVA_OPTS'] = sdk_env.get('JAVA_OPTS', '') + ' -Djavax.net.ssl.trustStore=' + str(trust)
subprocess.run(args + ['platforms;android-36', 'platforms;android-34', 'build-tools;35.0.0', 'build-tools;36.0.0', 'platform-tools', 'ndk;29.0.14206865', 'cmake;3.22.1'], input='y\n' * 100, text=True, check=True, env=sdk_env)
PY
# Do not rewrite source, lockfiles, local.properties or user signing configuration.
./gradlew :app:testGithubDebugUnitTest :app:assembleGithubDebug --max-workers=4 --console=plain
npm test
python3 scripts/check_localization.py
