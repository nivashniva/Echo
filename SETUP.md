# Setup Instructions

This document provides instructions for setting up the Nivukx project for development.

## Prerequisites

- Android Studio (latest version recommended)
- Android SDK (API level as specified in `build.gradle.kts`)
- JDK 21
- Git

## Initial Setup

### 1. Clone the Repository

```bash
git clone https://github.com/iad1tya/Echo-Music.git
cd Echo-Music
```

### 2. Configure Local Properties

Create a `local.properties` file from the template:

```bash
cp local.properties.template local.properties
```

Edit `local.properties` and set your Android SDK path:

```properties
sdk.dir=/path/to/your/android/sdk
```

You may also keep local development credentials here. Never commit them.

```properties
LASTFM_API_KEY=your_lastfm_api_key
LASTFM_SECRET=your_lastfm_secret
GH_CLIENT_ID=your_github_client_id
GH_CLIENT_SECRET=your_github_client_secret
FLOW_NEURO_BASE_URL=https://api.flowneuroengine.com
FLOW_NEURO_API_KEY=your_flow_neuro_key
```

The build reads these secrets from `local.properties` first and environment variables second. CI should use environment variables or repository secrets only.

**Example SDK paths:**

- macOS: `/Users/username/Library/Android/sdk`
- Linux: `/home/username/Android/sdk`
- Windows: `C:\\Users\\username\\AppData\\Local\\Android\\sdk`

### 3. Configure Firebase (Optional)

Firebase is used for analytics and crash reporting. If you want to use these features:

1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Add an Android app to your Firebase project
3. Download the `google-services.json` file
4. Place it in the `app/` directory

**Note:** If you skip Firebase setup, the app can still use the normal GMS build configuration without Firebase services enabled.

### 4. Configure Release Signing (Optional)

For release builds, configure signing through environment variables. Do not commit passwords or keystores.

```bash
export STORE_PASSWORD=your_store_password
export KEY_ALIAS=your_key_alias
export KEY_PASSWORD=your_key_password
```

### 5. Build the Project

Open the project in Android Studio or build from the command line.

Nivukx ships as a single **GMS** build variant with Google Cast and Firebase support. The obsolete FOSS variant has been removed.

```bash
# Debug build
./gradlew assembleUniversalGmsDebug

# Release build (requires signing configuration)
./gradlew assembleUniversalGmsRelease

# CI-equivalent checks
./gradlew :app:compileUniversalGmsDebugKotlin
./gradlew lintUniversalGmsDebug
```

*(On Windows, use `.\\gradlew.bat` instead of `./gradlew`.)*

### 6. Configure AI Translation (Optional)

Nivukx supports AI-powered lyrics translation. You can configure this in **Settings -> AI Settings**.

#### Option A: Using OpenRouter (Default)

1. Get an API key from [OpenRouter](https://openrouter.ai/).
2. In the app, open **Settings -> AI Settings**.
3. Ensure **Provider** is set to **OpenRouter**.
4. Enter your API key.

#### Option B: Using a Custom Provider

1. In the app, open **Settings -> AI Settings**.
2. Select your provider.
3. For a custom provider, enter its base URL.
4. Enter the API key.

## Important Files

### Confidential Files (Never commit these)

- `local.properties` - local SDK path and optional development secrets
- `app/google-services.json` - Firebase configuration when used
- `*.keystore` - release signing keys
- `gradle.properties` - may contain local Gradle configuration

These files should never contain credentials that are checked into source control.

### Template Files (Safe to commit)

- `local.properties.template` - template for local configuration

## Troubleshooting

### Build Fails with "SDK location not found"

Make sure `local.properties` contains the correct SDK path.

### Firebase-related Build Errors

Verify that `app/google-services.json` is valid for the application ID and that the GMS build is being used:

```bash
./gradlew assembleUniversalGmsDebug
```

### Gradle Sync Issues

Try cleaning and rebuilding:

```bash
./gradlew clean
./gradlew :app:compileUniversalGmsDebugKotlin
```

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
