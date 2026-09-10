<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# GitLab Pipeline Notifier Changelog

## [Unreleased]

## [1.0.0-beta.1] - 2026-09-10

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

[Unreleased]: https://github.com/hpalma/gitlab-pipeline-notifier/compare/v1.0.0-beta.1...HEAD
[1.0.0-beta.1]: https://github.com/hpalma/gitlab-pipeline-notifier/commits/v1.0.0-beta.1
