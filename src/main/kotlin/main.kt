import java.util.*
import time.*
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.schedule


val BUFFER_TIME = 1.seconds
val F = 1
val N = 3 * F + 1
var ID = 0
const val BUFFER_SIZE = 1
val BLOCK_TIME = 1.seconds

enum class ClientType {
    PEER, CLIENT
}

val logger = LoggerFactory.getLogger("Main")

data class Transaction(val hash: Int, val valid: Boolean = true)

data class Context(val clientType: ClientType, val clientId: Int)

data class Block(val height: Int, val hash: Long, val txs: List<Transaction>)

data class Peer(
    val id: Int,
    val bufferSize: Int,
    val bufferTime: Duration = BUFFER_TIME,
    val blockTime: Duration = BLOCK_TIME,
    var malicious: Boolean = false
) {
    private val logger = LoggerFactory.getLogger("Peer$id")

    var setA = mutableListOf<Peer>()
    var setB = mutableListOf<Peer>()
    var leader: Peer? = null
    var tail: Peer? = null

    private val txHistory = mutableSetOf<Transaction>()
    var txs = mutableListOf<Transaction>()
    var peers = listOf<Peer>()
    val blocks = mutableListOf(Block(1, 0, listOf()))
    private val votes = mutableListOf<Block>()
    private var lastBlockTime = System.currentTimeMillis()

    fun init() {
        orderPeers()

    }

    private fun validateBlock(block: Block): Boolean {
        return block.txs.all { it.valid }
    }

    private fun orderPeers() {
        val rng = Random(blocks.last().hash)
        peers = peers.sortedBy { it.id }.shuffled(rng)
        val (a, b) = split(peers)
        setA = a.toMutableList()
        setB = b.toMutableList()

        leader = setA.first()
        tail = setA.last()
        logger.info("Ordered: Leader ${leader!!.id}, Tail: ${tail!!.id} ")
        println(setA)
        println(setB)


    }

    private fun createBlock(): Block {
        val hash = txs.map { it.hash }.sum().toLong()
        return Block(blocks.last().height + 1, hash, txs.toList())

    }

    private fun propagateTx(tx: Transaction) {
        logger.info("Propagate $tx ")
        val thisContext = Context(ClientType.PEER, this.id)

        peers.forEach {
            it.onTransaction(thisContext, tx)
        }
    }

    fun onTransaction(context: Context, tx: Transaction) {
        logger.info("Got transaction $tx from context $context")

        if (!txHistory.contains(tx)) {
            txHistory.add(tx)
            txs.add(tx.copy())
            propagateTx(tx)
        }
    }

    private fun onCommit(context: Context, block: Block) {
        logger.info("onCommit from $context, block: $block")
        if (context.clientId == tail!!.id) {
            blocks.add(block)
            logger.info("Block added: $block")
            orderPeers()
            txs.clear()
            lastBlockTime = System.currentTimeMillis()
        }
    }

    private fun onBlock(context: Context, block: Block) {
        if (setB.contains(this))
            return

        logger.info("onBlock from $context, block $block")
        val ctx = Context(ClientType.PEER, this.id)
        val isValid = validateBlock(block)

        propagateBlock(block, setB)
        if (tail == this) {
            votes.add(block)
            if (votes.size == 2 * F) {
                // Check validity
                if (votes.distinct().size == 1 && isValid) {
                    //Commit
                    peers.forEach { peer ->
                        logger.info("Commit $block")
                        peer.onCommit(ctx, block)
                    }

                }
            }

        } else {
            if (context.clientId == leader!!.id && isValid) {
                tail!!.onBlock(ctx, block)
            }
        }
    }

    private fun propagateBlock(block: Block, peers: List<Peer>) {
        val ctx = Context(ClientType.PEER, this.id)
        peers.forEach {
            if (it != this && !malicious)
                it.onBlock(ctx, block)
        }
    }

    private fun timePassed() = System.currentTimeMillis() - lastBlockTime > bufferTime.toMillis()

    fun changeLeader() {
        val oldLeader = setA.removeAt(0)
        val newLeader = setA.first()

        setA.add(setB.first())
        setB.removeAt(0)
        setB.add(oldLeader)

        leader = newLeader
    }

    fun startBlockTimer() {
        logger.info("Start block timer")
        val currentBlock = blocks.size
        Timer().schedule(BLOCK_TIME.toMillis()) {
            if (blocks.size == currentBlock) {
                logger.info("Commit failed, changing the leader")
                changeLeader()
            }
        }
    }

    fun round() {
        if (txs.size == bufferSize || (timePassed() && txs.isNotEmpty())) {
            // Create block if leader
            if (leader == this) {
                val block = createBlock()
                propagateBlock(block, setA)
            }
            GlobalScope.launch {
                startBlockTimer()
            }


        }
    }

    suspend fun run() {
        while (true) {
            if (txs.size == BUFFER_SIZE) {
                // Create block if leader
                if (leader == this) {
                    val block = createBlock()
                    propagateBlock(block, setA)
                }

            }
            delay(10)
        }
    }
}


fun main() {
    logger.error("Start")

    val peers = createPeers(N, BUFFER_SIZE, BUFFER_TIME, BLOCK_TIME)

    val ctx = Context(ClientType.CLIENT, 1)

    repeat(2) {
        println("___________________")
        peers.first().onTransaction(ctx, Transaction(Random().nextInt()))
        peers.forEach {
            it.round()
        }
    }

}