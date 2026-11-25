package com.campusquest.common;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Mutable aggregate root: application-assigned UUIDv7 id plus optimistic locking.
 * Spring Data treats an entity with a {@code null} version as new, so saves are plain inserts.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    private UUID id = Ids.newId();

    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || org.hibernate.Hibernate.getClass(this) != org.hibernate.Hibernate.getClass(other)) {
            return false;
        }
        return Objects.equals(id, ((BaseEntity) other).getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(id);
    }
}
