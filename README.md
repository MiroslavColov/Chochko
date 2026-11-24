# Croma Steering Diag — Android source project (v0.1 experimental)

For Fiat Croma (194), 2008, 1.9 Multijet 120 hp, electric-hydraulic TRW steering pump.

## What is working in source code

- Native Android application for Bluetooth Classic SPP ELM327 adapters.
- Lists already-paired Bluetooth devices; connects/disconnects.
- Tests the ELM327 command channel and reads adapter-reported battery supply voltage.
- Timestamped communication log (can copy to phone clipboard).
- **Experimental** and **unvalidated** ISO 14230-4 / KWP2000 read-only initiation and identification probe. The default assumed target address is 0x10 and has **NOT** been verified against the Croma pump ECU. No DTC decoding has been implemented.
- NO ECU programming, NO code clearing, NO actuator commands.

## Hardware you need

1. Bluetooth Classic (SPP) ELM327 adapter compatible with K-line ISO 9141/14230 (not BLE-only).
2. MultiECUScan A1 **green** adapter cable. Croma pump's K-line is on **vehicle OBD pin 12**, while ELM interfaces usually put K-line on **pin 7**.
3. Car parked, ignition on. Never perform diagnostics while driving.

The Android app's Bluetooth connection DOES NOT prove the steering pump ECU is reachable.
The steering pump has independent power via F73, ignition feed via F51, ground and diagnostic K-line, so these should be checked by a qualified person when the pump is unreachable.

## Build APK

Android SDK / Android Studio are NOT installed in the file-generation environment, so **this release is provided as source code, not a compiled or verified APK**.

1. Unzip the project.
2. Open the `CromaSteeringDiag` folder in Android Studio (recent version supporting Android Gradle Plugin 8.7.3).
3. Allow Gradle sync / SDK 35 install.
4. `Build > Build APK(s)`.
5. The APK will usually be in `app/build/outputs/apk/debug/app-debug.apk`.
6. Install on your Android phone; pair the ELM adapter in Android Bluetooth settings first.
7. Connect via the green A1 adapter only when the car is stationary. Select your paired ELM and tap `Connect`.

## Important limitations

- No verified Croma-specific TRW steering protocol implementation or confirmed ECU address.
- The experimental probe is only a diagnostic-development aid; it cannot read actual pump fault codes yet and **must not** be interpreted as failure if it shows `NO DATA`.
- ELM327 clone quality and actual pin routing matter.
- If steering assistance is lost, do not drive until a technician verifies the repair.

## Official references

- Fiat eLearn wiring: https://4cardata.info/elearn/194/2/194000000/194000002/194000005/194001748
- MultiECUScan support list: https://multiecuscan.net/SupportedVehiclesList.aspx
- Green A1 cable information: https://www.gendan.co.uk/product_FESGREEN.html
- AlfaOBD supported modules (TRW 2 Fiat Croma): https://www.alfaobd.com/supported_units.html

## For next version

To implement genuinely reliable pump-ECU DTC reading and parameter decoding, supply: ELM327 adapter brand/model, an A1 adapter photo, and ideally a saved connection log from AlfaOBD / MultiECUScan with this exact replacement pump. Then we can replace the experimental address and messages with verified ones.

## Build from GitHub without Android Studio

A workflow is included at `.github/workflows/build-apk.yml`. See `BUILD-APK.md` for setup and APK download instructions. This workflow has **not** been tested on GitHub Actions in the current environment.
