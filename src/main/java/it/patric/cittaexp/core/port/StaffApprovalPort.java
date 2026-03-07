package it.patric.cittaexp.core.port;

import it.patric.cittaexp.core.model.ApprovalAction;
import it.patric.cittaexp.core.model.StaffApprovalTicket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffApprovalPort {

    StaffApprovalTicket open(StaffApprovalTicket ticket);

    Optional<StaffApprovalTicket> findById(UUID ticketId);

    List<StaffApprovalTicket> listPending(int limit);

    StaffApprovalTicket review(UUID ticketId, UUID reviewerUuid, ApprovalAction action, String note);
}
