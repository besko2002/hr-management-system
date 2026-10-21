-- Phase 1 schema: departments, employees and the self-referencing org hierarchy.

create table departments (
    id         uuid         not null primary key,
    name       varchar(120) not null unique,
    created_at timestamptz  not null default now()
);

-- Human-readable employee numbers (EMP-0001, EMP-0002, ...) come from a database
-- sequence so they stay unique and gap-tolerant under concurrency.
create sequence employee_number_seq start with 1 increment by 1;

create table employees (
    id                   uuid           not null primary key,
    employee_number      varchar(20)    not null unique
                             default ('EMP-' || lpad(nextval('employee_number_seq')::text, 4, '0')),
    full_name            varchar(150)   not null,
    email                varchar(255)   not null,
    password_hash        varchar(100)   not null,
    role                 varchar(20)    not null,
    job_title            varchar(120),
    department_id        uuid,
    manager_id           uuid,
    hire_date            date           not null,
    salary               numeric(12, 2),
    status               varchar(20)    not null,
    must_change_password boolean        not null default false,
    created_at           timestamptz    not null default now(),
    updated_at           timestamptz    not null default now(),
    version              bigint         not null default 0,

    constraint employees_role_check    check (role in ('ADMIN', 'HR', 'EMPLOYEE')),
    constraint employees_status_check  check (status in ('ACTIVE', 'TERMINATED')),
    constraint employees_salary_check  check (salary is null or salary >= 0),
    constraint employees_manager_not_self_check check (manager_id is null or manager_id <> id),
    constraint employees_department_fk foreign key (department_id) references departments (id),
    constraint employees_manager_fk    foreign key (manager_id) references employees (id)
);

-- Email is unique case-insensitively; it is also stored lower-cased by the application.
create unique index employees_email_lower_uk on employees (lower(email));
create index employees_manager_id_idx on employees (manager_id);
create index employees_department_id_idx on employees (department_id);
