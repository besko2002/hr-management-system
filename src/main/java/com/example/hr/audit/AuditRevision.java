package com.example.hr.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Envers revision row. {@code changedBy} is the JWT email (or subject) of the actor, or
 * {@value AuditRevisionListener#SYSTEM} when there is no authenticated principal (bootstrap,
 * scheduled jobs).
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity(AuditRevisionListener.class)
public class AuditRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "rev")
    private int id;

    @RevisionTimestamp
    @Column(name = "revtstmp", nullable = false)
    private long timestamp;

    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;

    public int getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }
}
