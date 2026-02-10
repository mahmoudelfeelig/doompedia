# Release Checklist

## Pipeline/Data
- Run: `python3 -m compileall -q data-pipeline/src`
- Run pipeline tests:
  - `cd data-pipeline`
  - `PYTHONPATH=src pytest -q`
- Build real pack:
  - `./scripts/build_en_1m_pack.sh`
- Publish hosted layout:
  - `BASE_URL=<your-cdn-base>/packs/en-core-1m/v1 ./scripts/publish_pack.sh`
- Deploy hosted files:
  - `S3_BUCKET=<bucket> S3_PREFIX=<prefix> ./scripts/deploy_pack_to_s3.sh`

## Android
- Build unit tests:
  - `cd android-app`
  - `./gradlew testDebugUnitTest`
- Build release artifact:
  - `./gradlew assembleRelease`
- Manual QA:
  - first run seed load
  - manifest update check
  - offline feed/search/bookmarks/saved-folders
  - TalkBack navigation
  - light/dark/system theme + accent color
  - settings import/export JSON

## iOS
- Generate and build:
  - `cd ios-app`
  - `xcodegen generate`
  - `xcodebuild -project Doompedia.xcodeproj -scheme Doompedia -sdk iphonesimulator -destination "platform=iOS Simulator,name=iPhone 15" CODE_SIGNING_ALLOWED=NO build`
- Manual QA:
  - first run seed load
  - manifest update check
  - offline feed/search/bookmarks/saved-folders
  - VoiceOver navigation
  - dynamic type + light/dark + accent color
  - settings import/export JSON

## Go/No-Go
- Pack manifest URL works from clean install.
- Crash-free smoke test across at least:
  - Android mid-tier device
  - iPhone recent simulator/device
- Accessibility checks pass for core flows.
- Attribution section in Settings and license links verified.
