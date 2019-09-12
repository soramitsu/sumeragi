import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import time.seconds
import kotlin.random.Random


class SumeragiTest {
    val F = 1
    val N = 3 * F + 1
    val BUFFER_SIZE = 1
    val BUFFER_TIME = 1.seconds

    val client = Context(ClientType.CLIENT, 1)
    val random = Random(0)

    lateinit var peers: List<Peer>

    @BeforeEach
    fun init() {
        peers = createPeers(N, BUFFER_SIZE, BUFFER_TIME)
    }

    @Test
    fun initTest() {
        peers.forEach {
            assertEquals(1, it.blocks.size)
            assertEquals(4, it.peers.size)
            assertEquals(3, it.leader!!.id)
            assertEquals(1, it.tail!!.id)

            val setA = it.setA.map { it.id }
            val setB = it.setB.map { it.id }

            assertEquals(listOf(3, 0, 1), setA)
            assertEquals(listOf(2), setB)
        }
    }

    @Test
    fun sunnyDayOneRound() {
        val tx = Transaction(random.nextInt())
        peers.first().onTransaction(client, tx)
        peers.forEach {
            it.round()
        }


        val blockHash = tx.hash.toLong()
        peers.forEach {
            assertEquals(2, it.blocks.size)
            assertEquals(Block(2, blockHash, listOf(tx)), it.blocks.last())
            assertEquals(1, it.leader!!.id)
            assertEquals(0, it.tail!!.id)
            assertEquals(0, it.txs.size)

            val setA = it.setA.map { it.id }
            val setB = it.setB.map { it.id }

            assertEquals(listOf(1, 2, 0), setA)
            assertEquals(listOf(3), setB)
        }
    }

    @Test
    fun sunnyDayTwoRounds() {
        val txs = listOf(Transaction(random.nextInt()), Transaction(random.nextInt()))
        txs.forEach { tx ->
            peers.first().onTransaction(client, tx)
            peers.forEach {
                it.round()
            }
        }
        val blockHash = txs.last().hash.toLong()

        peers.forEach {
            assertEquals(3, it.blocks.size)
            assertEquals(Block(3, blockHash, listOf(txs.last())), it.blocks.last())
            assertEquals(2, it.leader!!.id)
            assertEquals(0, it.tail!!.id)
            assertEquals(0, it.txs.size)


            val setA = it.setA.map { it.id }
            val setB = it.setB.map { it.id }

            assertEquals(listOf(2, 3, 0), setA)
            assertEquals(listOf(1), setB)
        }

    }

    @Test
    fun sunnyDayBufferTwo() {
        val peers = createPeers(N, 2, BUFFER_TIME)
        val txs = listOf(Transaction(random.nextInt()), Transaction(random.nextInt()))
        txs.forEach { tx ->
            peers.first().onTransaction(client, tx)
        }

        peers.forEach {
            it.round()
        }
        val blockHash = txs.map { it.hash }.sum().toLong()


        peers.forEach {
            assertEquals(2, it.blocks.size)
            assertEquals(Block(2, blockHash, txs), it.blocks.last())
            assertEquals(3, it.leader!!.id)
            assertEquals(2, it.tail!!.id)

            assertEquals(0, it.txs.size)


            val setA = it.setA.map { it.id }
            val setB = it.setB.map { it.id }

            assertEquals(listOf(3, 1, 2), setA)
            assertEquals(listOf(0), setB)
        }

    }

    @Test
    fun sunnyDayTimeNoCommit() {
        val peers = createPeers(N, 2, BUFFER_TIME)
        val tx = Transaction(random.nextInt())
        peers.first().onTransaction(client, tx)
        peers.forEach {
            it.round()
        }

        peers.forEach {
            assertEquals(1, it.blocks.size)
            assertEquals(4, it.peers.size)
            assertEquals(3, it.leader!!.id)
            assertEquals(1, it.tail!!.id)

            val setA = it.setA.map { it.id }
            val setB = it.setB.map { it.id }

            assertEquals(listOf(3, 0, 1), setA)
            assertEquals(listOf(2), setB)
        }

    }

    @Test
    fun sunnyDayTimeCommit() {
        val peers = createPeers(N, 2, BUFFER_TIME)
        val tx = Transaction(random.nextInt())
        peers.first().onTransaction(client, tx)


        runBlocking {
            delay(2_000)
        }

        peers.forEach {
            it.round()
        }

        val blockHash = tx.hash.toLong()
        peers.forEach {
            assertEquals(2, it.blocks.size)
            assertEquals(Block(2, blockHash, listOf(tx)), it.blocks.last())
            assertEquals(1, it.leader!!.id)
            assertEquals(0, it.tail!!.id)
            assertEquals(0, it.txs.size)

            val setA = it.setA.map { it.id }
            val setB = it.setB.map { it.id }

            assertEquals(listOf(1, 2, 0), setA)
            assertEquals(listOf(3), setB)
        }

    }

}