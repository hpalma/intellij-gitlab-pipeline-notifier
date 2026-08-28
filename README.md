# GitLab Pipeline Notifier

An IntelliJ Platform plugin that watches your GitLab pipelines and makes damn sure you notice when
one fails.

<!-- Plugin description -->
Watches GitLab CI pipelines over the GitLab REST API and raises a hard-to-miss alerts when one fails.

By default it alerts on pipelines **you** triggered in the GitLab projects matching the git remotes
of the projects you have open. You can also add extra projects to watch, and define rules to be
alerted about other people's failures — filtered by triggering user, branch/tag glob, and pipeline
source (push, merge request, schedule, web, trigger, api).

Each rule chooses how loud it gets:

- **Sticky balloon** — an in-IDE error notification that stays until you dismiss it, plus a red badge
  and an attention request on the application icon (a Dock bounce on macOS).
- **System notification** — a native OS notification, so you see it even when the IDE is in the
  background. The platform suppresses this automatically while the IDE is focused.
- **Modal dialog** — a blocking dialog brought to the front. Maximum visibility, maximum
  interruption; off by default for everything except your own failures.

Configure it under **Settings | Tools | GitLab Pipeline Notifier**. You need a GitLab personal access
token with the `read_api` scope; it is stored in the IDE's password safe (macOS Keychain, Windows
Credential Store, or libsecret), never in plain-text settings.
<!-- Plugin description end -->

## Configuration

1. Create a GitLab personal access token with the `read_api` scope.
2. **Settings | Tools | GitLab Pipeline Notifier** — set your GitLab host and paste the token, then
   hit **Test connection** to confirm it resolves your username.
3. Optionally add extra projects and notification rules.

## Building

Written in Java, no Kotlin. Requires JDK 25 - IntelliJ Platform 2026.2 ships Java 25 bytecode
(class file version 69), so an older compiler cannot read the platform jars. The Gradle foojay
toolchain resolver downloads the JDK automatically.

Gson is used for JSON and is `compileOnly`: the platform already bundles it
(`lib/intellij.libraries.gson.jar`), so the plugin zip contains only its own jar.

```bash
./gradlew buildPlugin   # build the distributable zip
./gradlew runIde        # launch a sandbox IDE with the plugin installed
./gradlew check         # run unit tests
./gradlew verifyPlugin  # IntelliJ Plugin Verifier
```
