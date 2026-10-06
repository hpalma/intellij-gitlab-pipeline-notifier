# Pipeline Notifier

An IntelliJ Platform plugin that watches your GitLab pipelines and makes damn sure you notice when
one fails.

<!-- Plugin description -->
Watches GitLab CI pipelines and GitHub Actions workflow runs over their REST APIs and raises
hard-to-miss alerts when one fails.

By default it alerts on pipelines **you** triggered in the GitLab and GitHub projects matching the
git remotes of the projects you have open. You can also add extra projects to watch, and define rules to be
alerted about other people's failures — filtered by triggering user, branch/tag glob, and pipeline
source (push, merge request, schedule, manual, API...). Rules belong to one service, since each has
its own set of sources.

Each rule chooses how loud it gets:

- **Sticky balloon** — an in-IDE error notification that stays until you dismiss it, plus a red badge
  and an attention request on the application icon (a Dock bounce on macOS).
- **System notification** — a native OS notification, so you see it even when the IDE is in the
  background. The platform suppresses this automatically while the IDE is focused.
- **Modal dialog** — a blocking dialog brought to the front. Maximum visibility, maximum
  interruption; off by default for everything except your own failures.

Configure it under **Settings | Tools | Pipeline Notifier**, one tab per service. Tokens are
stored in the IDE's password safe (macOS Keychain, Windows Credential Store, or libsecret), never in
plain-text settings. Self-hosted GitLab and GitHub Enterprise Server are supported.
<!-- Plugin description end -->

## Configuration

1. Create an access token for each service you use:
   - **GitLab**: a personal access token with the `read_api` scope.
   - **GitHub**: a fine-grained personal access token with read access to *Actions* on the
     repositories to watch, or a classic token with the `repo` scope.
2. **Settings | Tools | Pipeline Notifier** — in the service's tab, set the server URL and
   paste the token, then hit **Test connection** to confirm it resolves your username.
3. Optionally add extra projects and notification rules.

## Adding another CI service

Everything outside the service's own package works on a provider-neutral `PipelineRun`. To add one,
implement `CiProvider` (name, default host, token help, rule sources) and `CiClient` (current user,
failed runs, run detail, failed jobs, project listing) under `org.hugopalma.gitlabpipelinenotifier`,
then add it to `CiProviders`. Settings tabs, rules, polling, dedupe and alerts pick it up from there.
See the `gitlab` and `github` packages for examples.

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
