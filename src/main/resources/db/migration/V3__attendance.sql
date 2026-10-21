-- Phase 3a schema: attendance sessions.
--
-- A session is one continuous presence interval: `check_in` always set, `check_out` null
-- while the employee is still in. Several sessions per day are allowed (lunch, errands),
-- and the derived per-day figures (first in, last out, worked minutes, lateness, overtime,
-- status) are never stored — they are recomputed by DailyAttendanceCalculator so that a
-- later correction cannot leave stale numbers behind.
--
-- There is deliberately NO partial unique index on the open sessions: "one open session at
-- a time" is enforced per *work day* by the service, because a stale open session left
-- over from a previous day must be reportable as MISSING_CHECKOUT without blocking today's
-- check-in.

create table attendance_sessions (
    id                uuid        not null primary key,
    employee_id       uuid        not null,
    check_in          timestamptz not null,
    check_out         timestamptz,
    -- The work day the session is accounted to: check_in rendered in app.attendance.zone.
    -- Stored (not derived in SQL) so queries and exports stay zone-stable and indexable.
    work_date         date        not null,
    source            varchar(20) not null,
    corrected_by      uuid,
    correction_reason varchar(500),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    version           bigint      not null default 0,

    constraint attendance_sessions_employee_fk foreign key (employee_id) references employees (id)
        on delete cascade,
    constraint attendance_sessions_corrector_fk foreign key (corrected_by) references employees (id),
    constraint attendance_sessions_source_check check (source in ('SELF', 'HR_CORRECTION')),
    constraint attendance_sessions_order_check  check (check_out is null or check_out > check_in),
    -- A correction must always say why; a self check-in never carries a reason.
    constraint attendance_sessions_correction_check check (
        (source = 'SELF'           and corrected_by is null and correction_reason is null)
     or (source = 'HR_CORRECTION'  and corrected_by is not null and correction_reason is not null))
);

create index attendance_sessions_employee_date_idx on attendance_sessions (employee_id, work_date);
create index attendance_sessions_date_idx on attendance_sessions (work_date);
-- Finding the employee's open session is a hot path (check-out, check-in guard).
create index attendance_sessions_open_idx on attendance_sessions (employee_id) where check_out is null;
