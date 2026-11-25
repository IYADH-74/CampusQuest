package com.campusquest.progression;

import com.campusquest.audit.AuditAction;
import com.campusquest.audit.AuditService;
import com.campusquest.common.error.ApiException;
import com.campusquest.common.web.PageResponse;
import com.campusquest.progression.ProgressionApi.XpAdjustmentRequest;
import com.campusquest.progression.ProgressionApi.XpTransactionView;
import com.campusquest.security.AuthenticatedUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class XpAdjustmentService {

    private final XpLedger ledger;
    private final XpTransactionRepository transactions;
    private final AuditService audit;

    public XpAdjustmentService(XpLedger ledger, XpTransactionRepository transactions, AuditService audit) {
        this.ledger = ledger;
        this.transactions = transactions;
        this.audit = audit;
    }

    @Transactional
    public XpTransactionView adjust(AuthenticatedUser admin, UUID studentId, XpAdjustmentRequest request) {
        if (request.amount() == 0) {
            throw ApiException.badRequest("ZERO_ADJUSTMENT", "Adjustment amount must not be zero");
        }
        XpTransaction tx = ledger.post(new XpLedger.Entry(studentId, request.amount(), XpReason.MANUAL_ADJUSTMENT,
                null, null, admin.id(), request.note().strip()));
        audit.record(AuditAction.XP_ADJUSTED, admin.id(), "user", studentId,
                Map.of("requested", request.amount(), "applied", tx.getAmount(), "note", request.note().strip()));
        return XpTransactionView.of(tx);
    }

    @Transactional(readOnly = true)
    public PageResponse<XpTransactionView> history(UUID studentId, int page, int size) {
        return PageResponse.of(transactions.findByStudentIdOrderByCreatedAtDesc(studentId, PageRequest.of(page, size)),
                XpTransactionView::of);
    }
}
