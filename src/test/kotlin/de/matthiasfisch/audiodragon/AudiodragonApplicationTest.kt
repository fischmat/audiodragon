package de.matthiasfisch.audiodragon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AudiodragonApplicationTest {
    @Test
    fun `announces an embedded server port in the desktop launcher format`() {
        assertEquals("AUDIODRAGON_SERVER_PORT=43127", serverPortAnnouncement(43127))
    }
}
