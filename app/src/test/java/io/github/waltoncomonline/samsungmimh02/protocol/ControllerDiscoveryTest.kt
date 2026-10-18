package io.github.waltoncomonline.samsungmimh02.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class ControllerDiscoveryTest {
    @Test
    fun `enumerates active slash 24 without network broadcast or local address`() {
        val local = InetAddress.getByName("192.168.50.44") as Inet4Address

        val candidates = ControllerDiscovery.candidateAddresses(local, 24)

        assertEquals(253, candidates.size)
        assertTrue("192.168.50.121" in candidates)
        assertFalse("192.168.50.0" in candidates)
        assertFalse("192.168.50.44" in candidates)
        assertFalse("192.168.50.255" in candidates)
    }

    @Test
    fun `limits broad networks to local slash 24`() {
        val local = InetAddress.getByName("10.22.33.44") as Inet4Address

        val candidates = ControllerDiscovery.candidateAddresses(local, 16)

        assertTrue(candidates.all { it.startsWith("10.22.33.") })
        assertEquals(253, candidates.size)
    }

    @Test
    fun `respects narrower subnet prefix`() {
        val local = InetAddress.getByName("192.168.4.130") as Inet4Address

        val candidates = ControllerDiscovery.candidateAddresses(local, 25)

        assertEquals(125, candidates.size)
        assertTrue("192.168.4.129" in candidates)
        assertTrue("192.168.4.254" in candidates)
        assertFalse("192.168.4.127" in candidates)
    }
}