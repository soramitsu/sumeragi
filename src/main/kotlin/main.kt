import java.util.*
import time.*
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory


val BLOCK_TIME = 1.seconds
val F = 1
val N = 3 * F + 1
var ID = 0
val BUFFER_SIZE = 1

enum class ClientType {
    PEER, CLIENT
}

data class Transaction(val hash: Int, val valid: Boolean = true)

data class Context(val clientType: ClientType, val clientId: Int)

data class Block(val height: Int, val hash: Long, val txs: List<Transaction>)

data class Peer(var id: Int = 0, val malicious: Boolean = false) {
    private val logger : Logger



    var setA = listOf<Peer>()
    var setB = listOf<Peer>()
    var leader: Peer? = null
    var tail: Peer? = null

    val txHistory = mutableSetOf<Transaction>()
    var txs = mutableListOf<Transaction>()
    var peers = listOf<Peer>()
    val blocks = mutableListOf(Block(0, 0, listOf()))
    val votes = mutableListOf<Block>()

    init {
        this.id = ID
        ID++
        logger =  LoggerFactory.getLogger("Peer$id")

    }

    fun init() {
        orderPeers()
    }

    fun validateBlock(block: Block): Boolean {
        return block.txs.all { it.valid }
    }

    fun orderPeers() {
        val rng = Random(blocks.last().hash)
        peers = peers.sortedBy { it.id }.shuffled(rng)
        val (a, b) = split(peers)
        setA = a
        setB = b

        leader = setA.first()
        tail = setA.last()
        logger.info("Ordered: Leader ${leader!!.id}, Tail: ${tail!!.id} ")

    }

    fun createBlock(): Block {
        return Block(blocks.last().height + 1, Random().nextLong(), txs)

    }

    fun propagateTx(tx: Transaction) {
        logger.info("Propagate $tx ")
        peers.forEach {
            val thisContext = Context(ClientType.PEER, this.id)
            it.onTransaction(thisContext, tx)
        }
    }

    fun onTransaction(context: Context, tx: Transaction) {
//        logger.info("Got transaction $tx from context $context")

        if (context.clientType == ClientType.CLIENT || !txHistory.contains(tx)) {
            txHistory.add(tx)
            txs.add(tx)
            propagateTx(tx)
        }
    }

    fun onCommit(context: Context, block: Block) {
        logger.info("onCommit from $context")
        if (context.clientId == tail!!.id) {
            blocks.add(block)
            logger.info("Block added: $block")
            orderPeers()
            txs.clear()
        }
    }

    fun onBlock(context: Context, block: Block) {
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

    fun propagateBlock(block: Block, peers: List<Peer>) {
        val ctx = Context(ClientType.PEER, this.id)
        peers.forEach {
            if (it != this)
                it.onBlock(ctx, block)
        }
    }

    fun round() {
        if (txs.size == BUFFER_SIZE) {
            // Create block if leader
            if (leader == this) {
                val block = createBlock()
                propagateBlock(block, setA)
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

fun split(peers: List<Peer>): Pair<List<Peer>, List<Peer>> {
    val n = peers.size
    val f = (n - 1) / 3
    return Pair(peers.subList(0, 2 * f + 1), peers.subList(2 * f + 1, peers.size))
}

fun main() {
    val peers = Array(N) { Peer() }.toList()
    peers.forEach {
        it.peers = peers
        it.init()
    }
    val ctx = Context(ClientType.CLIENT, 1)

    repeat(6) {

        peers.first().onTransaction(ctx, Transaction(2))
        peers.forEach {
            it.round()
        }
    }

}