# Kestra Gitlab Plugin

## What

- Provides plugin components under `io.kestra.plugin.gitlab`.
- Includes classes such as `Create`, `Search`, `Create`.

## Why

- This plugin integrates Kestra with GitLab.
- It provides tasks that interact with GitLab projects, commits, and issues via the API.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `gitlab`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.gitlab.issues.Create`
- `io.kestra.plugin.gitlab.issues.Search`
- `io.kestra.plugin.gitlab.mergerequests.Create`

### Project Structure

```
plugin-gitlab/
├── src/main/java/io/kestra/plugin/gitlab/mergerequests/
├── src/test/java/io/kestra/plugin/gitlab/mergerequests/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
