# 004 — `org.json` silently returns defaults in JVM unit tests

**Status:** Fixed
**Severity:** Medium — a whole class of parsing bugs was untestable, and tests written against it
would have passed for the wrong reason.

## What happened

`app/build.gradle.kts` sets:

```kotlin
testOptions { unitTests.isReturnDefaultValues = true }
```

`org.json.JSONArray`/`JSONObject` are part of the Android framework, so in a JVM unit test they
resolve to the `android.jar` stubs. With `isReturnDefaultValues = true` those stubs do not throw
`RuntimeException("Stub!")` — they quietly return zero values. `JSONArray(json).length()` returns
`0` for any input.

The first run of `MpvTrackListTest` failed with `expected:<{1=7, 2=9}> but was:<{}>` — an empty parse
result, not a stub exception. Had the tests been written to assert something weaker (for example
"parsing does not throw"), they would have passed against a parser that never parses anything.

## Fix

Put a real implementation on the unit-test classpath, where it shadows the `android.jar` stub:

```kotlin
// gradle/libs.versions.toml
orgJson = "20250107"
org-json = { group = "org.json", name = "json", version.ref = "orgJson" }

// app/build.gradle.kts
testImplementation(libs.org.json)
```

## Wider consequence

Any future JVM test touching Android framework classes that are pure data (`org.json`, `android.net.Uri`,
`android.text.TextUtils`, `Base64`) will hit the same trap: it will not fail loudly, it will return
`null`/`0`/`false`. Prefer asserting on concrete parsed values rather than on the absence of an
exception, so a stubbed dependency shows up as a failure.
