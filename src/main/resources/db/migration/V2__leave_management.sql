-- Phase 2 schema: leave types, per-year balances, requests, public holidays and the
-- monthly-accrual log. Days are always whole integers — half days are not supported.

create table leave_types (
    code                  varchar(20) not null primary key,
    paid                  boolean     not null,
    requires_balance      boolean     not null,
    annual_allowance_days integer     not null,

    constraint leave_types_code_check      check (code in ('ANNUAL', 'SICK', 'UNPAID')),
    constraint leave_types_allowance_check check (annual_allowance_days >= 0)
);

-- Seeded reference data: there is no CRUD for leave types on purpose.
insert into leave_types (code, paid, requires_balance, annual_allowance_days) values
    ('ANNUAL', true,  true,  21),
    ('SICK',   true,  true,  10),
    ('UNPAID', false, false, 0);

create table holidays (
    id           uuid         not null primary key,
    holiday_date date         not null unique,
    name         varchar(120) not null,
    created_at   timestamptz  not null default now()
);

create index holidays_date_idx on holidays (holiday_date);

-- One row per (employee, leave type, year). `remaining` is never stored: it is always
-- computed as entitled + carried_over - used - pending.
create table leave_balances (
    id                uuid        not null primary key,
    employee_id       uuid        not null,
    leave_type        varchar(20) not null,
    balance_year      integer     not null,
    entitled_days     integer     not null default 0,
    carried_over_days integer     not null default 0,
    used_days         integer     not null default 0,
    pending_days      integer     not null default 0,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    version           bigint      not null default 0,

    constraint leave_balances_uk          unique (employee_id, leave_type, balance_year),
    constraint leave_balances_employee_fk foreign key (employee_id) references employees (id) on delete cascade,
    constraint leave_balances_type_fk     foreign key (leave_type) references leave_types (code),
    constraint leave_balances_year_check  check (balance_year between 2000 and 2100),
    constraint leave_balances_signs_check check (entitled_days >= 0 and carried_over_days >= 0
                                                 and used_days >= 0 and pending_days >= 0)
);

create table leave_requests (
    id            uuid        not null primary key,
    employee_id   uuid        not null,
    leave_type    varchar(20) not null,
    start_date    date        not null,
    end_date      date        not null,
    leave_year    integer     not null,
    working_days  integer     not null,
    reason        varchar(500),
    status        varchar(20) not null,
    decided_by    uuid,
    decided_at    timestamptz,
    decision_note varchar(500),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    version       bigint      not null default 0,

    constraint leave_requests_employee_fk foreign key (employee_id) references employees (id) on delete cascade,
    constraint leave_requests_decider_fk  foreign key (decided_by) references employees (id),
    constraint leave_requests_type_fk     foreign key (leave_type) references leave_types (code),
    constraint leave_requests_status_check check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    constraint leave_requests_range_check  check (end_date >= start_date),
    constraint leave_requests_days_check   check (working_days > 0)
);

create index leave_requests_employee_status_idx on leave_requests (employee_id, status);
create index leave_requests_status_idx on leave_requests (status);
create index leave_requests_dates_idx on leave_requests (start_date, end_date);

-- Makes the monthly accrual idempotent: the unique key is the guard, so a re-run or a
-- double fire of the scheduler can never credit the same month twice.
create table accrual_log (
    id            uuid        not null primary key,
    employee_id   uuid        not null,
    leave_type    varchar(20) not null,
    accrual_year  integer     not null,
    accrual_month integer     not null,
    days          integer     not null,
    created_at    timestamptz not null default now(),

    constraint accrual_log_uk          unique (employee_id, leave_type, accrual_year, accrual_month),
    constraint accrual_log_employee_fk foreign key (employee_id) references employees (id) on delete cascade,
    constraint accrual_log_type_fk     foreign key (leave_type) references leave_types (code),
    constraint accrual_log_month_check check (accrual_month between 1 and 12),
    constraint accrual_log_days_check  check (days >= 0)
);
