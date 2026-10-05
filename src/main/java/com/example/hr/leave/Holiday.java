package com.example.hr.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A company-wide non-working day. The date is unique; HR and ADMIN maintain the list. */
@Entity
@Table(name = "holidays")
@Getter
public class Holiday {

    @Id
    private UUID id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate date;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Holiday() {
    }

    public Holiday(LocalDate date, String name) {
        this.id = UUID.randomUUID();
        this.date = date;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public void moveTo(LocalDate date) {
        this.date = date;
    }

    public void rename(String name) {
        this.name = name;
    }
}
