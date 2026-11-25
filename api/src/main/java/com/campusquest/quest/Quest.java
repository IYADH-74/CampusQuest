package com.campusquest.quest;

import com.campusquest.cohort.Cohort;
import com.campusquest.common.BaseEntity;
import com.campusquest.common.error.ApiException;
import com.campusquest.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** A unit of work worth XP. Lifecycle rules live here; who may trigger them lives in the services. */
@Entity
@Table(name = "quests")
public class Quest extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private QuestKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private QuestCategory category;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @Column(nullable = false)
    private int tier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuestStatus status = QuestStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    private Instant dueAt;

    private Integer maxParticipants;

    @Column(nullable = false)
    private int minLevel;

    private Instant publishedAt;

    private Instant closedAt;

    private Instant cancelledAt;

    @ManyToMany
    @org.hibernate.annotations.BatchSize(size = 50)
    @JoinTable(name = "quest_cohorts",
            joinColumns = @JoinColumn(name = "quest_id"),
            inverseJoinColumns = @JoinColumn(name = "cohort_id"))
    private Set<Cohort> targetCohorts = new HashSet<>();

    protected Quest() {}

    public Quest(QuestKind kind, QuestCategory category, String title, String description, int tier, User author) {
        this.kind = kind;
        this.category = category;
        this.title = title;
        this.description = description;
        this.tier = tier;
        this.author = author;
    }

    public boolean isAssignment() {
        return kind == QuestKind.ASSIGNMENT;
    }

    public boolean isChallenge() {
        return kind == QuestKind.CHALLENGE;
    }

    public boolean isAuthoredBy(java.util.UUID userId) {
        return author.getId().equals(userId);
    }

    public boolean isPastDue(Instant now) {
        return dueAt != null && !now.isBefore(dueAt);
    }

    /** Students can see this quest on the challenge board / quest page. */
    public boolean isVisibleToStudents() {
        return status == QuestStatus.PUBLISHED || status == QuestStatus.CLOSED;
    }

    public boolean isAcceptingParticipants(Instant now) {
        return isChallenge() && status == QuestStatus.PUBLISHED && !isPastDue(now);
    }

    // --- lifecycle -------------------------------------------------------------------------------

    public void publish(Instant now) {
        requireStatus(QuestStatus.DRAFT, "Only drafts can be published");
        status = QuestStatus.PUBLISHED;
        publishedAt = now;
    }

    public void close(Instant now) {
        if (!isChallenge()) {
            throw ApiException.conflict("INVALID_QUEST_STATE", "Only challenges can be closed");
        }
        requireStatus(QuestStatus.PUBLISHED, "Only published challenges can be closed");
        status = QuestStatus.CLOSED;
        closedAt = now;
    }

    public void cancel(Instant now) {
        if (status != QuestStatus.PUBLISHED && status != QuestStatus.CLOSED) {
            throw ApiException.conflict("INVALID_QUEST_STATE", "Only published or closed quests can be cancelled");
        }
        status = QuestStatus.CANCELLED;
        cancelledAt = now;
    }

    public void requireDraft() {
        requireStatus(QuestStatus.DRAFT, "Only drafts can be changed this way");
    }

    private void requireStatus(QuestStatus expected, String message) {
        if (status != expected) {
            throw ApiException.conflict("INVALID_QUEST_STATE", message);
        }
    }

    // --- edits (callers enforce which are allowed in which status) ---------------------------------

    public void retitle(String title) {
        this.title = title;
    }

    public void describe(String description) {
        this.description = description;
    }

    public void changeCategory(QuestCategory category) {
        this.category = category;
    }

    public void changeTier(int tier) {
        this.tier = tier;
    }

    public void changeDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public void changeMaxParticipants(Integer maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public void changeMinLevel(int minLevel) {
        this.minLevel = minLevel;
    }

    public void retarget(Collection<Cohort> cohorts) {
        targetCohorts.clear();
        targetCohorts.addAll(cohorts);
    }

    // --- accessors ---------------------------------------------------------------------------------

    public QuestKind getKind() {
        return kind;
    }

    public QuestCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getTier() {
        return tier;
    }

    public QuestStatus getStatus() {
        return status;
    }

    public User getAuthor() {
        return author;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Integer getMaxParticipants() {
        return maxParticipants;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Set<Cohort> getTargetCohorts() {
        return Collections.unmodifiableSet(targetCohorts);
    }
}
