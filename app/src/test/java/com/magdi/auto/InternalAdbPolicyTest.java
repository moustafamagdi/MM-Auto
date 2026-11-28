package com.magdi.auto;
import org.junit.Test;
import static org.junit.Assert.*;

public class InternalAdbPolicyTest {
    @Test public void validatesEphemeralInputsWithoutShellSyntax() {
        assertEquals(37123, InternalAdbPolicy.port("37123"));
        assertEquals("001234", InternalAdbPolicy.code("001234"));
        assertEquals("001234", InternalAdbPolicy.code("٠٠١٢٣٤"));
        assertEquals(37123, InternalAdbPolicy.port("٣٧١٢٣"));
        assertEquals(0, InternalAdbPolicy.optionalPort(""));
        for (String invalid : new String[]{"0","65536","-1","host:1234","42;id"," 1234",""}) {
            assertThrows(IllegalArgumentException.class, () -> InternalAdbPolicy.port(invalid));
        }
        for (String invalid : new String[]{"12345","1234567","123 45","abcdef","12345\n"})
            assertThrows(IllegalArgumentException.class, () -> InternalAdbPolicy.code(invalid));
    }
    @Test public void onlyApprovedTargetsAndFixedPreserveDataCommand() {
        assertThrows(IllegalArgumentException.class, () -> InternalAdbPolicy.target("AUTO"));
        assertThrows(IllegalArgumentException.class, () -> InternalAdbPolicy.target("com.other.app"));
        assertThrows(IllegalArgumentException.class, () -> InternalAdbPolicy.target(null));
        assertEquals("exec:cmd package install -r --user 10 -i com.android.vending -S 2048",
                InternalAdbPolicy.installCommand(Releases.App.TUBE,10,2048));
        assertThrows(IllegalArgumentException.class, () -> InternalAdbPolicy.installCommand(Releases.App.IPTV,0,0));
    }
    @Test public void noFalseSuccessOrRawOutputInDiagnostics() {
        assertTrue(InternalAdbPolicy.success("Success\n"));
        assertFalse(InternalAdbPolicy.success("Failure [Success]"));
        assertFalse(InternalAdbPolicy.success("Success\nFailure"));
        assertFalse(InternalAdbPolicy.success(""));
        assertEquals("INSTALL_FAILED_UPDATE_INCOMPATIBLE", InternalAdbPolicy.failureCode("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: private path]"));
        assertEquals("NO_CONFIRMED_RESULT", InternalAdbPolicy.failureCode("private key or pairing input"));
    }
}
