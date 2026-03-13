package it.patric.cittaexp.integration.huskclaims;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HuskClaimsHookStateServiceTest {

    @Test
    void resolveModeWhenPluginMissingIsOff() {
        Assertions.assertEquals(
                HuskClaimsHookStateService.GuardMode.HUSKCLAIMS_UNAVAILABLE,
                HuskClaimsHookStateService.resolveMode(false, false)
        );
    }

    @Test
    void resolveModeWhenHookEnabledIsNative() {
        Assertions.assertEquals(
                HuskClaimsHookStateService.GuardMode.NATIVE_HOOK_ACTIVE,
                HuskClaimsHookStateService.resolveMode(true, true)
        );
    }

    @Test
    void resolveModeWhenHookDisabledIsFallback() {
        Assertions.assertEquals(
                HuskClaimsHookStateService.GuardMode.FALLBACK_GUARD_ACTIVE,
                HuskClaimsHookStateService.resolveMode(true, false)
        );
    }
}
