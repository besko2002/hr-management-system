-- Phase 3b schema: monthly payroll runs and immutable payslip snapshots.
--
-- Money is numeric(12,2) everywhere — never a floating-point type — and the application
-- only ever puts BigDecimal(scale = 2, RoundingMode.HALF_UP) into these columns.

-- The termination *date*, which payroll needs to pro-rate a final month. Nullable: rows
-- that existed before this migration (and every ACTIVE employee) simply have none.
alter table employees add column terminated_at date;

create table payroll_runs (
    id           uuid        not null primary key,
    run_year     integer     not null,
    run_month    integer     not null,
    status       varchar(20) not null,
    created_by   uuid,
    created_at   timestamptz not null default now(),
    finalized_by uuid,
    finalized_at timestamptz,
    version      bigint      not null default 0,

    -- THE idempotency guard. Two concurrent POSTs for the same month both try to insert
    -- this key; the loser gets a unique violation and its whole transaction (run plus
    -- every payslip) is rolled back, so exactly one run can ever exist per month.
    constraint payroll_runs_period_uk    unique (run_year, run_month),
    constraint payroll_runs_creator_fk   foreign key (created_by) references employees (id),
    constraint payroll_runs_finalizer_fk foreign key (finalized_by) references employees (id),
    constraint payroll_runs_status_check check (status in ('DRAFT', 'FINALIZED')),
    constraint payroll_runs_month_check  check (run_month between 1 and 12),
    constraint payroll_runs_year_check   check (run_year between 2000 and 2100),
    constraint payroll_runs_final_check  check (
        (status = 'DRAFT'     and finalized_at is null and finalized_by is null)
     or (status = 'FINALIZED' and finalized_at is not null))
);

-- One payslip per employee per run. Every input, rate and intermediate result is stored,
-- so a later salary change, a new tax table or an attendance correction can never alter
-- history: the payslip is read, never recomputed.
create table payslips (
    id                     uuid         not null primary key,
    run_id                 uuid         not null,
    employee_id            uuid         not null,

    -- identity snapshot (a rename or a transfer must not rewrite an old payslip)
    employee_number        varchar(20)  not null,
    employee_name          varchar(150) not null,
    job_title              varchar(120),
    department_name        varchar(120),
    hire_date              date         not null,
    terminated_at          date,

    -- inputs
    base_salary            numeric(12, 2) not null,
    working_days_in_month  integer        not null,
    payable_working_days   integer        not null,
    unpaid_leave_days      integer        not null,
    absent_days            integer        not null,
    late_minutes           integer        not null,
    overtime_minutes       integer        not null,
    holiday_minutes        integer        not null,

    -- rates derived from the inputs
    daily_rate             numeric(12, 2) not null,
    hourly_rate            numeric(12, 2) not null,
    overtime_hours         numeric(12, 2) not null,
    holiday_hours          numeric(12, 2) not null,

    -- earnings
    prorated_base          numeric(12, 2) not null,
    overtime_pay           numeric(12, 2) not null,
    holiday_pay            numeric(12, 2) not null,

    -- deductions before statutory items
    unpaid_leave_deduction numeric(12, 2) not null,
    absence_deduction      numeric(12, 2) not null,

    -- statutory items and the result
    gross_earnings         numeric(12, 2) not null,
    insurable_wage         numeric(12, 2) not null,
    insurance              numeric(12, 2) not null,
    personal_exemption     numeric(12, 2) not null,
    taxable_income         numeric(12, 2) not null,
    tax                    numeric(12, 2) not null,
    net_pay                numeric(12, 2) not null,
    -- true when the arithmetic produced a negative net and it was floored to zero
    net_floored            boolean        not null default false,

    -- the full audit snapshot: config in force (brackets, rates, multipliers) + the tax
    -- bracket breakdown, as JSON
    snapshot               jsonb        not null,
    created_at             timestamptz  not null default now(),

    constraint payslips_employee_uk  unique (run_id, employee_id),
    constraint payslips_run_fk       foreign key (run_id) references payroll_runs (id) on delete cascade,
    constraint payslips_employee_fk  foreign key (employee_id) references employees (id) on delete cascade,
    constraint payslips_net_check    check (net_pay >= 0),
    constraint payslips_days_check   check (working_days_in_month >= 0 and payable_working_days >= 0
                                            and unpaid_leave_days >= 0 and absent_days >= 0),
    constraint payslips_minutes_check check (late_minutes >= 0 and overtime_minutes >= 0
                                             and holiday_minutes >= 0)
);

create index payslips_run_idx on payslips (run_id);
create index payslips_employee_idx on payslips (employee_id);
