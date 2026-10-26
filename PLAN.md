# HR Management System — Plan

A modular-monolith HR backend (React frontend last). The value is in the **business rules**, not CRUD:
hierarchy-aware access, leave workflow with balances, attendance, payroll maths, audit trail, exports.

## Stack
Java 21, Spring Boot 3.5.16, PostgreSQL 16, Flyway, Spring Security (JWT HS256), Spring Data JPA (+ native SQL for
recursive CTEs), Hibernate Envers (audit), springdoc, OpenPDF (payslip PDF), Apache POI (Excel), Testcontainers.
Frontend (last phase): React 18 + TypeScript + Vite. Docker Compose, GitHub Actions.

## Domain rules (the interesting part)
- **Org hierarchy**: employee.manager_id (self FK). A manager sees their whole team (direct + indirect) via a
  recursive CTE. Setting a manager must never create a cycle (checked with the CTE, in the same transaction).
- **Roles**: ADMIN, HR, EMPLOYEE. "Manager" is NOT a role: it is derived (has reports). Access = role + position in tree.
  Salary is visible only to the employee themself, HR and ADMIN — never to a manager.
- **Leave**: types (annual, sick, unpaid), per-year balances, request state machine
  PENDING -> APPROVED | REJECTED | CANCELLED, approved by the requester's manager (or HR if none), no overlapping
  requests, no exceeding the balance, working days exclude weekends/holidays, monthly accrual by a scheduled job.
- **Attendance**: check-in/out, lateness, overtime, one open session at a time.
- **Payroll**: BigDecimal, tax brackets + insurance, unpaid-leave and overtime adjustments, monthly run is idempotent,
  payslip as PDF, payroll/attendance exports as Excel.
- **Audit**: who changed salary / position / manager / status, when, before and after.

## Phases
1. Setup + auth + employees/departments + org hierarchy (recursive CTE), cycle prevention, RBAC by tree position. ✅
2. Leave management (types, balances, request workflow, overlap + balance rules, holidays, monthly accrual job). ✅
3. Attendance + payroll (calculation, idempotent monthly run, payslip PDF, Excel exports). ✅
4. Audit trail (Envers) + reports + backend polish (Swagger, Dockerfile, compose, README, CI, GitHub repo). ✅
5. React frontend (employee / manager / HR views), Docker compose of everything, screenshots, CV.

## Ports (avoid ones already used by sibling projects)
Backend 8091, Postgres 5437, frontend 5175.
