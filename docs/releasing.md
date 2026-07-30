# Releasing Shoumei Player

Shoumei Player uses immutable semantic release tags as the source of truth for
Android versions.

## Version mapping

- Release tag: `vMAJOR.MINOR.PATCH`
- Android `versionName`: `MAJOR.MINOR.PATCH`
- Android `versionCode`: `MAJOR * 1,000,000 + MINOR * 1,000 + PATCH`
- Untagged local build: the `shoumei.versionName` value from
  `gradle.properties`, with `-dev` appended to `versionName`

Minor and patch values must be at most 999. The calculated version code must be
positive and no greater than Android's 2,100,000,000 limit.

## GitHub release secrets

Configure these Actions secrets before pushing the first release tag:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

`ANDROID_KEYSTORE_BASE64` contains the upload keystore encoded as a single
base64 string. The workflow reconstructs it only in the runner's temporary
directory.

## Publishing a release

1. Ensure the release commit is pushed and CI-ready.
2. Create an annotated semantic tag:

   ```powershell
   git tag -a v0.1.0 -m "Shoumei Player 0.1.0"
   git push origin v0.1.0
   ```

3. The tagged-release workflow verifies that:
   - the tag points to the workflow commit;
   - the tag is exactly `vMAJOR.MINOR.PATCH`;
   - the mpv submodule is the checked-in gitlink and exactly matches
     `shoumei.mpvVersion` from `gradle.properties`;
   - all third-party workflow actions are pinned to full commit SHAs.
4. It builds a signed Android App Bundle, writes a SHA-256 checksum, and
   publishes both files on the GitHub release.

Never move or reuse a published release tag. Enable immutable releases in the
GitHub repository settings before the first public release.
