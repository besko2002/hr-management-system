package com.example.hr.employee;

/**
 * Hook for other modules that must clean up when an employee is terminated. Every
 * listener runs inside the <em>same</em> transaction as the termination itself, so either
 * all of it happens or none of it does.
 *
 * <p>Implemented by the leave module (cancel the pending requests and refund the reserved
 * days); later phases can add their own without touching {@link EmployeeService}.
 */
public interface EmployeeTerminationListener {

    void onTerminated(Employee employee);
}
