package com.example.hr.employee.dto;

import java.util.UUID;

/** {@code managerId == null} detaches the employee and makes them a root of the org chart. */
public record SetManagerRequest(UUID managerId) {
}
