# Build the installable Android APK (no Android Studio needed)

**This source project is NOT an APK.** I could not compile it in the conversation environment, which has no Android SDK or network access for Android build dependencies. The GitHub Actions workflow below is provided but has **not** been executed or verified here.

## On GitHub (cloud build)

1. Create a new private GitHub repository (do not upload any password, VIN, or personal data).
2. Unzip `CromaSteeringDiag_android_cloud_build.zip` on a computer.
3. In GitHub, choose **Add file > Upload files** and upload the project files/folders, **including the hidden `.github/workflows/build-apk.yml` folder**. Commit to the `main` branch. Note that web upload does not always handle hidden folders easily; you can create `.github/workflows/build-apk.yml` via `Add file > Create new file` with that exact path and paste its content.
4. Go to **Actions** > **Build Android APK** > **Run workflow**. If Actions is disabled, enable it for this repository.
5. When the build shows a green checkmark, open the run > **Artifacts** > download **CromaSteeringDiag-debug-apk**.
6. Unzip the downloaded artifact to get `app-debug.apk`. Transfer it to your phone and open it. Android might ask you to allow installation from the chosen source.

It is a **debug APK signed by the GitHub runner**, not a Google Play release. Each fresh cloud run may use a different debug signature, so uninstall the previous one before installing a newer artifact if Android reports an incompatible signature.

## Alternative

Open this project in Android Studio with SDK Platform 35 and Java 17. Run **Build > Build APK(s)**. No Gradle wrapper files are needed when you run the GitHub cloud workflow above, because it installs Gradle directly.

## What it does and does not do

- It connects to already-paired Bluetooth Classic ELM327 devices, tests their responses, reads adapter supply voltage, and exports raw diagnostic logs.
- It includes a separate **experimental, unverified** steering K-line probe. The TRW steering ECU address and protocol are not confirmed. Do not use its result to decide whether the pump is good or bad.
- **It is NOT currently a functional steering-pump fault-code reader.**
- You typically need a good ELM327 Bluetooth Classic interface, plus a green MultiECUScan A1 adapter that routes vehicle OBD pin 12 to the reader K-line. Not every ELM327 clone supports this.
- Use only with the car parked. Do not use the app for programming or steering actuator tests.
