package it.patric.cittaexp.core.view;

import it.patric.cittaexp.core.model.TicketStatus;
import it.patric.cittaexp.core.model.TicketType;
import java.util.List;
import java.util.UUID;

public record ApprovalsQueueView(List<TicketRow> tickets) {

    public record TicketRow(
            UUID ticketId,
            UUID cityId,
            String cityName,
            TicketType type,
            TicketStatus status,
            String requesterName,
            String reason,
            long requestedAtEpochMilli
    ) {
    }
}
