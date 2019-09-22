import java.time.Duration

fun split(peers: List<Peer>): Pair<List<Peer>, List<Peer>> {
    val n = peers.size
    val f = (n - 1) / 3
    return Pair(peers.subList(0, 2 * f + 1), peers.subList(2 * f + 1, peers.size))
}

fun createPeers(n: Int, bufferSize: Int, bufferTime: Duration, blockTime: Duration) : List<Peer>{
    val peers = (0 until n).map { Peer(it, bufferSize, bufferTime, blockTime) }
    peers.forEach {
        it.peers = peers
        it.init()
    }

    return peers

}