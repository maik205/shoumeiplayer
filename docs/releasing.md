# Release Shoumei Player

Annotated semantic-version tags provide the Android version and trigger the GitHub release workflow.

## Version mapping

The application maps versions as follows:

- Release tag: `vMAJOR.MINOR.PATCH`
- Android `versionName`: `MAJOR.MINOR.PATCH`
- Android `versionCode`: `MAJOR * 1,000,000 + MINOR * 1,000 + PATCH`
- Local development build: `shoumei.versionName` from `gradle.properties` with `-dev` appended

Minor and patch values must not exceed `999`. The calculated version code must remain between `1` and `2,100,000,000`.

## Configure signing

Add these GitHub Actions secrets before publishing a signed release:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

`ANDROID_KEYSTORE_BASE64` contains the upload keystore as a single Base64 string. The workflow decodes it inside the runner’s temporary directory.

Without these secrets, the workflow builds unsigned artifacts that cannot be installed without signing.

## Publish a release

1. Push the release commit and confirm continuous integration passes.
2. Create and push an annotated tag:

   ```powershell
   git tag -a v0.1.0 -m "Shoumei Player 0.1.0"
   git push origin v0.1.0
   ```

3. Wait for the `Tagged release` workflow to validate the tag and mpv source pin.
4. Confirm the GitHub release contains the Android App Bundle, universal Android Package, and `SHA256SUMS.txt`.

The workflow derives `SHOUMEI_RELEASE_TAG` from the pushed tag. It rejects malformed versions or a tag that does not match the workflow commit.

Published release tags must remain immutable.
