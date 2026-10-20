package app.remotio.tv.roku

import app.remotio.tv.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RokuParsingTest {

    @Test
    fun `device-info prefers the name the owner gave the TV`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <device-info>
                <vendor-name>TCL</vendor-name>
                <model-name>55S455</model-name>
                <friendly-device-name>TCL•55S455 - X00000ABC123</friendly-device-name>
                <user-device-name>Living Room &amp; Kitchen</user-device-name>
                <is-tv>true</is-tv>
                <power-mode>PowerOn</power-mode>
            </device-info>
        """.trimIndent()

        val tv = parseDeviceInfo("192.168.0.20", xml)

        assertEquals(Platform.Roku, tv.platform)
        assertEquals("192.168.0.20", tv.host)
        assertEquals("Living Room & Kitchen", tv.name)
    }

    @Test
    fun `device-info falls back to the friendly name when the owner never named it`() {
        val xml = """
            <device-info>
                <model-name>55S455</model-name>
                <friendly-device-name>TCL 55S455</friendly-device-name>
                <user-device-name></user-device-name>
            </device-info>
        """.trimIndent()

        assertEquals("TCL 55S455", parseDeviceInfo("10.0.0.5", xml).name)
    }

    @Test
    fun `ssdp reply from a Roku yields its host`() {
        val reply = "HTTP/1.1 200 OK\r\n" +
            "Cache-Control: max-age=3600\r\n" +
            "ST: roku:ecp\r\n" +
            "Location: http://192.168.0.134:8060/\r\n" +
            "USN: uuid:roku:ecp:X00000ABC123\r\n\r\n"

        assertEquals("192.168.0.134", parseSsdpLocationHost(reply))
    }

    @Test
    fun `ssdp reply from some other device is ignored`() {
        val reply = "HTTP/1.1 200 OK\r\n" +
            "ST: urn:dial-multiscreen-org:service:dial:1\r\n" +
            "LOCATION: http://192.168.0.170:60000/upnp/dev/abc/desc\r\n\r\n"

        assertNull(parseSsdpLocationHost(reply))
    }

    @Test
    fun `characters become url-encoded literal keys`() {
        assertEquals("Lit_a", literalKey("a"))
        assertEquals("Lit_%20", literalKey(" "))
        assertEquals("Lit_%26", literalKey("&"))
        assertEquals("Lit_%E2%82%AC", literalKey("€"))
    }
}
