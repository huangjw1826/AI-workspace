# ADR-001: Keep 3.0 Tasks In-Process With Recovery Guards

## Status

Accepted

## Date

2026-05-23

## Context

AI Recorder currently runs as a local Windows application with FastAPI, SQLite, local files, and a Vite frontend. Transcription and summary tasks are started through FastAPI `BackgroundTasks`.

The 3.0 reliability problem is that queued or running tasks can be lost if the backend process exits. Moving immediately to Redis, Celery, or a separate worker service would add deployment complexity to a single-machine app that is intentionally easy to start with the existing scripts.

## Decision

Keep the 3.0 task runner in-process for now, but add reliability guards:

- On startup, mark leftover `queued` and `running` tasks as interrupted errors so they do not stay stuck forever.
- Reuse an existing active task for the same recording and task type instead of creating duplicates.
- Respect `ASR_MAX_CONCURRENCY` with a process-local ASR semaphore.
- Add timeouts for ffmpeg and LLM requests.
- Add a cancel endpoint and check cancellation between workflow stages.

## Alternatives Considered

### Celery or RQ with Redis

- Pros: Real queue semantics, worker isolation, easier hard cancellation later.
- Cons: Requires another service and more Windows setup work.
- Rejected for 3.0 because the app is still optimized for local, low-friction use.

### Dedicated multiprocessing worker

- Pros: Better process-level cancellation for ASR and ffmpeg.
- Cons: More lifecycle and packaging complexity.
- Deferred until cancellation needs hard process termination.

### Leave BackgroundTasks unchanged

- Pros: No new code.
- Cons: Running tasks can remain stuck after restart and duplicate task submissions can overload CPU.
- Rejected because it undermines daily-use reliability.

## Consequences

- 3.0 gets a safer task lifecycle without changing deployment shape.
- Cancellation is cooperative: it prevents future stages and queued work, but it does not yet forcibly kill an already-running FunASR call.
- If future versions need stronger guarantees, the task service module and ADR make it easier to migrate to a real worker or process pool.
