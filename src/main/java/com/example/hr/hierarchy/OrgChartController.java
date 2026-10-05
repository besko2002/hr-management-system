package com.example.hr.hierarchy;

import com.example.hr.employee.dto.OrgChartNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/org-chart")
@Tag(name = "Org chart")
class OrgChartController {

    private final HierarchyService hierarchy;

    OrgChartController(HierarchyService hierarchy) {
        this.hierarchy = hierarchy;
    }

    @GetMapping
    @Operation(summary = "The whole company as a nested tree (any authenticated user, no salaries)")
    List<OrgChartNode> orgChart() {
        return hierarchy.orgChart();
    }
}
