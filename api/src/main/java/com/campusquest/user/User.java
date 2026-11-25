package com.campusquest.user;

import com.campusquest.cohort.Cohort;
import com.campusquest.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A person on the platform. Role-specific attributes (cohort, department, subject, XP) are guarded by
 * check constraints in the schema and by the factory / mutator methods here; there are no public setters.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountStatus status;

    @Column(nullable = false)
    private int tokenVersion;

    @Column(length = 120)
    private String department;

    @Column(length = 120)
    private String subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort;

    @Column(nullable = false)
    private long xp;

    @Column(nullable = false)
    private int level;

    protected User() {}

    private User(String email, String passwordHash, String firstName, String lastName, Role role,
            AccountStatus status) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.status = status;
    }

    /** A student who signed up themselves and still has to prove they own the email address. */
    public static User selfRegisteredStudent(String email, String passwordHash, String firstName, String lastName,
            Cohort cohort) {
        User user = new User(email, passwordHash, firstName, lastName, Role.STUDENT,
                AccountStatus.PENDING_VERIFICATION);
        user.cohort = cohort;
        return user;
    }

    /** An account created by an admin; the person sets their own password through the invite link. */
    public static User invited(String email, String firstName, String lastName, Role role) {
        return new User(email, null, firstName, lastName, role, AccountStatus.INVITED);
    }

    /** Fully active account with a password; used by seeding and tests. */
    public static User active(String email, String passwordHash, String firstName, String lastName, Role role) {
        return new User(email, passwordHash, firstName, lastName, role, AccountStatus.ACTIVE);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isStudent() {
        return role == Role.STUDENT;
    }

    public void activate() {
        if (passwordHash == null) {
            throw new IllegalStateException("Cannot activate an account without a password");
        }
        this.status = AccountStatus.ACTIVE;
    }

    public void disable() {
        this.status = AccountStatus.DISABLED;
        revokeIssuedTokens();
    }

    public void enable() {
        this.status = passwordHash == null ? AccountStatus.INVITED : AccountStatus.ACTIVE;
    }

    /** Sets a new password and invalidates every access token issued so far. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        revokeIssuedTokens();
    }

    /** Transparent hash upgrade (e.g. stronger encoder parameters) that keeps sessions alive. */
    public void upgradePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void revokeIssuedTokens() {
        this.tokenVersion++;
    }

    public void rename(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void assignCohort(Cohort cohort) {
        requireRole(Role.STUDENT, "cohort");
        this.cohort = cohort;
    }

    public void assignDepartment(String department) {
        requireRole(Role.STAFF, "department");
        this.department = department;
    }

    public void assignSubject(String subject) {
        requireRole(Role.TEACHER, "subject");
        this.subject = subject;
    }

    /** Only {@code XpLedger} calls this, inside the same transaction as the ledger row. */
    public void applyXpBalance(long newBalance, int newLevel) {
        requireRole(Role.STUDENT, "xp");
        this.xp = newBalance;
        this.level = newLevel;
    }

    private void requireRole(Role expected, String attribute) {
        if (role != expected) {
            throw new IllegalStateException(attribute + " only applies to " + expected);
        }
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Role getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public String getDepartment() {
        return department;
    }

    public String getSubject() {
        return subject;
    }

    public Cohort getCohort() {
        return cohort;
    }

    public long getXp() {
        return xp;
    }

    public int getLevel() {
        return level;
    }
}
