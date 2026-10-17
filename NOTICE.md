# wheelaa attribution and changes

wheelaa is an independent experimental derivative, not an official DiAuto, WheelPlay, Google or BYD release.

- DiAuto: https://github.com/shihabal3amri/DiAuto at a2119f485fb0c833b9b06ba70c10266b00c815be. Android Auto transport, native decoding, connection UI and existing tests are retained, with original notices under LICENSE (AGPL-3.0), COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt and docs/THIRD_PARTY.md.
- WheelPlay: https://github.com/fython/wheelplay at 4ed3536822998babd086e4fcb1a384e791b0e2d4. The browser architecture and single-controller / stuck-finger recovery design informed the new BrowserSession implementation. The WheelPlay TouchLease logic was adapted for one Android Auto pointer; its GPL-3.0 license is retained in licenses/WheelPlay-GPL-3.0.txt. No CarPlay authentication assets or native WebRTC libraries are included from WheelPlay. GPLv3 and AGPLv3 components are combined under their respective terms, including the AGPL network-source requirements.

- libsu core 6.0.0: vendored from topjohnwu/libsu commit 8c3e80ffe466b89ff875471e542d77e6a1b480c5 (Apache-2.0), with unchanged Java sources. See third_party/libsu-core/UPSTREAM.md.

New wheelaa code is offered under AGPL-3.0-or-later. Original third-party licenses remain applicable. The browser offers the corresponding source of the built version at /source.zip, generated from an explicit source/build-script allowlist during APK assembly.

Changes: independent app ID and launcher, opt-in LAN HTTP service, six-digit pairing, one browser token, JPEG capture from the Android Auto TextureView (up to 960 px wide, 10 fps, quality 70), normalized single-pointer AAP input, expiry/cancellation, browser UI and tests. Audio and microphone use DiAuto native routes; browser audio/WebRTC/background capture have not been implemented.

The original DiAuto README and product-specific setup notes are retained under docs/. BYD-specific claims in upstream documentation describe the upstream app, not validation of wheelaa.
