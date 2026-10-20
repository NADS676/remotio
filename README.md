# Remotio

An Android remote for a TCL Roku TV and an Amazon Fire TV Stick, over Wi-Fi.

- **Roku TV**: Roku's documented [External Control Protocol](https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md) (HTTP, port 8060). No pairing.
- **Fire TV**: the same local API Amazon's Fire TV phone app uses (DIAL wake-up on 8009, HTTPS on 8080). You pair once with a PIN shown on the TV. Amazon doesn't document this API, so a Fire OS update could break it.

The app finds both kinds of device on the network (SSDP for Roku, mDNS for Fire TV). You can also add one by IP address.

## Using it

- **Two layouts**, switched with the icon next to the power button (the app remembers your pick):
  - **Touchpad**: a dark swipe pad instead of a D-pad. Tap for OK, swipe to move one step per ~48dp, and rest your finger at the end of a swipe to keep scrolling.
  - **One-thumb**: everything you press often sits in the bottom half. With two or three TVs saved, tabs at the top switch between them in one tap.
- **Type** opens a text box for whatever text field is open on the TV: search, sign-in, Wi-Fi passwords. On the Roku, letters appear on the TV as you type. On the Fire TV, the text goes over when you tap Send.
- **Speak** uses Android's speech recognizer. The result fills the text box, ready to send.
- The phone's own volume buttons control the TV while the app is open.

## TV-side setup

**Roku TV**
- *Settings › System › Advanced system settings › Control by mobile apps › Network access* must be **Default** (or Permissive). Otherwise the TV answers every command with 403.
- For the power button to turn the TV **on**, enable *Settings › System › Power › Fast TV start*. Without it, the TV drops off the network when it's off.

**Fire TV**
- Nothing to enable. The first time you pick it, the TV shows a PIN to type into the app.
- The power button puts the Fire TV to sleep. A sleeping Fire TV stops listening, so it can't be woken from the app.

## Build and install

Needs JDK 17+ (Android Studio's bundled JBR works) and the Android SDK with platform 37.

**Release build** (optimized, signed with the project's own key; use this one on your phone):

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

You can also copy `app-release.apk` to the phone and open it there; Android asks you to allow installs from that app once.

- The signing key is `keystore/remotio-release.jks`, and its password is in `keystore.properties`. Both are gitignored. **Back them up.** Every future update must be signed with this key. Without it, Android only installs a new build after uninstalling the old one, which wipes your saved TVs and Fire TV pairing.
- For each update, bump `versionCode` in `app/build.gradle.kts` and install with `adb install -r`. Your data is kept.
- A debug build and a release build are signed with different keys, so switching between them means uninstalling first (`adb uninstall app.remotio`).

**Debug build** (for development):

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Android 17+, the app asks for **Nearby devices** (local network access) at first launch. It needs this to reach the TV.

## Layout

- `tv/`: protocol code. `RokuRemote` and `FireTvRemote` implement `TvRemote`. `TvDiscovery` finds devices, and `SavedTvStore` remembers them.
- `ui/`: Compose screens. `TouchpadLayout` and `OneThumbLayout` are the two remotes, and `TextEntrySheet` handles typing and voice. `RemoteViewModel` sends everything (keys and text) through one ordered queue.
