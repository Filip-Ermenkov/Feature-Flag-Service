# Take-Home Challenge: Feature Flag Service

Thank you for your interest in joining our platform team! This small challenge gives you a chance to show how you approach building a service — and gives us something concrete to discuss together in the follow-up interview.

## Time expectation

Please spend no more than **4–6 hours** on this. We do not expect a polished, production-ready system. We would rather see a small, clean, working core with a clear README than a large, half-finished feature set. If you run out of time, write down in the README what you would do next — that counts in your favor.

## The scenario

Our platform team provides shared services to many internal and external users. One common platform capability is **feature flags**: named switches that let teams turn functionality on or off without redeploying.

Your task is to build a small **Feature Flag Service** as a Java application exposing a REST API.

## Core requirements

### 1. Data model

A feature flag has at least:

| Field | Type | Notes |
|---|---|---|
| `id` | generated | unique identifier |
| `name` | string | unique, e.g. `new-checkout-flow` |
| `description` | string | optional |
| `enabled` | boolean | |
| `createdAt` / `updatedAt` | timestamp | maintained by the service |

### 2. REST API

Implement the following endpoints (JSON in/out):

- `POST /flags` — create a flag (reject duplicate names with a meaningful error)
- `GET /flags` — list all flags
- `GET /flags/{id}` — get a single flag (404 if not found)
- `PATCH /flags/{id}` — update a flag (at minimum: toggle `enabled`)
- `DELETE /flags/{id}` — delete a flag
- `GET /flags/{name}/evaluate` — returns whether the flag is currently enabled, e.g. `{ "name": "new-checkout-flow", "enabled": true }`

Use appropriate HTTP status codes and return helpful error messages.

### 3. Persistence

Flags must survive a restart of the application. A file-based or embedded database (e.g. H2, SQLite) is perfectly fine. Please structure your code so the storage mechanism could be swapped out later.

### 4. Tests

Write automated tests for the parts you consider most important. We do not expect full coverage — we expect you to make a deliberate choice about *what* is worth testing and to mention that choice in the README.

### 5. README

Include a README covering:

- How to build and run the application (one or two commands, ideally)
- How to call the API (example `curl` commands are enough)
- Key design decisions and trade-offs you made
- What you would improve or add with more time

## Technical constraints

- **Java 21 or newer**
- Build with **Maven or Gradle**
- You may use frameworks and libraries freely (Spring Boot, Quarkus, plain Java with an embedded HTTP server — your choice). Be ready to explain *why* you chose what you chose.
- Submit as a **Git repository** (link to GitHub, or a zipped repo including the `.git` folder). Please commit as you go — we are interested in your working process, not a single "final" commit.

## Submission

Create a new public (or private + shared) repository on [GitHub](https://github.com/new) for this project, push your commits there, and send the repository link to **kalin.kostashki@bosch.com**. If you prefer to keep the repo private, share access with the same address. A zipped repo (including the `.git` folder) emailed to the same address is also acceptable.

## Optional stretch goals

Only if you have time left within the budget — pick **at most one**:

- A `Dockerfile` (and instructions to run the service in a container)
- A simple CI workflow (e.g. GitHub Actions) that builds and runs the tests
- OpenAPI/Swagger documentation for the API
- Per-environment flag values (e.g. a flag enabled in `dev` but not in `prod`)

Not attempting a stretch goal will **not** count against you.

## What we look at

- Does it run, and does the API behave correctly?
- Is the code clear, well-structured, and idiomatic Java?
- Are responsibilities sensibly separated (we care about this more than design-pattern name-dropping)?
- Quality and intent of the tests
- Clarity of the README and your commit history

## Follow-up

In the next interview, we'll walk through your solution together: you'll briefly present it, and we'll discuss your decisions and possibly extend it a little. There are no trick questions — we want to understand how you think.

Good luck, and have fun with it!
