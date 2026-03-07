package it.patric.cittaexp.core.service;

import it.patric.cittaexp.core.model.StaffApprovalTicket;
import it.patric.cittaexp.core.model.TicketType;
import java.util.List;
import java.util.UUID;

public interface StaffApprovalService {

    StaffApprovalTicket request(UUID cityId, UUID requesterUuid, TicketType type, String reason, String payloadJson);

    StaffApprovalTicket approve(UUID ticketId, UUID reviewerUuid, String note);

    StaffApprovalTicket reject(UUID ticketId, UUID reviewerUuid, String note);

    List<StaffApprovalTicket> pending(int limit);
}
