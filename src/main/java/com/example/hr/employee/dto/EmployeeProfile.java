package com.example.hr.employee.dto;

/**
 * A single employee profile in a response. Two concrete shapes exist on purpose:
 * {@link EmployeeResponse} carries the salary, {@link EmployeeView} has no salary field
 * at all — so a manager's view of a report can never accidentally contain it.
 */
public sealed interface EmployeeProfile permits EmployeeResponse, EmployeeView {

    java.util.UUID id();

    String fullName();
}
