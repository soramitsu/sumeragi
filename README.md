# スメラギ

Sumeragi is a Byzantine fault tolerant consensus algorithm for permissioned, peer-to-peer networks that try to reach consensus about some set of data.

## The Basics

- no ordering service; instead, the leader of each round just uses the transactions they have at hand to create a block and the leader changes each round to prevent long-term censorship
https://github.com/soramitsu/sumeragi/blob/master/src/main/kotlin/Peer.kt#L154-L158

- 3*f*+1 validators that are split into two groups, *a* and *b*, of 2*f*+1 and *f* validators each
https://github.com/soramitsu/sumeragi/blob/master/src/main/kotlin/Utils.kt#L3-L7

- 2*f*+1 validators must sign off on a block in order for it to be committed
https://github.com/soramitsu/sumeragi/blob/master/src/main/kotlin/Peer.kt#L114-L117

- the first node in set *a* is called the *leader* (sumeragi) and the last node in set *a* is called the *proxy tail*
https://github.com/soramitsu/sumeragi/blob/master/src/main/kotlin/Peer.kt#L46
https://github.com/soramitsu/sumeragi/blob/master/src/main/kotlin/Peer.kt#L47

- the basic idea is that up to *f* validators can fail and the system should run, so if there are *f* Byzantine faulty nodes, you want them to be in the *b* set as much as possible

- empty blocks are not produced, so to prevent an evil leader from censoring transactions and claiming there are no transactions to create blocks with, everytime a node sends a transaction to the leader, the leader has to submit a signed receipt of receiving it; then, if the leader does not create a block in an orderly amount of time (the *block time*), the submitting peer can use this as proof to convince non-faulty nodes to do a view change and elect a new leader
> Dumitru: There is no need in `signed receipt of receiving`, because a malicious leader can give the receipt and still ignore the transaction later.
https://github.com/soramitsu/sumeragi/blob/master/src/main/kotlin/Peer.kt#L131-L151

- after a node signs off on a block and forwards it to the *proxy tail*, they expect a commit message within a reasonable amount of time (the *commit time*); if there is no commit message in time, the node tries to convince the network to do a view change and elect a new proxy tail
https://github.com/soramitsu/sumeragi/blob/master/src/main/kotlin/Peer.kt#L116
> Dumitru: no commit time check implemented
https://github.com/soramitsu/sumeragi/blob/master/src/main/kotlin/Peer.kt#L116

- once a commit message is received from the proxy tail, all nodes commit the block locally; if a node complains that they never received the commit message, then a peer that has the block will provide that peer with the committed block (note: there is no danger of a leader creating a new block while the network is waiting for a commit message because the next round cannot continue nor a new leader elected until after the current round is committed or leader election takes place)
> Dumitru: No complaint implemented
https://github.com/soramitsu/sumeragi/blob/master/src/main/kotlin/Peer.kt#L80-L89

## The Details

### Network Topology

A network of nodes is assumed, where each node knows the identity of all other nodes on the network. These nodes are called *validators.* We also assume that there are 3*f*+1 validators on the network, where *f* is the number of simultaneous Byzantine faulty nodes that the network can contain and still properly function (albeit, performance will degrade in the presence of a Byzantine faulty node, but this is okay because Hyperledger Iroha is designed to operate in a permissioned environment).

Because the identities of the nodes are known by all and can be proven through digital signatures, it makes sense to overlay a topology on top of the network of nodes in order to provide guarantees that can enable consensus to be reached faster.

For each round (e.g., block), the previous round's (block's) hash is used to determine an ordering over the set of nodes, such that there is a deterministic and canonical order for each block. In this ordering, the first 2*f+1* nodes are grouped into a set called set *a*. Under normal (non-faulty) conditions, consensus for a block is performed by set *a*. The remaining *f* nodes are grouped into a set called set *b*. Under normal conditions, set *b* acts as a passive set of validators to view and receive committed blocks, but otherwise they do not participate in consensus.

### Data Flow: Normal Case

Assume the leader has at least one transaction. The leader creates a block either when the *block timer* goes off or its transaction cache is full. The leader then sends the block to each node in set *a*. Each peer in set *a* then validates and signs the block, and sends it to the proxy tail; after sending it to the proxy tail, each non-leader node in set *a* also sends the block to each node in set *b*, so they can act as observers on the block. When each node in set *a* sends the block to the proxy tail, they set a timer, the *commit timer*, within which time the node expects to get a commit message (or else it will suspect the proxy tail).

The proxy tail should at this point have received the block from at least one of the peers. From the time the first peer contacts the proxy tail with a block proposal, a timer is set, the *voting timer*. Before the *voting timer* is over, the proxy tail expects to get 2*f* votes from the other nodes in set *a*, to which it then will add its vote in order to get 2*f*+1 votes to commit the block.

https://github.com/soramitsu/sumeragi/blob/master/src/test/kotlin/SumeragiTest.kt#L44
https://github.com/soramitsu/sumeragi/blob/master/src/test/kotlin/SumeragiTest.kt#L97
https://github.com/soramitsu/sumeragi/blob/master/src/test/kotlin/SumeragiTest.kt#L129
https://github.com/soramitsu/sumeragi/blob/master/src/test/kotlin/SumeragiTest.kt#L153

### Data Flow: Faulty Cases

**Possible faulty cases related to the leader are:**

- leader ignores all transactions and never creates a block

  - the solution to this is to have other nodes broadcast a transaction across the network and if someone sends a transaction to the leader and it gets ignored, then the leader can be suspected; the suspect message is sent around the network and a new leader is elected
  > Dumitru: No election mechanism implemented, since it implies one more consensus. Seed based random shuffle implemented
  https://github.com/soramitsu/sumeragi/blob/master/src/test/kotlin/SumeragiTest.kt#L185
  
- leader creates a block, but only sends it to a minority of peers, so that 2*f*+1 votes cannot be obtained for consensus

  - the solution is to have a timeout where a new leader will be elected if a block is not agreed upon; the old leader is then moved to set *b*

- leader creates multiple blocks and sends them to different peers, causing the network to not reach consensus about a block

  - the solution is to have a timeout where a new leader will be elected if a block is not agreed upon; the old leader is then moved to set *b*

**Possible faulty cases related to the proxy tail are:**

- proxy tail received some votes, but does not receive enough votes for a block to commit

  - proxy tail is moved one node down and the old proxy tail will forward the block and votes to that 

- proxy tail receives enough votes for a block, but lies and says that they didn't
- proxy tail does not inform other nodes about a block commit (block withholding attack)
- proxy tail does not inform set *b* about a block commit
- proxy tail selectively sends a committed block to some, but not other nodes

**Possible faulty cases related to nodes in set *a* are:**

- a peer may not sign off on a block
- a peer may withhold their signature so as to prevent or slow down consensus
- a peer may make a false accusation against the leader
- a peer may make a false accusation against the proxy tail