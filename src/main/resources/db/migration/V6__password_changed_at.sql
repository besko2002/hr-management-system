-- Tokens issued before this instant are rejected (see ActiveEmployeeFilter). Null = never changed.
alter table employees add column password_changed_at timestamptz;
