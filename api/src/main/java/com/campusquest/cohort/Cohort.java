package com.campusquest.cohort;

import com.campusquest.common.BaseEntity;
import com.campusquest.user.Role;
import com.campusquest.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** A group of students who learn together (e.g. {@code CS-2A}), plus the roster of teachers who teach it. */
@Entity
@Table(name = "cohorts")
public class Cohort extends BaseEntity {

    @Column(nullable = false, length = 32, updatable = false)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean archived;

    @ManyToMany
    @JoinTable(name = "teaching_assignments",
            joinColumns = @JoinColumn(name = "cohort_id"),
            inverseJoinColumns = @JoinColumn(name = "teacher_id"))
    private Set<User> teachers = new HashSet<>();

    protected Cohort() {}

    public Cohort(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void archive() {
        this.archived = true;
    }

    public void restore() {
        this.archived = false;
    }

    public void replaceTeachers(Collection<User> newTeachers) {
        if (newTeachers.stream().anyMatch(t -> t.getRole() != Role.TEACHER)) {
            throw new IllegalArgumentException("Only teachers can be put on a teaching roster");
        }
        teachers.clear();
        teachers.addAll(newTeachers);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isArchived() {
        return archived;
    }

    public Set<User> getTeachers() {
        return Collections.unmodifiableSet(teachers);
    }
}
