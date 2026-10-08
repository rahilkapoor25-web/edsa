# EDSA — Automated Exam Duty & Seating Allocation System

## What the system does

EDSA is a Java web application that turns four spreadsheets — students, rooms, faculty,
timetable — into a complete exam seating plan and invigilation duty roster that satisfies
every rule the examination office follows. The coordinator uploads the CSVs, clicks once,
and gets a plan back.

Five pages: **Import** (upload the four CSVs, show row counts and malformed-row warnings),
**Solve** (start the run, show progress and the violation count falling to zero),
**Plan viewer** (seating and duty tables, filter by room or date), **Disruption** (mark a
faculty member absent, show a diff of what the repairer changed), **Reports** (preview and
download seating charts, attendance sheets, workload summary).

Source of truth for the design: `docs/EDSA_Tech_Brief.pdf`.

## Stack

| Technology | Used for |
| --- | --- |
| Java 17 (LTS) | The whole application |
| Spring Boot + Thymeleaf | HTTP layer and the five pages, server-rendered HTML + minimal CSS |
| JDBC | All database access, written as plain Java |
| H2 (now), MySQL (later) | Students, rooms, faculty, timetable, saved plans, duty history |
| CSV + `BufferedReader` | Loading the four input files |
| Threads + `synchronized` | Running the solver off the request thread, background rule checking |
| Maven | Build, dependencies, test execution |
| JUnit 5 | Unit and integration tests |
| JaCoCo | Statement and branch coverage reports |
| GitHub Actions | Continuous integration |

**Not used:** no ORM — no Hibernate, no JPA, no `spring-boot-starter-data-jpa`. Database
access is written directly in JDBC. No security framework either; the prototype runs on the
college network, recorded as a scope limitation in the SRS. See **The sign-in gate is not
authentication** below.

The database is H2 in-memory today (`src/main/resources/application.properties`) and moves
to MySQL later. Because everything goes through JDBC, that swap is a driver dependency plus
the datasource properties — no code change.

## Package architecture — the one-way rule

The code sits in three packages, each with one job. `edsa.web` shows the pages, `edsa.data`
reads and writes files, and `edsa.core` makes all the decisions.

```
Browser  →  edsa.web    controllers, templates
            edsa.web    →  edsa.data    CSV, reports, saved plans
            edsa.web    →  edsa.core    model, rules, solver
            edsa.data   →  edsa.core
            edsa.core   →  (nothing — no imports upward)
```

> "Arrows point one way only: the pages may use the core, but the core never uses the pages."

Concretely:

- **`edsa.core` must never import `edsa.web`, `edsa.data`, or Spring** (no
  `org.springframework.*`), and no web, JDBC or template types. Plain Java only.
- `edsa.data` may import `edsa.core`. It must never import `edsa.web`.
- `edsa.web` may import both.

That one-way rule is the reason the decision-making can be tested without opening a browser,
and the reason the pages could be replaced later without touching the rules. It is also what
makes `edsa.core.CliRunner` work headlessly — "because `edsa.core` has no web dependency."

If a change seems to need an upward import, the class is in the wrong package. Move the
class, don't add the import.

## Controllers decide nothing

> "One rule for this folder: pages never decide anything. A page takes the request, asks the
> core, and shows the answer. Any rule about seniority or capacity written inside a page is
> in the wrong folder."

No controller in `edsa.web` contains allocation or rule logic. A controller binds the
request, calls into `edsa.core` (or `edsa.data`), puts the result on the model, and picks a
view. Anything that decides who sits where, who invigilates, whether a plan is legal, or how
a plan is scored belongs in `edsa.core` — even a single `if` about capacity or seniority.

## What lives where

**`edsa.core` — the brain**

- *The things:* `Student`, `Faculty`, `Room`, `ExamSlot`, `Seat`, `SeatingPlan`, `DutyRoster`,
  `Assignment`, `WorkloadLedger`, and `Person` as a shared parent of `Student` and `Faculty`.
- *The rules:* a `Constraint` interface, split into hard and soft, with one class per rule and
  a `PlanChecker` that runs them all and lists what is broken.
- *The decisions:* `GreedyAllocator` makes a first plan, `LocalSearchImprover` makes it fairer,
  `AbsenceRepairer` handles a missing invigilator, `ValidatorThread` keeps checking the plan in
  the background.
- *The errors:* four custom exceptions — capacity, broken rules, no substitute available, bad
  input files.

**`edsa.data` — the files**

Reads the four CSVs and checks every row, writes the reports out, loads them into the
database, saves and reloads finished plans, keeps a log of what the coordinator did. A bad row
is reported with the file name and line number rather than skipped quietly.

**`edsa.web` — the five pages**

Controllers and Thymeleaf templates for Import, Solve, Plan viewer, Disruption and Reports.
The Solve page starts the job on a background thread and polls for progress, so the browser
never sits waiting for the solver.

Built so far: **Data and Run** (`/`) loads the four files or the bundled sample, shows row
counts, computes a plan and lists every rule with pass/fail and a breach count; **Room
schedule** (`/rooms`) shows the seating plan, filterable by room, with a CSV download at
`/seating-plan.csv`; **Workloads** (`/workloads`) shows duties per invigilator. `Workspace`
holds the loaded data and the computed plan for the whole application.

Controllers ask the core and hand the answer to the template — no filtering, counting or
grouping in a controller. When a page needs the plan shaped a particular way, that shape is a
method on `ExamPlan` (`sittings()`, `sittingsIn(roomId)`, `workloads()`, `roomsUsed()`), not a
loop in `edsa.web`.

Every page goes through one layout, `templates/layout.html`, which owns the header and tab
bar. A page supplies its content as a fragment:

```html
<html th:replace="~{layout :: page('Overview', 'overview', ~{:: main})}">
```

Styling is one stylesheet, `static/css/edsa.css`, with the colours, fonts and spacing as
custom properties at the top — change them there, not in the rules below. Inter for text,
JetBrains Mono for ids and counts (`.mono`, `.code`, `.is-numeric`), one green accent, white
cards with thin borders on a light canvas. No JavaScript framework, and no CSS build step.

## The sign-in gate is not authentication

`LoginController` accepts **any** address ending in `@thapar.edu` with **any** non-empty
password. Nothing is verified: there are no accounts, no password is ever checked, and
nothing is stored beyond the typed address in the HTTP session. `SignedInFilter` sends
requests without that session attribute to `/login`, and `/logout` invalidates the session.

This is a placeholder gate so the pages have someone to name and a way out, not a security
boundary. Anyone who can reach the server can type an address and get in. The missing
security layer is recorded as a scope limitation in the SRS. Replacing it means replacing
`LoginController` and `SignedInFilter` with a real mechanism, not extending them — and until
that happens, do not put anything behind this gate that would matter if it were bypassed.

## Rules and scoring

**Hard constraints** decide whether a plan is allowed at all — anything that breaks one is
thrown away immediately: no invigilator on their own subject's paper, no double-booking of a
person or room, room capacity never exceeded, no two students of the same paper seated
adjacent.

**Soft constraints** are preferences that add penalty points. Adding up those points gives the
plan a score, and **a lower score is a better plan**. That one number is the system's whole
idea of "better".

| Soft constraint | Penalty |
| --- | --- |
| Workload fairness | (duties − fair share)², per invigilator |
| Consecutive slots | 5 per back-to-back pair |
| Seniority cover | 10 per room with no senior |
| Department spread | 3 per single-department room |

These weights encode the examination office's priorities. They live in one configuration class
so the ordering changes without touching the solver.

**Determinism:** the same input always gives the same output. Nothing is learned or remembered
between runs.

## Adding a constraint later

1. One new class implementing `Constraint`, in `edsa.core`.
2. One JUnit test class proving it rejects what it should and accepts what it should.
3. One line registering it in the constraint list, plus a weight if it is soft.
4. One pull request.

Nothing in the solver, the storage layer or the web layer changes. If adding a rule ever
requires editing `GreedyAllocator`, the abstraction has leaked — investigate that before
writing more rules.

## Build and run

```
mvn clean test        compile and run every test, with a JaCoCo coverage report
                      written to target/site/jacoco/index.html
mvn spring-boot:run   start the web application on http://localhost:8080
mvn clean package     build the executable jar into target/
```

Headless engine run:

```
java -cp target/classes edsa.CliRunner
java -cp target/classes edsa.CliRunner students.csv rooms.csv faculty.csv timetable.csv
```

With no arguments it runs on the bundled sample files in `src/main/resources/sample`.

That command drives the engine without the web layer. It is how the solver is tested and
demonstrated while the pages are still being built, and it works only as long as the one-way
rule above holds. The brief writes this entry point as `edsa.core.CliRunner`, but it loads
CSVs and so needs `edsa.data`; it lives in the root `edsa` package beside `EdsaApplication`,
as a composition root may depend on both layers while `edsa.core` stays clean.

CI runs `mvn clean test` on every push and pull request (`.github/workflows/ci.yml`).
