# EDSA — Exam Duty & Seating Allocation

Turns the examination office's spreadsheet into a seating plan and an invigilation duty
roster, and keeps both correct when somebody calls in sick.

Upload one workbook, pick Quiz, Midsem or Endsem, and the papers are timetabled, students
seated and invigilators assigned. Every rule is then re-checked independently, and anything
that could not be placed is reported with its reason rather than quietly dropped.

## Running it

You need a **JDK 17 or newer** and **Git**. You do not need Maven — the `mvnw` wrapper
downloads it on first use.

```bash
git clone <this repository>
cd edsa

# The administrator account. There is no default password; pick your own, 8+ characters.
export EDSA_SEEDADMIN_ENABLED=true
export EDSA_SEEDADMIN_EMAIL=you@example.edu
export EDSA_SEEDADMIN_PASSWORD=<a password you choose>

./mvnw spring-boot:run
```

On Windows PowerShell use `.\mvnw.cmd` and `$env:EDSA_SEEDADMIN_ENABLED="true"` and so on, in
the same window as the run command.

Then open <http://localhost:8080> and sign in with that email and password. Look for this line
in the console — without it there is no account and nothing will let you in:

```
Created the administrator account for you@example.edu
```

Set the same three variables on a later start with a **different** password and it is reset,
which is how to get back in if you forget it. Leave them unset in a real deployment: anybody
who can set them could take the administrator account over. Provision everyone else through
the Accounts page.

### Getting data in

- **Quickest:** *Data and Run* → **Load sample data** (60 students, 4 rooms, 8 faculty,
  6 papers).
- **Ready-made workbooks:** `docs/EDSA_Sample_Data_Seniority.xlsx` (180 students, 20 staff,
  10 rooms, 6 papers); `docs/EDSA_Sample_Data_Engineering.xlsx` (144 students, 24 staff,
  7 rooms, 9 papers, with a "Try this" sheet of seven absences and the Points they produce);
  and `docs/EDSA_Disruption_Test_Data.xlsx`, whose "Disruption tests" sheet lists nine steps
  (record, reject, confirm absences) with the expected result of each.
- **Your own:** *Data and Run* → **Download template**, replace the example rows, then choose
  the file. It is checked the moment it is chosen; **Generate schedule** is enabled only when
  the check passes, and otherwise every problem is listed with its worksheet, row and column.

The workbook has six worksheets. Required columns:

| Worksheet | Required columns |
| --- | --- |
| Students | Student ID, Name, Department |
| Faculty | Faculty ID, Name, Department, **Post** (Professor, Associate Professor or Assistant Professor) |
| Rooms | Room ID, Building, Capacity |
| Subjects | Subject Code, Subject Name, Department |
| Enrollments | Student ID, Subject Code |
| Exams | Exam ID, Subject Code, Date, **Start Time** |

Optional columns (Email, Year, Section, room Name, Semester) are format-checked when filled in.
Fill in the Faculty `Email` column — that is what the Accounts page creates sign-in accounts
from. The four-CSV upload still works, behind **Upload CSV files**.

## The pages

| Page | What it is for |
| --- | --- |
| **Data and Run** (`/`) | Import the workbook, choose the mode, see every rule pass or fail |
| **Room schedule** (`/rooms`) | The sittings, the seat-by-seat grid, and a search for an invigilator by name |
| **Workloads** (`/workloads`) | Duties per invigilator |
| **Disruption** (`/disruption`) | Record an absence, confirm or reject it, see what moved |
| **Points** (`/points`) | Every teacher's duties, covers, misses and points, with the history. Administrators only |
| **Accounts** (`/users`) | Create accounts one at a time or in bulk from the imported faculty. Administrators only |

Downloads: `/exam-plan.xlsx` (Timetable, Seating Matrix, Seating and Duties sheets),
`/seating-plan.csv`, `/import-template.xlsx`. The sun/moon button in the header switches dark
mode; the choice is remembered per browser.

## The rules every plan must pass

Eight hard rules, each re-checked on the finished plan and listed on Data and Run as pass or
fail with what broke and where:

| Rule | Meaning |
| --- | --- |
| Exam duration | Every sitting lasts the length its mode gives it |
| Room capacity | No room seats more students than it holds |
| Room overlap | No two sittings share a room, including a 30-minute turnaround between them |
| Student conflict | No student sits two papers at once |
| Faculty conflict | No invigilator is in two rooms at once |
| Invigilators per room | Every room in use has exactly two invigilators |
| Senior in every room | At least one of them is a Professor (Associate and Assistant Professors are junior) |
| Department conflict | Nobody invigilates a paper of their own department |

The scheduler also picks the smallest free room that fits a cohort, and seats students in a
checkerboard (every other seat) whenever the room is big enough, packing row by row only when
it is not.

## Who can do what

Importing data, choosing the mode and recomputing the plan belong to the examination office
(the `ADMIN` role), because each of them replaces a session everybody else is reading. A
professor reads every page, downloads every export, searches for their own duties, and reports
their own absence — the account decides whose absence that is, so the form cannot be edited to
file leave for a colleague. Confirming or rejecting leave is the office's.

## Sick leave

Recording an absence changes nothing on its own. It is stored as pending and waits for an
administrator to confirm or reject it, and **only a confirmation moves a duty**. A review
deadline passing does not approve anything — it marks the request overdue, because silence is
not approval. Once confirmed, each affected room is offered to the least-loaded colleague who
is free at that time and not from the paper's department; if the absent teacher was the room's
only Professor, only another Professor may take it. A room nobody can take is reported rather
than quietly left empty.

**Points.** A confirmed absence costs the absent teacher **−1 for each duty it made them miss**
(even one nobody could cover), and the colleague who takes each duty earns **+1**. Pending and
rejected absences earn nothing. Points are kept in the database and survive restarts and
re-imports.

Absences are measured as intervals, so leave from 09:00 to 12:00 takes the paper running
09:00–11:00 and leaves one starting at 12:00 alone.

## Exam durations

Quiz is **10 minutes**, Midsem **120**, Endsem **180**. The selected mode decides, not the
spreadsheet: a `Duration Minutes` value left in a workbook cannot stretch a Quiz, and one the
mode will not accept is reported on screen rather than silently applied. Midsem and Endsem
papers may set their own length, up to 300 minutes.

## Where the data lives

H2, in a file under `./data`, so accounts, imported records and recorded leave survive a
restart. The directory is not in version control — it holds real records. Delete it to start
from nothing.

Every query is plain JDBC, so moving to MySQL later is the four datasource lines in
`application.properties` plus a driver dependency, with no Java changes. The schema in
`src/main/resources/db/schema.sql` is applied on start-up and is written to reapply harmlessly
over an existing database.

The *loaded* session — the plan on screen — is held in memory and is not restored on start-up.
After a restart, press **Use imported data** on Data and Run to plan the stored records again.

## Build and test

```bash
./mvnw clean test                  # every test, plus a JaCoCo report in target/site/jacoco
./mvnw spring-boot:run             # http://localhost:8080
./mvnw clean package               # an executable jar in target/
java -jar target/edsa-*.jar        # run that jar
```

Headless, without the web layer:

```bash
java -cp target/classes edsa.CliRunner            # the bundled sample, as Endsem
java -cp target/classes edsa.CliRunner MIDSEM
```

## Architecture

Three packages, and the arrows point one way: `edsa.web` → `edsa.data` → `edsa.core`, with
`edsa.auth` beside them for accounts. `edsa.core` holds every decision and imports nothing
upward and no Spring, which is why the planner can be tested without opening a browser.
Controllers decide nothing — they bind a request, ask the core, and pick a view.

See [`edsaa.md`](edsaa.md) for the full design, the rules and how to add a constraint.
