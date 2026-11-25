# FACE AI — Android APK project

A fullscreen Android WebView app using the original animated face engine, first-run nickname setup, Android runtime camera/microphone permissions (served from a secure local WebView asset origin), native Android Bengali speech recognition and Text-to-Speech, and optional Gemini vision/chat.

## Build APK with GitHub Actions (phone-only workflow)
1. Create a new GitHub repository.
2. Upload the contents of this project (keep `.github/workflows/android-apk.yml`).
3. Open **Actions** → **Build FACE AI APK** → **Run workflow**.
4. Wait for the green successful run.
5. Open the run → **Artifacts** → download `FACE-AI-debug-apk`.
6. Extract the ZIP; install `app-debug.apk` on your Android phone.

The workflow creates a debug APK. It is suitable for personal testing and sideloading. A Play Store release needs a separately generated signing key and a signed release build.

## First launch
- Android requests Camera and Microphone permissions immediately.
- The app then asks for a nickname once.
- After ENTER, the face fills the screen without persistent text or buttons.
- Tap once: voice input.
- Double tap: ask what the camera currently sees.
- Long press the face: open private setup for Gemini API key.
- Camera remains active after onboarding while the app is in the foreground.

## Gemini API
1. Create a key in Google AI Studio: https://aistudio.google.com/app/apikey
2. Long-press the face and paste the key into the private setup.
3. Tap SAVE. The key is kept in this app's local WebView storage and is not included in the APK source.

The app calls `gemini-2.5-flash` for text + a still camera frame when you ask a question. Free-tier access and rate limits depend on Google's current policy and can change. A key embedded in a distributed APK can be extracted; for a public release, put the key behind your own server/proxy and apply quotas. Do not share your key.

## Important limitations
- The camera feed is kept locally in the app; when you ask a question, a compressed still image is sent to Gemini with your prompt. Do not point it at private material unless you are comfortable sending that frame to the API provider.
- This is not continuous live video streaming to Gemini. It analyzes a snapshot when asked.
- Android SpeechRecognizer uses the device's speech service and needs internet on many devices. Bengali voice availability depends on installed speech services.
- Camera/microphone permission denial means the corresponding feature cannot work; Android does not allow an app to silently force-grant permissions.
- API free tier is not guaranteed unlimited or permanently free.
