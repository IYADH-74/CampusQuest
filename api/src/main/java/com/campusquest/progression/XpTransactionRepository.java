package com.campusquest.progression;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface XpTransactionRepository extends JpaRepository<XpTransaction, UUID> {

    Page<XpTransaction> findByStudentIdOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    @Query("select coalesce(sum(t.amount), 0) from XpTransaction t where t.studentId = :studentId")
    long sumForStudent(UUID studentId);
}
