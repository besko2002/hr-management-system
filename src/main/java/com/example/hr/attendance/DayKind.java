package com.example.hr.attendance;

/** What the company calendar says about a date, independently of any employee. */
public enum DayKind {
    WORKING,
    WEEKEND,
    HOLIDAY;

    /** Weekend and holiday work is paid at the holiday rate, so they behave identically. */
    public boolean isRestDay() {
        return this != WORKING;
    }
}
