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