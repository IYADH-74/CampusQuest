package com.campusquest.quest;

import com.campusquest.common.BaseEntity;
import com.campusquest.common.error.ApiException;
import com.campusquest.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One student's attempt at one quest. Every state transition from {@code docs/domain.md} is a method here,
 * and each one validates its own precondition, so an illegal transition can't be reached from any caller.
 */
@Entity
@Table(name = "quest_participations")
public class Participation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quest_id", nullable = false, updatable = false)
    private Quest quest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ParticipationStatus status = ParticipationStatus.ACTIVE;

    @Column(nullable = false)
    private Instant joinedAt;

    private Instant submittedAt;

    @Column(length = 5000)
    private String submissionText;

    @Column(length = 2048)
    private String submissionUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    private Instant reviewedAt;

    @Column(length = 2000)
    private String feedback;

    private Integer xpAwarded;

    @Column(nullable = false)
    private int revisionCount;

    protected Participation() {}

    public Participation(Quest quest, User student, Instant joinedAt) {
        this.quest = quest;
        this.student = student;
        this.joinedAt = joinedAt;
    }

    public boolean belongsTo(java.util.UUID studentId) {
        return student.getId().equals(studentId);
    }

    public void submit(String text, String url, Instant now) {
        require(ParticipationStatus.ACTIVE, "Only active work can be submitted");
        if (quest.isPastDue(now)) {
            throw ApiException.conflict("PAST_DUE", "The due date has passed");
        }
        status = ParticipationStatus.SUBMITTED;
        submissionText = text;
        submissionUrl = url;
        submittedAt = now;
    }

    public void withdraw() {
        if (!quest.isChallenge()) {
            throw ApiException.conflict("CANNOT_WITHDRAW", "Assignments are mandatory and cannot be withdrawn from");
        }
        require(ParticipationStatus.ACTIVE, "Only active challenges can be withdrawn from");
        status = ParticipationStatus.WITHDRAWN;
    }

    public void rejoin(Instant now) {
        require(ParticipationStatus.WITHDRAWN, "Already participating");
        status = ParticipationStatus.ACTIVE;
        joinedAt = now;
        submissionText = null;
        submissionUrl = null;
        submittedAt = null;
        feedback = null;
        reviewer = null;
        reviewedAt = null;
    }

    public void approve(User reviewer, String feedback, int xp, Instant now) {
        review(reviewer, feedback, now);
        status = ParticipationStatus.APPROVED;
        xpAwarded = xp;
    }

    public void reject(User reviewer, String feedback, int xpDelta, Instant now) {
        review(reviewer, feedback, now);
        status = ParticipationStatus.REJECTED;
        xpAwarded = xpDelta;
    }

    public void requestChanges(User reviewer, String feedback, Instant now) {
        if (quest.isPastDue(now)) {
            throw ApiException.conflict("PAST_DUE",
                    "The due date has passed. Approve or reject the submission instead");
        }
        review(reviewer, feedback, now);
        status = ParticipationStatus.ACTIVE;
        revisionCount++;
    }

    public void expire(int xpDelta) {
        require(ParticipationStatus.ACTIVE, "Only active work can expire");
        status = ParticipationStatus.EXPIRED;
        xpAwarded = xpDelta;
    }

    /** Quest cancelled or student moved cohort: open work is dropped with no XP effect. */
    public boolean cancelIfOpen() {
        if (ParticipationStatus.IN_FLIGHT.contains(status)) {
            status = ParticipationStatus.CANCELLED;
            return true;
        }
        return false;
    }

    private void review(User reviewer, String feedback, Instant now) {
        require(ParticipationStatus.SUBMITTED, "Only submitted work can be reviewed");
        this.reviewer = reviewer;
        this.feedback = feedback;
        this.reviewedAt = now;
    }

    private void require(ParticipationStatus expected, String message) {
        if (status != expected) {
            throw ApiException.conflict("INVALID_PARTICIPATION_STATE", message);
        }
    }

    public Quest getQuest() {
        return quest;
    }

    public User getStudent() {
        return student;
    }

    public ParticipationStatus getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getSubmissionText() {
        return submissionText;
    }

    public String getSubmissionUrl() {
        return submissionUrl;
    }

    public User getReviewer() {
        return reviewer;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getFeedback() {
        return feedback;
    }

    public Integer getXpAwarded() {
        return xpAwarded;
    }

    public int getRevisionCount() {
        return revisionCount;
    }
}
