package com.example.hr.department;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** An organisational unit employees can belong to. */
@Entity
@Table(name = "departments")
@Getter
public class Department {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Department() {
    }

    public Department(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.createdAt = Instant.now();
    }

    public void rename(String name) {
        this.name = name;
    }
}
