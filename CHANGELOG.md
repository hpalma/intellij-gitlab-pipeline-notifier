<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Pipeline Notifier Changelog

## [Unreleased]

## [1.1.0] - 2026-10-07

### Added

- GitHub Actions support: failed workflow runs are watched alongside GitLab pipelines, on github.com
  or GitHub Enterprise Server. Each service has its own tab in settings, with its own URL, token and
  extra projects.
- Notification rules now belong to a service (GitLab or GitHub), so each offers its own pipeline sources.

### Security

- Project paths from git remotes and settings are validated, and encoded in GitHub API URLs.
- Editing a server URL clears the access token that was loaded for the old one, and the old token is
  removed from the password safe. A warning is shown for plain `http://` URLs.
- Text from the server shown in alerts (branch, job and repository names) is stripped of markup and
  control/bidirectional characters and length-capped.
- Log redaction covers passwords containing `@` and `token=` query values.

### Fixed

- A throttled server (HTTP 429, or 403 with `Retry-After` or an exhausted quota) is left alone until
  it is ready, instead of being asked again every poll. A 403 on a single project no longer pauses
  the whole service.
- The poller backs off when every project of a service fails.
- Applying settings before the stored token has loaded no longer overwrites it.
- GitHub logins are compared case-insensitively, and runs are attributed to the original actor so
  they match the filter used to find them.
- The poll watermark trails the newest run by a minute so late-listed runs are not skipped.
- GitHub repository paths differing only in case are watched once.

### Changed

- Internals are split into a provider-neutral core and one adapter per CI service, so further
  services can be added without touching polling, matching or alerting.

## [1.0.1] - 2026-10-04

### Fixed

- Small code improvements and security fixes.

## [1.0.0] - 2026-09-21

### Added

- Poll GitLab for failed pipelines in projects matched from the open project's git remotes, plus any
  extra project paths configured in settings.
- Alert on pipelines triggered by the current user, resolved from the personal access token.
- Configurable rules to alert on other failures, filtered by triggering user, branch/tag glob and
  pipeline source.
- Three independently toggleable alert channels per rule: sticky balloon with application-icon badge
  and attention request, native system notification, and a blocking modal dialog.
- Personal access token stored in the IDE password safe.
- Setting to alert again when a retried pipeline fails again, instead of only once per pipeline.

[Unreleased]: https://github.com/hpalma/gitlab-pipeline-notifier/compare/v1.1...HEAD
[1.0.1]: https://github.com/hpalma/gitlab-pipeline-notifier/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/hpalma/gitlab-pipeline-notifier/commits/v1.0.0
[1.1]: https://github.com/hpalma/gitlab-pipeline-notifier/compare/v1.0.1...v1.1
