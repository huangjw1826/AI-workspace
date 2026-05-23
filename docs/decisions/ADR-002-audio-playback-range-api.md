# ADR-002: Serve Recorded Audio Through a Range-Aware API

## Status

Accepted

## Date

2026-05-23

## Context

AI Recorder 3.0 adds in-app audio playback and transcript timeline navigation. The frontend needs a browser-playable URL for each recording, but the app stores audio on the local Windows filesystem and recordings can live outside the app data directory when imported from a watched folder.

The API must not expose arbitrary filesystem paths or require loading large audio files into memory.

## Decision

Add `GET /api/recordings/{recording_id}/audio`.

The endpoint:

- Looks up the recording by database ID.
- Reads only the `original_path` stored for that recording.
- Returns `404` when the recording or file is missing.
- Supports HTTP byte ranges so browser audio controls can seek without downloading the whole file.
- Streams the file in chunks.

## Alternatives Considered

### Return raw filesystem paths to the frontend

- Pros: Simple to implement.
- Cons: Browser access to local paths is unreliable and leaks machine-specific paths.
- Rejected.

### Copy every watched audio into `data/recordings`

- Pros: Easier path containment.
- Cons: Duplicates large files and contradicts the existing watch behavior that preserves original files.
- Rejected for 3.0.

### Serve files through static middleware

- Pros: Simple for files under one directory.
- Cons: Watch-imported recordings can live in user-selected directories, and static mounts would risk exposing more than the selected recording.
- Rejected.

## Consequences

- Playback works through a stable API URL rather than local file paths.
- Range support improves seeking and large-file behavior.
- The endpoint trusts database registration as the access boundary. If future versions add multi-user access, this endpoint must add authorization checks before returning audio.
