package com.magdi.auto;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RepairStateTest {
    @Test public void recognizesSameVersionReinstallByUpdateTime() {
        assertTrue(RepairState.finished(100, 200, 1000));
    }
    @Test public void cancelAndEarlyResumeDoNotOfferShizuku() {
        assertFalse(RepairState.finished(100, 100, 1000));
    }
    @Test public void uninstallDoesNotCountAsRepair() {
        assertFalse(RepairState.finished(100, 0, 1000));
    }
    @Test public void expiredAndInvalidClockDoNotComplete() {
        assertFalse(RepairState.finished(100, 200, 30 * 60 * 1000L + 1));
        assertFalse(RepairState.finished(100, 200, -1));
    }
}
