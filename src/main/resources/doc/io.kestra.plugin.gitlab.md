# How to use the GitLab plugin

Create issues and merge requests, and search issues in GitLab from Kestra flows.

## Authentication

Set `token` to a GitLab personal, project, or group access token with scopes covering the operations you need. `url` defaults to `https://gitlab.com` — set it to your instance URL for self-hosted GitLab. Set `projectId` to the numeric project ID or URL-encoded project path. Store `token` in a [secret](https://kestra.io/docs/concepts/secret) and apply all three globally with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks

`issues.Create` opens a new issue — set `title` and optionally `issueDescription` and `labels`.

`issues.Search` queries issues in a project — filter by `search` (free-text), `state` (`opened`, `closed`, or `all`), and `labels`.

`mergerequests.Create` opens a new merge request — set `title`, `sourceBranch`, `targetBranch`, and optionally `mergeRequestDescription`.
