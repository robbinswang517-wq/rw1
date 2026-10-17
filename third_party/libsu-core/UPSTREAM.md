# libsu core 6.0.0

Source: https://github.com/topjohnwu/libsu
Tag: 6.0.0
Commit: 8c3e80ffe466b89ff875471e542d77e6a1b480c5
License: Apache-2.0 (LICENSE).

Java sources, manifest and consumer ProGuard rules are unmodified. Only build.gradle.kts is replaced to use wheelaa shared Android Gradle Plugin and SDK configuration, without upstream publication/Javadoc tasks. Vendored because JitPack is inaccessible in the cloud; this avoids substituting unverified binary artifacts.
