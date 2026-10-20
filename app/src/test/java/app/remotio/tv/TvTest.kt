package app.remotio.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvTest {

    @Test
    fun `typed addresses are reduced to a bare host`() {
        assertEquals("192.168.0.20", normalizeHost("  192.168.0.20 "))
        assertEquals("192.168.0.20", normalizeHost("192.168.0.20:8060"))
        assertEquals("192.168.0.20", normalizeHost("http://192.168.0.20:8060/"))
        assertEquals("fe80::1", normalizeHost("fe80::1"))
    }

    @Test
    fun `fire tv shows only the buttons its API has`() {
        val keys = Platform.FireTv.supportedKeys
        assertTrue(RemoteKey.Select in keys)
        assertTrue(RemoteKey.PlayPause in keys)
        assertFalse(RemoteKey.InstantReplay in keys)
        assertFalse(RemoteKey.ChannelUp in keys)
        assertEquals(RemoteKey.entries.toSet(), Platform.Roku.supportedKeys)
    }

    @Test
    fun `only unpaired fire tvs need pairing`() {
        assertTrue(Tv(Platform.FireTv, "192.168.0.170", "Fire TV").needsPairing)
        assertFalse(Tv(Platform.FireTv, "192.168.0.170", "Fire TV", token = "abc").needsPairing)
        assertFalse(Tv(Platform.Roku, "192.168.0.20", "Living Room").needsPairing)
    }

    @Test
    fun `text edits only resend what changed`() {
        assertEquals(0 to "stranger things", textEdit("", "stranger things"))
        assertEquals(0 to "s", textEdit("stranger thing", "stranger things"))
        assertEquals(1 to "", textEdit("stranger things", "stranger thing"))
        assertEquals(4 to "ear", textEdit("the bluey", "the bear"))
        assertEquals(3 to "", textEdit("abc", ""))
    }

    @Test
    fun `an emoji counts as one backspace`() {
        assertEquals(1 to "", textEdit("hi 👋", "hi "))
    }
}
