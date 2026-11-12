package dev.hram.androsaur.stand

import kotlin.test.*

class WifiQrTest {
    @Test fun readsRouterAndAndroidSharingFormats() {
        val router = WifiQr.parse("WIFI:T:WPA;S:Home;P:example-pass;;")
        assertEquals("Home", router.ssid)
        assertEquals("example-pass", router.password)
        val android = WifiQr.parse("WIFI:S:Home;T:WPA;P:example-pass;H:false;;")
        assertEquals(router.ssid, android.ssid)
        assertEquals(router.password, android.password)
    }

    @Test fun preservesEscapesAndWhitespace() {
        val network = WifiQr.parse("""WIFI:T:WPA;S: Home\;\:\,\"\\ ;P:pass\;\:\,\"\\word;;""")
        assertEquals(" Home;:,\"\\ ", network.ssid)
        assertEquals("pass;:,\"\\word", network.password)
    }

    @Test fun supportsOpenAndHiddenNetworks() {
        assertEquals("", WifiQr.parse("WIFI:T:nopass;S:Guest;H:true;;").password)
        assertEquals("Guest", WifiQr.parse("WIFI:S:Guest;;").ssid)
    }

    @Test fun rejectsOtherQrAndMalformedOrUnsupportedNetworks() {
        for (raw in listOf(
            "https://example.org", "WIFI:T:WPA;P:example-pass;;",
            "WIFI:T:WPA;S:Home;P:short;;", "WIFI:T:WEP;S:Home;P:example-pass;;",
            "WIFI:T:WPA2-EAP;S:Home;P:example-pass;;", "WIFI:S:Home;S:Other;;",
            "WIFI:S:Home;broken;;", "WIFI:S:Home;P:trailing\\",
            "WIFI:S:${"я".repeat(17)};;", "WIFI:S:Home\u0000;;",
            "WIFI:T:WPA;S:Home;P:${"p".repeat(64)};;"
        )) assertFailsWith<IllegalArgumentException> { WifiQr.parse(raw) }
    }

    @Test fun doesNotExposeCredentialsInRenderingOrErrors() {
        val password = "private-example"
        assertFalse(WifiQr.parse("WIFI:T:WPA;S:Home;P:$password;;").toString().contains(password))
        val error = assertFailsWith<IllegalArgumentException> { WifiQr.parse("WIFI:T:WEP;S:Home;P:$password;;") }
        assertFalse(error.message.orEmpty().contains(password))
    }
}
