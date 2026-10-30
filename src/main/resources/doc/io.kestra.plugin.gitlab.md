# How to use the GitLab plugin

Create issues and merge requests, search issues, and run and watch CI/CD pipelines in GitLab from Kestra flows.

## Authentication

Set `token` to a GitLab personal, project, or group access token with scopes covering the operations you need. `url` defaults to `https://gitlab.com` — set it to your instance URL for self-hosted GitLab. Set `projectId` to the numeric project ID or a `group/project` path. The value is sent as one URL segment; an already encoded path such as `group%2Fproject` also works. Store `token` in a [secret](https://kestra.io/docs/concepts/secret) and set all three on each task.

## Tasks

`issues.Create` opens a new issue — set `title` and optionally `issueDescription` and `labels`.

`issues.Search` queries issues in a project — filter by `search` (free-text), `state` (`opened`, `closed`, or `all`), and `labels`.

`mergerequests.Create` opens a new merge request — set `title`, `sourceBranch`, `targetBranch`, and optionally `mergeRequestDescription`.

`pipelines.Create` starts a pipeline for a `ref` with optional `variables`. With `wait: true` it polls every `pollInterval` (default `PT10S`, minimum `PT1S`) until the pipeline finishes, fails on `failed` or `canceled`, and cancels the pipeline when `maxWaitDuration` (default `PT1H`) is reached or the execution is killed.

`pipelines.Get`, `pipelines.Cancel` and `pipelines.Retry` read, cancel or retry one pipeline by its global `pipelineId`.

`pipelines.List` lists pipelines newest first, filtered by `status` (for example `FAILED`), `ref`, `sha`, `source` (for example `SCHEDULE`) or `updatedAfter`, and sorted by `orderBy` (`ID` or `UPDATED_AT`, for example). `limit` (1 to 10000, default 20) caps the number read. `fetchType: FETCH_ONE` returns the first pipeline as `row`, `FETCH` returns them all, `STORE` writes them to internal storage page by page, and `NONE` returns only `size`.

## Triggers

`pipelines.Trigger` polls a project and starts an execution when pipelines reach one of the watched `statuses` (default `SUCCESS`, `FAILED`, `CANCELED`); each pipeline fires once. The first poll only records the pipelines that already finished, without starting an execution. Later polls ask only for pipelines updated since the last one recorded and inspect up to `maxPipelines` (1 to 100, default 20) of them.
