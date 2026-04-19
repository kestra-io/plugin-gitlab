# Kestra Gitlab Plugin

## What

- Provides plugin components under `io.kestra.plugin.gitlab`.
- Includes classes such as `Create`, `Search`, `Create`.

## Why

- What user problem does this solve? Teams need to interact with GitLab projects, commits, and issues from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps GitLab steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on GitLab.

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
