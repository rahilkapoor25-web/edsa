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
| Spring Security | Form login against the account table, CSRF, sessions |
| H2 (now), MySQL (later) | Accounts, imported students, rooms, faculty, subjects, enrollments, exams |
| Apache POI | Reading the one uploaded workbook and writing the template |
| CSV + `BufferedReader` | The legacy four-file upload, still supported |
| Threads + `synchronized` | Running the solver off the request thread, background rule checking |
| Maven | Build, dependencies, test execution |
| JUnit 5 | Unit and integration tests |
| JaCoCo | Statement and branch coverage reports |
| GitHub Actions | Continuous integration |

**Not used:** no ORM — no Hibernate, no JPA, no `spring-boot-starter-data-jpa`. Database
access is written directly in JDBC, including the account and import tables. The schema lives
in `src/main/resources/db/schema.sql` and is applied on start-up.

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
  Plus, for absences: `AbsenceWindow` (an interval somebody is away), `Absence`, `LeaveRequest`
  and `LeaveStatus`.
- *The rules:* a `Constraint` interface, split into hard and soft, with one class per rule and
  a `PlanChecker` that runs them all and lists what is broken.
- *The decisions:* `TimetableScheduler` places the papers, `GreedyAllocator` seats and staffs
  them, `AbsenceRepairer` covers an invigilator who cannot attend, and `ExamPlanner` runs the
  three steps plus the independent check. Still to come: `LocalSearchImprover` for fairness
  and `ValidatorThread` for background re-checking.
- *The errors:* four custom exceptions — capacity, broken rules, no substitute available, bad
  input files.

**`edsa.data` — the files**

Reads the four CSVs and checks every row, writes the reports out, loads them into the
database, saves and reloads finished plans, keeps a log of what the coordinator did. A bad row
is reported with the file name and line number rather than skipped quietly. Also stores the
recorded absences (`LeaveRequestRepository`) and reads the imported faculty back out with
their contact details (`ExamDataRepository.facultyContacts()`, `FacultyContact`) for the
Accounts page to provision from.

**`edsa.web` — the five pages**

Controllers and Thymeleaf templates for Import, Solve, Plan viewer, Disruption and Reports.
The Solve page starts the job on a background thread and polls for progress, so the browser
never sits waiting for the solver.

Built so far: **Data and Run** (`/`) imports the workbook, picks the mode and lists every rule
with pass/fail and a breach count; **Room schedule** (`/rooms`) shows the sittings as one
table, with seat-by-seat grids once a room is chosen; **Workloads** (`/workloads`) shows duties
per invigilator; **Disruption** (`/disruption`) records an absence, has it confirmed or rejected, and shows the diff;
**Accounts** (`/users`, administrators only) creates and disables accounts. Downloads:
`/exam-plan.xlsx` (Timetable, Seating and Duties sheets), `/seating-plan.csv`, and
`/import-template.xlsx`. `Workspace` holds the loaded data, the chosen mode, the computed plan
and the last repair; importing data or changing the mode plans it straight away.

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

## Authentication

Sign-in is decided by the `app_user` table, never by the shape of the address. An address is
accepted only when an active account exists for it and the password matches the stored BCrypt
hash, so `rahilkapoor1be@thapar.edu` gets in when it is registered and `r@thapar.edu` does
not, while a Gmail address registered by the office does. Spring Security owns the login POST
(`SecurityConfig`); `LoginController` only renders the form.

- Passwords are hashed with BCrypt in `AccountService` and never stored, logged or echoed.
- An unknown address, a wrong password and a disabled account all return the same message,
  so the form cannot be used to discover who has an account.
- CSRF protection is on for every state-changing request; the session id is regenerated at
  login (session fixation) and invalidated at logout, with the cookie cleared.
- Everything is protected except `/login`, `/css/**` and the error page.

**Creating the first account.** There is no default password. Set these in the environment
before the first start and an administrator is created once, only while the table is empty:

```
EDSA_SEEDADMIN_ENABLED=true
EDSA_SEEDADMIN_EMAIL=you@thapar.edu
EDSA_SEEDADMIN_PASSWORD=<a password you choose, at least 8 characters>
```

Leave them unset in production and provision accounts through `AccountService.register(...)`
instead. Never commit a password to `application.properties`.

**Creating professors' accounts in bulk.** The Accounts page provisions accounts from the
faculty records the workbook import already stored — there is no second upload. The Faculty
worksheet's optional `Email` column is what an account is made from; a malformed or repeated
address is reported with its worksheet, row and column at import time. `/users` previews every
imported record and why it is eligible, skipped or unusable, and the administrator confirms
before anything is inserted. Each new account is tied to its **faculty id** (never a name — an
id is stable across imports and two people may share a name), gets the `PROFESSOR` role, and
is created disabled.

**No password is ever generated.** A password derived from an id, a name or an email is
guessable by anyone holding the spreadsheet. Instead each account holds the BCrypt hash of a
256-bit single-use secret that expires (`edsa.accounts.activation-valid-hours`, a week by
default); the professor sets their own password at `/activate?token=…`, which is what enables
the account. Email delivery is not configured in this application, so the links are shown to
the administrator once, on screen, to pass on — they are never stored in readable form, never
logged, and never shown twice (which is why that one POST renders its result directly instead
of redirecting). Provisioning runs in one transaction, so a failure leaves no half-created
batch, and an account that already exists is skipped with its password, role and faculty
mapping untouched.

Authentication is kept clear of allocation: nothing in `edsa.core` knows accounts exist.
`edsa.auth` may use `edsa.data` (it reads the imported faculty records); nothing imports
`edsa.auth` except `edsa.web`, so the arrows still point one way:
`edsa.web → edsa.auth → edsa.data → edsa.core`.

## Exam modes

`ExamMode` holds the three kinds and how long one sitting lasts: **Quiz 10 minutes, Midsem 120,
Endsem 180**. The duration is the only difference between them — one scheduler serves all three.

**The mode is the authority on duration, not the workbook.** `ExamMode.minutesFor(override)` is
the single place that decides how long a sitting runs, and every other component asks it:
`Exam.durationUnder(mode)` delegates to it, `TimetableScheduler` times each slot from it, and
`ExamDurationConstraint` independently asks it again afterwards and compares. A paper may set
its own length under Midsem or Endsem, within `ExamMode.MAX_OVERRIDE_MINUTES` (300). **A Quiz
is always exactly 10 minutes**: a stale `Duration Minutes` value left in a workbook cannot
stretch one. An override the mode refuses is reported rather than silently applied —
`ExamData.ignoredDurationOverrides(mode)` lists them and the Data and Run page says which
papers declared a length that was not used.

Changing the mode drops the plan built under the previous one (`Workspace`), so a stale
timetable is never shown: the next compute re-times every sitting.

## Scheduling

`ExamPlanner` runs three steps and is the single entry point the web layer uses:

1. `TimetableScheduler` turns each declared `Exam` into an `ExamSlot` with real times. Papers
   are placed largest cohort first, at the earliest time that works, so sittings pack rather
   than leave gaps, and several run in parallel when students, rooms and invigilators allow.
   It reserves the rooms it checked, so the allocator cannot drift from what was verified.
2. `GreedyAllocator` seats each cohort across those rooms and gives every used room the
   least-loaded free invigilator. Conflicts are tracked incrementally, so cost does not grow
   with the number of rooms.
3. `PlanChecker` then re-checks the finished plan independently.

**Intervals are start-inclusive and end-exclusive.** A Midsem starting at 09:00 ends at 11:00;
nothing else may use that student, room or invigilator at 10:00, but a sitting may start at
11:00.

Anything that cannot be placed is reported with its reason (`PlanningResult.problems()`) and
the plan is not presented as a timetable — `isUsable()` is what the pages ask.

## Importing data

The primary workflow is one `.xlsx` workbook with six worksheets — Students, Faculty, Rooms,
Subjects, Enrollments, Exams — read by `ExcelWorkbookReader` (Apache POI). Every value is
treated as untrusted text: formulas are refused rather than evaluated, and a bad cell is
reported as `Worksheet 'Rooms', row 3, column 'Capacity': ...`. Parsing finishes before
anything is written and `WorkbookImportService` writes in one transaction, so a rejected
workbook leaves the database exactly as it was. `/import-template.xlsx` downloads a filled
example to start from.

The four-CSV upload and the bundled sample data still work, unchanged.

## Covering an absence

**Nothing is automatic, because the application does not decide that somebody is ill.** An
absence is *recorded* as a `LeaveRequest` with status `PENDING` and a configurable confirmation
deadline (`edsa.leave.confirm-within-hours`, 24 by default). A pending request changes no duty.
A `REJECTED` one changes no duty. A deadline going by changes no duty — `isOverdueForReview`
flags it for chasing, and it stays pending, because silence is not approval. Duties move at
exactly one moment: when an administrator confirms. Requests live in the `leave_request` table,
so one nobody has reviewed is still waiting after a restart.

Any signed-in account may record an absence — a professor reporting their own — but confirming
or rejecting is `hasRole('ADMIN')`. `LeaveWorkflow` sequences it; every rule it applies belongs
to `edsa.core` (`LeaveRequest.mayReassign()`, `AbsenceWindow.covers`, `AbsenceRepairer`).

`AbsenceWindow` is the absence as an interval, start-inclusive and end-exclusive like every
other interval here, so **only the duties the absence actually overlaps are touched**: somebody
away 09:00–12:00 loses the paper running 09:00–11:00 and keeps the one starting at 12:00. It
covers a few hours, a whole day, several days or the whole session.

Once confirmed, `AbsenceRepairer` offers each affected room to the least-loaded colleague who
is free at that time and not themselves away on confirmed leave. One absence therefore moves
one person per room and disturbs nobody else, and seating is never touched. No cascade is
attempted: when nobody is free for a room, that room is reported as uncovered rather than fixed
by shuffling three other people, because the coordinator needs to know. The repair is
all-or-nothing by construction — a whole new `DutyRoster` is built and the plan handed in is
never modified — so the caller either adopts the complete repaired roster or keeps the original,
and there is no state in which some duties moved and others silently did not. The repaired
roster replaces the current one and is re-checked, so the other pages and the downloads show
the plan as it now stands.

## Rules and scoring

**Hard constraints** decide whether a plan is allowed at all. Five are implemented, one per
failure the coordinator must act on, each reporting what broke and where:
`ExamDurationConstraint`, `RoomCapacityConstraint`, `RoomOverlapConstraint`,
`StudentConflictConstraint`, `FacultyConflictConstraint`. Still to come from the brief: no
invigilator on their own subject's paper, and no two students of the same paper seated
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
./mvnw clean test      compile and run every test, with a JaCoCo coverage report
                       written to target/site/jacoco/index.html
./mvnw spring-boot:run start the web application on http://localhost:8080
./mvnw clean package   build the executable jar into target/
```

On Windows use `.\mvnw.cmd` in place of `./mvnw`. The wrapper is script-only: it downloads
Maven itself on first run, so nothing has to be installed first.

Headless engine run:

```
java -cp target/classes edsa.CliRunner
java -cp target/classes edsa.CliRunner MIDSEM
java -cp target/classes edsa.CliRunner students.csv rooms.csv faculty.csv timetable.csv ENDSEM
```

With no arguments it plans the bundled sample files in `src/main/resources/sample`. An
optional last argument picks the mode; ENDSEM is the default.

That command drives the engine without the web layer. It is how the solver is tested and
demonstrated while the pages are still being built, and it works only as long as the one-way
rule above holds. The brief writes this entry point as `edsa.core.CliRunner`, but it loads
CSVs and so needs `edsa.data`; it lives in the root `edsa` package beside `EdsaApplication`,
as a composition root may depend on both layers while `edsa.core` stays clean.

CI runs `mvn clean test` on every push and pull request (`.github/workflows/ci.yml`).
