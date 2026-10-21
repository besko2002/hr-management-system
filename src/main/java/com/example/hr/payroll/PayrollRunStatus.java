package com.example.hr.payroll;

/**
 * A payroll run is either still being worked on or closed forever.
 *
 * <pre>
 *   DRAFT      -> recalculate, finalize, delete
 *   FINALIZED  -> nothing (recalculate / delete answer 409)
 * </pre>
 *
 * Once FINALIZED, later attendance corrections, salary changes or config edits cannot move
 * a single figure: the payslips are read, never recomputed.
 */
public enum PayrollRunStatus {
    DRAFT,
    FINALIZED;

    public boolean isFinalized() {
        return this == FINALIZED;
    }
}
