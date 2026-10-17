#!/usr/bin/env bash
# Source this file in Codex cloud; Android Studio uses its own SDK settings.
export WHEELAA_TOOLS_DIR="${WHEELAA_TOOLS_DIR:-/workspace/.wheelaa-tools}"
export JAVA_HOME="$WHEELAA_TOOLS_DIR/jdk-17.0.17+10"
export ANDROID_HOME="$WHEELAA_TOOLS_DIR/android-sdk"
export ANDROID_USER_HOME="$WHEELAA_TOOLS_DIR/android-user"
export GRADLE_USER_HOME="$WHEELAA_TOOLS_DIR/gradle"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
