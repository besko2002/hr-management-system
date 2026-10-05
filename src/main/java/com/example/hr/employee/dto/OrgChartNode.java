package com.example.hr.employee.dto;

import java.util.List;
import java.util.UUID;

/** A node of the company tree. No salary field anywhere in the org chart. */
public record OrgChartNode(
        UUID id,
        String employeeNumber,
        String fullName,
        String jobTitle,
        String departmentName,
        List<OrgChartNode> children) {
}
