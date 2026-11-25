package com.campusquest.common;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Base for append-mostly records (ledger rows, tokens) that carry no version column.
 * Implements {@link Persistable} so Spring Data inserts without a preceding SELECT.
 */
@MappedSuperclass
public abstract class InsertOnlyEntity implements Persistable<UUID> {

    @Id
    private UUID id = Ids.newId();

    @Transient
    private boolean isNew = true;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || org.hibernate.Hibernate.getClass(this) != org.hibernate.Hibernate.getClass(other)) {
            return false;
        }
        return Objects.equals(id, ((InsertOnlyEntity) other).getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(id);
    }
}
