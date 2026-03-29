package it.patric.cittaexp.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerEconomyServiceTest {

    @Test
    void transactionSuccessSupportsMethodBasedResponses() {
        assertTrue(PlayerEconomyService.readTransactionSuccess(new MethodSuccessResponse()));
        assertEquals("-", PlayerEconomyService.readTransactionError(new MethodSuccessResponse()));
    }

    @Test
    void transactionSuccessSupportsFieldBasedResponses() {
        assertTrue(PlayerEconomyService.readTransactionSuccess(new FieldSuccessResponse()));
        assertEquals("ok", PlayerEconomyService.readTransactionError(new FieldSuccessResponse()));
    }

    @Test
    void transactionSuccessFallsBackToTypeEnumStyleResponses() {
        assertTrue(PlayerEconomyService.readTransactionSuccess(new TypeSuccessResponse()));
        assertEquals("done", PlayerEconomyService.readTransactionError(new TypeSuccessResponse()));
    }

    @Test
    void unknownResponseShapeFailsClosed() {
        assertFalse(PlayerEconomyService.readTransactionSuccess(new Object()));
        assertNull(PlayerEconomyService.readTransactionError(new Object()));
    }

    public static final class MethodSuccessResponse {
        public boolean transactionSuccess() {
            return true;
        }

        public String errorMessage() {
            return "-";
        }
    }

    public static final class FieldSuccessResponse {
        public final boolean transactionSuccess = true;
        public final String errorMessage = "ok";
    }

    public static final class TypeSuccessResponse {
        public final ResponseType type = ResponseType.SUCCESS;
        public final String errorMessage = "done";
    }

    public enum ResponseType {
        SUCCESS,
        FAILURE
    }
}
