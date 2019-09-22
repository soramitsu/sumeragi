import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import time.seconds

class UtilsTest {
    @Test
    fun splitTest() {
        val peers = createPeers(4, 1, 1.seconds, 1.seconds)
        val (setA, setB) = split(peers)

        assertEquals(listOf(0, 1, 2), setA.map { it.id })
        assertEquals(listOf(3), setB.map { it.id })
    }
}