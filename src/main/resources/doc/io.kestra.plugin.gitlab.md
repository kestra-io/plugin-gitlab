# How to use the GitLab plugin

Create issues and merge requests, search issues, and run and watch CI/CD pipelines in GitLab from Kestra flows.

## Authentication

Set `token` to a GitLab personal, project, or group access token with scopes covering the operations you need. `url` defaults to `https://gitlab.com` — set it to your instance URL for self-hosted GitLab. Set `projectId` to the numeric project ID or URL-encoded project path. Store `token` in a [secret](https://kestra.io/docs/concepts/secret) and set all three on each task.

## Tasks

`issues.Create` opens a new issue — set `title` and optionally `issueDescription` and `labels`.

`issues.Search` queries issues in a project — filter by `search` (free-text), `state` (`opened`, `closed`, or `all`), and `labels`.

`mergerequests.Create` opens a new merge request — set `title`, `sourceBranch`, `targetBranch`, and optionally `mergeRequestDescription`.

`pipelines.Create` starts a pipeline for a `ref` with optional `variables`. With `wait: true` it polls until the pipeline finishes, fails on `failed` or `canceled`, and cancels the pipeline when `maxWaitDuration` is reached or the execution is killed.

`pipelines.Get`, `pipelines.Cancel` and `pipelines.Retry` read, cancel or retry one pipeline by its global `pipelineId`.

`pipelines.List` lists pipelines newest first, filtered by `status`, `ref`, `sha`, `source` or `updatedAfter`; `fetchType: STORE` writes them to internal storage.

## Triggers

`pipelines.Trigger` polls a project and starts an execution when pipelines reach one of the watched `statuses` (default `success`, `failed`, `canceled`); each pipeline fires once.
