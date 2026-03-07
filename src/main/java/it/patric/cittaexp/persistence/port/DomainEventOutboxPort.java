package it.patric.cittaexp.persistence.port;

import it.patric.cittaexp.persistence.domain.OutboxEvent;
import java.util.List;
import java.util.UUID;

public interface DomainEventOutboxPort {

    void enqueue(OutboxEvent event);

    List<OutboxEvent> pollPending(int limit);

    void markApplied(UUID eventId);

    void markConflict(UUID eventId, String reason);

    void markFailed(UUID eventId, String reason);

    long pendingCount();

    long conflictCount();
}
