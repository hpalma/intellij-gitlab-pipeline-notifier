<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Pipeline Notifier Changelog

## [Unreleased]

### Added

- GitHub Actions support: failed workflow runs are watched alongside GitLab pipelines, on github.com
  or GitHub Enterprise Server. Each service has its own tab in settings, with its own URL, token and
  extra projects.
- Notification rules now belong to a service (GitLab or GitHub), so each offers its own pipeline sources.

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

[1.0.1]: https://github.com/hpalma/gitlab-pipeline-notifier/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/hpalma/gitlab-pipeline-notifier/commits/v1.0.0
