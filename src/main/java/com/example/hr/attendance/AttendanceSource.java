package com.example.hr.attendance;

/** Where a session came from: the employee's own clock, or an HR fix-up. */
public enum AttendanceSource {

    /** Created by the employee through {@code /check-in} + {@code /check-out}. */
    SELF,

    /** Created or edited by HR/ADMIN; always carries {@code correctedBy} and a reason. */
    HR_CORRECTION
}
