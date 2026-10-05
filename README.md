# HR Management System — Phases 1–4

A Spring Boot 3.5 / Java 21 HR backend.

- **Phase 1** — authentication, employees, departments and the **org hierarchy**: a
  self-referencing manager tree traversed with PostgreSQL recursive CTEs, cycle-free by
  construction, with access decided by role *and* position in the tree.
- **Phase 2** — **leave management**: types and per-year balances, a working-day
  calculator, holidays, the request state machine with overlap and balance rules, the
  approval authority matrix, a team calendar and an idempotent monthly accrual job.
- **Phase 3** — **attendance and payroll**: check-in/out with one open session at a time,
  a pure per-day calculator (lateness, overtime, absence, leave, missing check-out), HR
  corrections, and a `BigDecimal`-only payroll engine with insurance, progressive tax,
  proration, an idempotent monthly run, immutable payslip snapshots, payslip PDFs and
  Excel exports.
- **Phase 4** — **audit trail** (Hibernate Envers with actor + before/after field diffs),
  **management reports** (headcount, leave, payroll, attendance), Swagger polish, a
  multi-stage Dockerfile, Compose `app` profile, and GitHub Actions CI.

> **Money disclaimer.** Every payroll rate shipped in `application.yml` — the insurance
> percentage and band, the personal exemption and the tax brackets — is an **illustrative,
> simplified example**. It is not the law of any country and this project is **not tax or
> legal advice**. All of it is configuration, and every payslip stores the configuration it
> was computed with.

The React frontend is Phase 5 (see [PLAN.md](PLAN.md)).

## Run it

```bash
docker compose up -d --wait                                   # Postgres 16 on localhost:5437
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw clean verify  # build + tests (Testcontainers)
java -jar target/hr-management-system-0.0.1-SNAPSHOT.jar       # http://localhost:8091
```

### Run the API in Docker

```bash
# Postgres alone (default): already covered by `docker compose up -d --wait`
# API + Postgres:
cp .env.example .env.local   # set JWT_SECRET (and override HR_ADMIN_* outside local use)
docker compose --profile app up -d --build --wait
# or build the image alone:
docker build -t hr-management-system:local .
```

- Swagger UI: <http://localhost:8091/swagger-ui.html> (public)
- Health: <http://localhost:8091/actuator/health>
- Configuration: every value in `application.yml` is env-overridable — see [.env.example](.env.example).
  The image does **not** bake in `JWT_SECRET` / passwords; pass them at runtime.
- On first start an ADMIN is seeded from `HR_ADMIN_EMAIL` / `HR_ADMIN_PASSWORD`
  (defaults `admin@hr.local` / `Admin@12345`, **development only**) and only when no ADMIN exists.
  There is no public registration: HR/ADMIN create accounts.
- CI: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs `./mvnw -B verify` on Java 21.

## Endpoints so far

| Method | Path | Who |
| --- | --- | --- |
| POST | `/api/auth/login` | public |
| POST | `/api/auth/change-password` | any authenticated user |
| GET | `/api/departments` | any authenticated user |
| POST / PUT / DELETE | `/api/departments[/{id}]` | HR, ADMIN (delete → 409 while it has employees) |
| POST | `/api/employees` | HR, ADMIN — returns the temporary password **once** |
| GET | `/api/employees` | HR, ADMIN — `page`, `size` (≤100), `q`, `departmentId`, `status`, `sort` |
| GET | `/api/employees/me` | self, salary included |
| GET | `/api/employees/{id}` | self, HR, ADMIN, any ancestor manager — anybody else gets 404 |
| PUT / PATCH | `/api/employees/{id}` | HR, ADMIN |
| POST | `/api/employees/{id}/terminate` | HR, ADMIN — direct reports move to the terminated person's manager |
| PUT | `/api/employees/{id}/manager` | HR, ADMIN — 409 on a cycle or a terminated manager |
| GET | `/api/employees/me/team` | direct reports |
| GET | `/api/employees/me/team/all` | all descendants with their `depth` |
| GET | `/api/employees/{id}/chain` | management chain upwards to the root |
| GET | `/api/org-chart` | any authenticated user — nested company tree |

### Leave (Phase 2)

| Method | Path | Who |
| --- | --- | --- |
| GET | `/api/leave/types` | HR, ADMIN — seeded reference data, no CRUD |
| GET | `/api/leave/balances/me` | self — `year` (defaults to the current year) |
| GET | `/api/leave/balances/{employeeId}` | self, any ancestor manager, HR, ADMIN — anybody else 404 |
| POST | `/api/leave/requests` | any ACTIVE employee, for themself — `{type, startDate, endDate, reason?}` |
| GET | `/api/leave/requests/me` | self — paged, `status`, `year`, `page`, `size` (≤100) |
| GET | `/api/leave/requests/pending` | the caller's direct reports' PENDING; HR/ADMIN see all — paged |
| GET | `/api/leave/requests/{id}` | the requester, their ancestors, HR, ADMIN — anybody else 404 |
| POST | `/api/leave/requests/{id}/approve` | the direct manager, or HR/ADMIN (override) |
| POST | `/api/leave/requests/{id}/reject` | same — `{decisionNote}` is **required** |
| POST | `/api/leave/requests/{id}/cancel` | the requester, or HR/ADMIN on their behalf |
| GET | `/api/leave/calendar` | `from`, `to` — approved leave of the caller's team; HR/ADMIN: everyone |
| GET | `/api/leave/holidays` | any authenticated user — optional `from`, `to` |
| POST / PUT / DELETE | `/api/leave/holidays[/{id}]` | HR, ADMIN — duplicate date → 409 |
| POST | `/api/leave/accrual/run` | HR, ADMIN — `year`, `month`; idempotent, returns how many were credited |

### Attendance (Phase 3)

| Method | Path | Who |
| --- | --- | --- |
| POST | `/api/attendance/check-in` | any ACTIVE employee, for themself — 409 if a session is already open today |
| POST | `/api/attendance/check-out` | same — 409 when nothing is open today |
| GET | `/api/attendance/me` | self — `from`, `to`, range ≤ **92 days**; days + totals |
| GET | `/api/attendance/employees/{id}` | self, any ancestor manager, HR, ADMIN — anybody else 404; **never any pay** |
| GET | `/api/attendance/team/today` | the caller's whole subtree (HR/ADMIN: everyone): in / late / absent / on leave |
| POST | `/api/attendance/sessions/{id}/correct` | HR, ADMIN — `{checkIn, checkOut?, reason}`, reason **required** |
| POST | `/api/attendance/employees/{id}/sessions` | HR, ADMIN — add a missing session, same body |
| GET | `/api/attendance/export.xlsx` | HR, ADMIN — `year`, `month`; one row per employee-day |

### Payroll (Phase 3)

| Method | Path | Who |
| --- | --- | --- |
| POST | `/api/payroll/runs` | HR, ADMIN — `{year, month}`; **idempotent**, a second call is 409 |
| GET | `/api/payroll/runs` | HR, ADMIN — paged, newest month first |
| GET | `/api/payroll/runs/{id}` | HR, ADMIN — totals + every payslip |
| POST | `/api/payroll/runs/{id}/recalculate` | HR, ADMIN — DRAFT only, replaces the payslips; 409 once FINALIZED |
| POST | `/api/payroll/runs/{id}/finalize` | HR, ADMIN — locks the run forever |
| DELETE | `/api/payroll/runs/{id}` | HR, ADMIN — DRAFT only; 409 once FINALIZED |
| GET | `/api/payroll/runs/{id}/export.xlsx` | HR, ADMIN — register with a `SUM()` totals row |
| GET | `/api/payroll/payslips/me` | self — **FINALIZED runs only** |
| GET | `/api/payroll/payslips/{id}` | the employee themself once FINALIZED, HR/ADMIN always; a manager **404** |
| GET | `/api/payroll/payslips/{id}/pdf` | same access rules — OpenPDF payslip |

### Audit (Phase 4)

| Method | Path | Who |
| --- | --- | --- |
| GET | `/api/audit/employees/{id}` | HR, ADMIN only — paged field diffs (`salary`, `jobTitle`, `managerId`, `status`, `role`, `departmentId`); newest first. EMPLOYEE / manager / self → **404** |

### Reports (Phase 4)

| Method | Path | Who |
| --- | --- | --- |
| GET | `/api/reports/headcount` | HR, ADMIN — counts by department × status |
| GET | `/api/reports/headcount.xlsx` | HR, ADMIN — same as Excel |
| GET | `/api/reports/leave-summary?year=` | HR, ADMIN — used / pending / remaining by department × leave type |
| GET | `/api/reports/payroll-summary?year=` | HR, ADMIN — monthly totals from **FINALIZED** runs (`BigDecimal`) |
| GET | `/api/reports/attendance-summary?year=&month=` | HR, ADMIN — late / absent / overtime minutes per department |

## Rules worth knowing

- **Salary** is returned only to the employee themself, HR and ADMIN. A manager looking at
  a report gets a different DTO (`EmployeeView`) that has no `salary` field at all; team,
  chain and org-chart responses never contain one either.
- **Unknown-to-you employees answer 404, not 403**, so ids cannot be probed.
- **Only ADMIN** may grant or revoke the ADMIN role or modify an ADMIN account; nobody may
  change their own role.
- **Terminated accounts lose access immediately**: a terminated employee cannot log in (same
  generic message as a wrong password), and already-issued tokens are rejected by
  `ActiveEmployeeFilter`, which does one indexed primary-key status lookup per request.
- **Hierarchy traversal is SQL**: `EmployeeRepository` holds the recursive CTEs (descendants
  with depth, chain upwards, ancestor test, whole org chart in one query). Cycle prevention
  runs as a CTE inside the same transaction as the manager update.

Errors are always `ApiError` JSON: 400 with `fieldErrors` for validation, 401/403, 404 for
unknown paths and out-of-scope records, 405, 409 for conflicts.

## Leave rules

**Types** are seeded reference data (`leave_types`), not something anyone can create:

| Type | Paid | Needs a balance | Yearly allowance |
| --- | --- | --- | --- |
| `ANNUAL` | yes | yes | 21 days |
| `SICK` | yes | yes | 10 days |
| `UNPAID` | no | no | — |

**Days are whole integers.** Half days are not supported anywhere in the module.

**Weekend = Friday + Saturday** (Egypt), and it is configuration, not code:
`app.leave.weekend-days` (`LEAVE_WEEKEND_DAYS`) takes any comma-separated list of
`java.time.DayOfWeek`, e.g. `SATURDAY,SUNDAY`. `WorkingDayCalculator` is a pure class — no
database, no clock — that counts the working days of an inclusive range given the weekend
days and the holidays; a holiday falling on a weekend day is never subtracted twice. A
request whose range contains **0 working days** is rejected with 400.

**Balances** (`leave_balances`, one row per employee / type / year) are created lazily the
first time they are needed. `remaining` is **never stored**:

```
remaining = entitled + carriedOver - used - pending
```

`pending` holds the days reserved by PENDING requests, so an undecided request cannot be
spent twice. For a new joiner the entitlement of the **hire year** is pro-rated:

```
year < hire year  ->  0
year > hire year  ->  annualAllowanceDays
year = hire year  ->  round(annualAllowanceDays × (13 − hireMonth) / 12)
```

`13 − hireMonth` is the number of calendar months the employee is present in their hire
year, counting the hire month itself as a whole month; rounding is half-up. For ANNUAL
(21 days): January 21, April 16, July 11, October 5, December 2. The day of the month does
not matter.

**Monthly accrual** (`LeaveAccrualService`, `POST /api/leave/accrual/run`, plus a
`@Scheduled` cron job) credits ANNUAL entitlement month by month. Because 21/12 is not a
whole number, the credit for month *m* is the difference of the cumulative targets,
`round(allowance × m / 12) − round(allowance × (m−1) / 12)` → 2,2,1,2,2,2,1,2,2,2,1,2,
which adds up to exactly 21 over a year with no drift. The run is **idempotent per
(employee, type, year, month)**: the `accrual_log` unique key is claimed with an
`INSERT … ON CONFLICT DO NOTHING` and the entitlement is raised only if that insert
created the row, so a re-run or a double fire of the scheduler adds nothing twice.
Only ACTIVE employees hired on or before the end of that month are credited.

**Upfront granting and monthly accrual are alternatives, and that is enforced** — one or
the other owns the ANNUAL entitlement, never both:

| `annual.upfront` | ANNUAL entitlement | Accrual run (endpoint, scheduler, service) |
| --- | --- | --- |
| `true` (default) | the whole pro-rated year at balance creation | refused with **409** before any row is read or written: *"Monthly accrual is disabled while app.leave.annual.upfront=true; ANNUAL is granted upfront"* |
| `false` | starts at 0, built up month by month | credits `monthlyCredit(allowance, month)`, reaching exactly the yearly allowance after twelve runs |

`runFor(year, month)` is the single entry point of every caller, so the guard cannot be
bypassed. The misconfiguration `accrual.enabled=true` **with** `annual.upfront=true` is
refused at **startup**: the scheduler's constructor throws `IllegalStateException`, so the
application does not come up instead of silently double-granting on the first fire.

Configuration (`app.leave.*`):

```yaml
weekend-days: FRIDAY,SATURDAY   # LEAVE_WEEKEND_DAYS
annual.upfront: true            # grant the (pro-rated) year upfront (accrual then refuses) …
accrual.enabled: false          # … or accrue monthly: set upfront=false AND enabled=true
accrual.cron: "0 30 1 1 * *"    # 01:30 on the 1st of every month
accrual.zone: UTC
```

The accrual job only exists as a bean when `accrual.enabled` is true, so nothing is
scheduled in the default configuration (and nothing fires during tests).

**Filing a request** (`POST /api/leave/requests`, always for yourself) is refused when:

| Rule | Status |
| --- | --- |
| `endDate` before `startDate` | 400 |
| start in a past year | 400 |
| start before the employee's hire date | 400 |
| start more than one year ahead | 400 |
| the range spans two calendar years | 400 |
| the range has no working day (weekend/holidays only) | 400 |
| `reason` longer than 500 characters | 400 |
| overlaps one of your own PENDING or APPROVED requests | 409 |
| not enough remaining balance (types that need one) | 409 |

Overlap boundaries are inclusive, so **adjacent ranges are fine** (one ends on the 5th, the
next starts on the 6th) while a single shared day is not; REJECTED and CANCELLED requests
free their days again. UNPAID needs no balance.

**State machine** (`LeaveStatus`, one explicit transition table, 409 on anything else):

```
PENDING  -> APPROVED | REJECTED | CANCELLED
APPROVED -> CANCELLED        (only while the leave has not started)
REJECTED, CANCELLED          terminal
```

**Who decides**: the requester's **direct** manager; HR and ADMIN may always decide as an
override, and are the only ones who can decide for an employee with no manager. A higher
ancestor who is *not* the direct manager gets **403**; somebody outside the requester's
chain gets **404**, so request ids cannot be probed. Deciding **your own** request is
**403** for everybody, HR and ADMIN included — the employee cancels instead. A rejection
must carry a `decisionNote` (≤ 500 chars); `decidedBy` and `decidedAt` are recorded.

**Cancelling** is for the requester (or HR/ADMIN on their behalf): always while PENDING,
and for an APPROVED leave only while `startDate` is still in the future. A manager rejects
rather than cancels (403).

**Balance accounting** — after any sequence of transitions the row is back to the exact
numbers it started from:

```
file     : pending += days
approve  : pending -= days, used += days
reject   : pending -= days
cancel   : PENDING  -> pending -= days
           APPROVED -> used    -= days
```

**Concurrency**: every balance-changing operation first takes a
`SELECT … FOR UPDATE` on *all* balance rows of that employee for that year (in leave-type
order, so transactions cannot deadlock), and only then reads the remaining days, checks the
overlap and reserves. Requests for one employee therefore serialise: with a balance for K
days, exactly K of N simultaneous requests succeed and the rest get 409 — there is a test
that runs this with real threads. State changes additionally lock the `leave_requests` row
before examining its status, so two simultaneous approvals cannot both consume the same
reservation. Both tables also carry an optimistic `version` as a backstop.

**Termination** extends the existing transaction through an `EmployeeTerminationListener`:
every PENDING request of the terminated employee becomes CANCELLED and its reserved days
are refunded. APPROVED leave is left untouched — it is the record of days actually taken.

**The team calendar** (`GET /api/leave/calendar`) returns only APPROVED leave of the
caller's own subtree (HR/ADMIN: the whole company), resolved by the same recursive CTE as
the org chart. Its projection has **no `reason` column at all**, so why somebody is off can
never leak through it.

## Attendance rules

A **session** is one continuous presence interval: `check_in` always set, `check_out` null
while the employee is still in. Several closed sessions a day are normal (lunch, errands).
Nothing derived is stored — `DailyAttendanceCalculator` recomputes first-in, last-out,
worked minutes, lateness, overtime and the status every time, so an HR correction is
reflected immediately and no row can go stale.

**One open session at a time, per work day.** A second check-in on the same day is **409**;
a check-out with nothing open today is **409**. A session left open on an *earlier* day does
**not** block today's check-in, and today's check-out cannot close it either — that would
invent hours. It is surfaced as `MISSING_CHECKOUT` on its own day and HR corrects it.

**The work day** is the check-in instant rendered in `app.attendance.zone` (default
`Africa/Cairo`) and is stored on the row, so day queries and exports never depend on the
server's default zone. Instants themselves are stored in UTC.

**Per-day derivation** (all of it pure and unit-tested):

| Figure | Rule |
| --- | --- |
| `workedMinutes` | sum of the **closed** sessions; an open one contributes nothing |
| `lateMinutes` | `max(0, firstIn − (workStart + grace))`, **only on a WORKING day**. Arriving *exactly* on the boundary (09:15 by default) is **not** late. Reported only — **lateness is never deducted from pay** |
| `overtimeMinutes` | on a WORKING day: the minutes of closed sessions **after `workEnd`**. On a WEEKEND or HOLIDAY: **all** worked minutes. Both capped at `max-overtime-minutes-per-day` |
| `firstIn` / `lastOut` | earliest check-in / latest check-out of the day |

**Status**, in precedence order:

| Status | When |
| --- | --- |
| `MISSING_CHECKOUT` | a session still open ≥ `auto-close-after-hours` after its check-in. The day yields **no** minutes and **no** overtime — an unclosed session is never guessed into pay |
| `WEEKEND` / `HOLIDAY` | the calendar says so, worked or not |
| `LATE` | a working day with at least one session and `lateMinutes > 0` |
| `PRESENT` | a working day with at least one session and no lateness — including a session that is still open *today* (the employee is simply in) |
| `ON_LEAVE` | a working day, **no** session, covered by an APPROVED leave request (paid vs UNPAID stays distinguishable in `leave`) |
| `ABSENT` | a working day, no session, no approved leave |

**Weekend and holiday work is recorded, not refused.** Checking in on a Friday is allowed;
the day keeps the status `WEEKEND` (or `HOLIDAY`) and **its worked minutes count entirely
as overtime, paid at the holiday multiplier** (×2.0 by default) rather than the weekday
overtime one (×1.5). That is the deliberate simplification: such work is voluntary by
definition, so there is no "normal" portion of it.

**A day that is both on leave and worked counts as worked** (`PRESENT`/`LATE`). The leave
coverage is still reported, but payroll only deducts an UNPAID day when nobody turned up.

**Two clamps apply to every range**: a day **after today** is not emitted at all (nothing is
known about it yet, and calling a future working day `ABSENT` would be a lie payroll would
then deduct), and days outside the employment window (before the hire date, after the
termination date) are not emitted either.

**HR corrections** (`correct` an existing session, or add a missing one) always carry a
**mandatory reason**, stamp `corrected_by` and flip `source` to `HR_CORRECTION`. Refused:

| Rule | Status |
| --- | --- |
| `checkOut` at or before `checkIn` | 400 |
| `checkIn` or `checkOut` in the future | 400 |
| a session longer than 24 hours (exactly 24 h is fine) | 400 |
| a session starting before the employee's hire date | 400 |
| overlap with another session **of the same employee** | 409 |
| anybody who is not HR/ADMIN | 403 |

Touching endpoints do **not** overlap (leaving at 13:00 and returning at 13:00 is legal),
a correction ignores the row it is editing but not the others, and two employees may of
course share an interval.

Configuration (`app.attendance.*`), all env-overridable:

| Key | Default | Meaning |
| --- | --- | --- |
| `zone` | `Africa/Cairo` | the zone instants are rendered in to get a work day and a wall-clock time |
| `work-start` | `09:00` | nominal start of the working day |
| `work-end` | `17:00` | nominal end; minutes after it are overtime |
| `grace-minutes` | `15` | minutes after `work-start` that are still on time (boundary inclusive) |
| `max-overtime-minutes-per-day` | `240` | hard ceiling on the overtime credited for one day |
| `auto-close-after-hours` | `16` | a session still open this long is abandoned → `MISSING_CHECKOUT` |

The weekend and the holidays are **not** duplicated here: attendance reuses
`app.leave.weekend-days` and the `holidays` table through `WorkCalendar`, so there is
exactly one definition of "working day" in the application.

## Payroll rules

> **Illustrative, simplified, and not tax or legal advice.** The numbers below are a
> plausible-looking example of a progressive monthly tax plus a capped social-insurance
> contribution. Replace every one of them for a real jurisdiction.

**All money is `BigDecimal`, scale 2, `RoundingMode.HALF_UP`, in `numeric(12,2)` columns.**
There is no `double` or `float` in a field, an intermediate or a DTO anywhere in the module;
the single rounding entry point is `Money`. `Employee.salary` is the **monthly gross base**.

`PayrollCalculator` is pure — an immutable `PayrollInput` in, a `PayrollResult` out, no
database, no clock — and every intermediate is exposed on the payslip so the net can be
re-added by hand:

```
dailyRate     = base / workingDaysInMonth                       (rounded to 2 dp first)
hourlyRate    = dailyRate / workingHoursPerDay                  (rounded to 2 dp first)
proratedBase  = base                              if payableWorkingDays == workingDaysInMonth
              = round(base × payableDays / workingDaysInMonth)  otherwise
overtimePay   = overtimeHours × hourlyRate × overtimeMultiplier
holidayPay    = holidayHours  × hourlyRate × holidayOvertimeMultiplier
grossEarnings = proratedBase + overtimePay + holidayPay
                − unpaidLeaveDays × dailyRate − absentDays × dailyRate
insurance     = clamp(base, minInsurable, maxInsurable) × employeeRate
taxableIncome = max(0, grossEarnings − insurance − personalExemptionMonthly)
tax           = Σ marginal bracket charges, each rounded HALF_UP to 2 dp
netPay        = max(0, grossEarnings − insurance − tax)
```

Deliberate decisions, each one tested:

- **A full month pays exactly the base.** `dailyRate × workingDays` drifts (454.55 × 22 =
  10 000.10 for a 10 000 base over 22 days), so proration is only applied to a partial
  month; a whole month is paid as the base itself.
- **Deductions use the rounded `dailyRate`**, because that rate is printed on the payslip
  and the employee must be able to reproduce the line from the printed numbers.
- **Lateness costs nothing.** It is reported (`lateMinutes`) and never deducted.
- **Insurance is charged on the contractual monthly base**, clamped into the insurable
  band — not on overtime, and not pro-rated for a partial month. A base of 0 pays 0.
- **Each tax bracket's charge is rounded on its own** and the rounded charges are summed,
  so the breakdown printed on the payslip adds up to the tax line exactly. Income exactly
  on a ceiling is fully taxed by that bracket and nothing spills into the next.
- **Floors.** The taxable income floors at 0, and so does the net: if deductions exceed
  earnings the net is `0.00`, `netFloored` is `true` and a `warning` appears on both the
  JSON and the PDF rather than showing the employee a debt.
- **Overtime minutes become hours at scale 2** (90 min → 1.50 h, 100 min → 1.67 h).

**Worked example** (the reference month used by the test suite — March 2025 has 22 working
days with the Friday+Saturday weekend and no holidays):

```
base                   8 800.00      dailyRate  8 800 / 22 = 400.00
                                     hourlyRate   400 / 8  =  50.00
overtime      180 min = 3.00 h × 50.00 × 1.5 =    225.00
holiday work  240 min = 4.00 h × 50.00 × 2.0 =    400.00
unpaid leave  1 day   × 400.00               =  − 400.00
absence       1 day   × 400.00               =  − 400.00
                                       gross  =  8 625.00
insurance     clamp(8 800, 2 000, 12 600) × 0.11 = 968.00
taxable       8 625.00 − 968.00 − 1 250.00   =  6 407.00
tax           0 + 150.00 + 300.00 + 1 407.00 × 0.20 = 731.40
net           8 625.00 − 968.00 − 731.40     =  6 925.60
late          2 × 30 min = 60 min  (reported, not deducted)
```

### The run model

`POST /api/payroll/runs {year, month}` creates a **DRAFT** run plus one immutable payslip
per eligible employee **in a single transaction**.

**Eligible** = hired on or before the end of the month, **with** a base salary, and either
still ACTIVE *or* terminated **inside** that month (so a final, pro-rated month is still
paid). An employee with no `salary` is not payable and gets no payslip at all; somebody
terminated in an earlier month — or with no `terminated_at` recorded — is not in the run.

**Idempotency is enforced by the `(run_year, run_month)` unique constraint**, not by the
`exists` pre-check. The run row is inserted and flushed *first*, so two concurrent POSTs
serialise on it and the loser's whole transaction — run *and* payslips — is rolled back
with a **409**. There is a test that runs this with real threads: six simultaneous calls
leave exactly one run and one set of payslips.

**Months in the future are refused** (400). The current month is allowed; because a day
after today is never derived, such a run pays the full base and counts only the attendance
recorded so far. Re-run it with `recalculate` or delete and recreate it once the month ends.

```
DRAFT      -> recalculate (replaces the payslips), finalize, delete
FINALIZED  -> nothing: recalculate and delete answer 409
```

**A FINALIZED run is frozen.** Attendance corrections, salary changes and edits to
`app.payroll.*` afterwards cannot move a single figure, because the payslip is **read, never
recomputed**: the identity fields, the inputs, the derived rates and every intermediate are
stored as columns, and the whole configuration in force plus the bracket-by-bracket tax
breakdown are kept in a `jsonb` snapshot that travels with the payslip.

**Payslip privacy**:

| Who | DRAFT | FINALIZED |
| --- | --- | --- |
| HR, ADMIN | visible | visible |
| the employee themself | **404** | visible (`/payslips/me` lists these) |
| their manager, any ancestor | **404** | **404** — a manager never sees pay |
| any other employee | **404** | **404** |

Everything refused is 404, never 403, so a payslip id cannot be probed.

### Documents

- **`GET /payslips/{id}/pdf`** — OpenPDF. Company header from configuration, the employee,
  the period, then every line item including the tax bracket breakdown and the disclaimer.
  The tests parse it back with **PDFBox** (a different library from the writer) and assert
  the net amount text is in it.
- **`GET /runs/{id}/export.xlsx`** and **`GET /api/attendance/export.xlsx`** — Apache POI.
  The payroll register has a bold header, one row per payslip and a totals row built from
  real `SUM()` **formulas**; the attendance sheet has one row per employee-day. Both are
  read back with POI in the tests, formulas included.

### Configuration (`app.payroll.*`)

| Key | Default | Meaning |
| --- | --- | --- |
| `working-hours-per-day` | `8` | divides the daily rate into an hourly rate |
| `overtime-multiplier` | `1.5` | weekday overtime |
| `holiday-overtime-multiplier` | `2.0` | weekend and public-holiday work |
| `insurance.employee-rate` | `0.11` | employee share of social insurance |
| `insurance.min-insurable` | `2000.00` | the base is clamped **up** to this before the rate |
| `insurance.max-insurable` | `12600.00` | …and **down** to this |
| `tax.personal-exemption-monthly` | `1250.00` | subtracted before the brackets; taxable floors at 0 |
| `tax.brackets` | see below | marginal monthly brackets; the last has no `up-to` |
| `company.name` / `company.address` | `Example Holding`, Cairo | printed on the payslip PDF |

| Monthly band | Rate |
| --- | --- |
| 0 – 1 500 | 0 % |
| 1 500 – 3 000 | 10 % |
| 3 000 – 5 000 | 15 % |
| 5 000 – 8 000 | 20 % |
| 8 000 – 12 000 | 22.5 % |
| above 12 000 | 25 % |

`PayrollRates` validates itself at startup — a negative rate, a non-increasing bracket
table or a missing open-ended top bracket stops the application instead of producing wrong
money later. **Again: these figures are illustrative and are not tax or legal advice.**

### Termination

The existing terminate flow now also writes `employees.terminated_at` from the injected
`java.time.Clock` (so tests control it), nullable for every ACTIVE employee and for rows
that predate the V4 migration. Payroll uses it to pro-rate the final month: the payable
working days are those between the month start (or the hire date) and the termination date,
and days after leaving are not counted as absences.

## Known limitations

- **Audit scope:** the trail covers *employee* data changes (who changed what, before/after, via Hibernate Envers).
  There is no separate business-event log (e.g. "payroll run finalized", "export downloaded"), and no free-text
  "reason" is stored with a change.
- **Payroll rates are illustrative** (insurance and tax brackets are configurable examples, not legal or tax advice).
- Leave is whole days only (no half days); attendance check-in/out is by API, there is no device integration.
- The default `JWT_SECRET`, `admin@hr.local` / `Admin@12345` are for local development only: change them for any
  real deployment.
- React frontend: coming next.
