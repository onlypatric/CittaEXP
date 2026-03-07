package it.patric.cittaexp.core.service;

import it.patric.cittaexp.core.model.CapitalState;
import java.util.Optional;

public interface CapitalService {

    Optional<CapitalState> currentCapital();

    Optional<CapitalState> refreshFromRanking();
}
