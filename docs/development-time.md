# Development time

An approximate record of the time spent developing msfx.lib with Claude Code,
to give an order of magnitude of the effort, and to compare it with earlier
libraries of the same kind written by hand.

## How it is measured

- **Up to 2026-11-17**: the author's estimate of the coding effort.
- **From 2026-11-17**: the active time of the sessions with Claude Code. Each
  message and each answer is logged with its time; the time between two
  events counts when they are less than 30 minutes apart, so longer pauses
  are left out as breaks.

Not counted: work done outside the sessions, and the design thinking done
away from the computer, which earlier developments needed too.

## Record

| Period | Time | Work |
|---|---|---|
| 2026-10-26 to 2026-11-17 | 40 h (estimate) | md metadata, db engines, pool and adapters, SQL analyzer, QSelect, QInsert, QDelete, QUpdate, tests |
| 2026-11-17, from 08:20 | 0.0 h (2 min) | time tracking, README updated |

**Total: 40 h.**

## Size of the code

At the last update, 2026-11-17:

| Part | Files | Lines |
|---|---|---|
| Library, `java/src/msfx` | 80 | 20,700 |
| Tests, `java/src/test/claude` | 13 | 5,100 |

Lines include the Javadoc of every member. The CMA catalog used by the tests,
8,580 lines, is generated and not counted.
