package io.github.muntashirakon.adb;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StreamCompletionTest {
    private AdbStream stream() throws Exception {
        AdbConnection connection = mock(AdbConnection.class);
        when(connection.getMaxData()).thenReturn(4096);
        return new AdbStream(connection, 1);
    }
    @Test(timeout=1000) public void bufferedPeerResultDrainsBeforeEof() throws Exception {
        AdbStream stream = stream(); stream.addPayload("Success\n".getBytes("UTF-8")); stream.notifyClose(true);
        byte[] out = new byte[8];
        assertEquals(8, stream.openInputStream().read(out));
        assertEquals("Success\n", new String(out,"UTF-8"));
        assertEquals(-1, stream.openInputStream().read(out));
    }
    @Test(timeout=1000) public void closeAfterConsumptionIsEof() throws Exception {
        AdbStream stream = stream(); stream.addPayload(new byte[]{1});
        assertEquals(1,stream.read(new byte[1],0,1)); stream.notifyClose(true);
        assertEquals(-1,stream.read(new byte[1],0,1));
    }
    @Test(timeout=1000) public void partialBufferRemainsReadableAfterPeerClose() throws Exception {
        AdbStream stream = stream(); stream.addPayload(new byte[]{1,2,3});
        assertEquals(1,stream.read(new byte[1],0,1)); stream.notifyClose(true);
        byte[] out = new byte[2]; assertEquals(2,stream.read(out,0,2));
        assertArrayEquals(new byte[]{2,3},out); assertEquals(-1,stream.read(out,0,2));
    }
    @Test(timeout=1000) public void explicitLocalCloseIsNotSuccessfulEof() throws Exception {
        AdbStream stream = stream(); stream.notifyClose(false);
        assertThrows(java.io.IOException.class, () -> stream.read(new byte[1],0,1));
    }
}
