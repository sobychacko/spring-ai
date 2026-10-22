# Spring AI Kafka session repository

A `SessionRepository` for `spring-ai-session` built on Kafka Streams and Spring for Apache Kafka.
It works with `DefaultSessionService` and the session advisors unchanged, and passes the same contract suite as the JDBC store.
It lets a team that already runs Kafka keep agent memory there, with no extra database.

Status: an architectural spike, tested on an embedded single broker.
It shows the session semantics on Kafka; production hardening, the Boot starter and multi-broker testing are still to come.

## How it works

Every write rule of the session contract is per session: create-if-absent, the version check, idempotent append, delete.
Calls are requests on a topic keyed by session id, so all calls for one session reach the one Streams task that owns its partition.
That task handles its requests one at a time, which gives the guarantee a row lock gives JDBC.

```
 any instance         Kafka          partition owner
+------------+                     +---------------+
|Session-    |  1   +----------+   | 2 Streams task|
|Service     |----->| requests |-->|   checks and  |
|  |         |      +----------+   |   updates its |
|  v         |                     |   stores      |
|KafkaSession|      +----------+   | 3 changes go  |
|Repository  |      |changelogs|<--|   to changelog|
|            |  4   +----------+   | 4 reply, same |
|            |<-----| replies  |<--|   transaction |
+------------+      +----------+   +---------------+
```

1. The repository sends the call with `ReplyingKafkaTemplate` to the session's partition.
2. The owner task checks the rules and updates its RocksDB stores.
3. Every store change also goes to a compacted changelog topic, which restores the store and erases deletes.
4. The reply commits in the same exactly-once transaction as the changes; callers read replies `read_committed`.

Reads take the same path, so a read is ordered behind every earlier write to the session.
Two calls span all sessions, listing a user's sessions and the expiry sweep: they go to every partition in parallel and the results are merged.

Every application instance is both a client and the owner of some partitions, assigned by the Streams group protocol.
Which instance serves an HTTP request doesn't matter: the session's calls always reach its partition's owner, and no conversation state is kept on the caller.

### Stores

| Store | Key | Value |
|---|---|---|
| `sessions` | session id | the session, its event version, the next sequence number, the newest compaction boundary |
| `events` | length-prefixed session id + zero-padded sequence number | the event, encoded with `SessionEventCodec` |
| `event-ids` | session id + event id | the sequence number |

A session's history is a `prefixScan` over `events`, in append order.
An active-view read starts at the newest compaction boundary and adds the events it names, as JDBC does.
Event ids are scoped to their session, which the contract allows.

## Usage

```java
KafkaSessionRepository repository = KafkaSessionRepository.builder()
    .bootstrapServers("broker:9092")
    .topicPrefix("my-app.ai-session")   // my-app.ai-session.requests / .replies
    .partitions(12)
    .build();
repository.start();                     // SmartLifecycle: a Spring context starts it

SessionService sessions = DefaultSessionService.builder()
    .sessionRepository(repository)
    .build();
```

| Option | Default | Meaning |
|---|---|---|
| `bootstrapServers` | `localhost:9092` | Kafka brokers |
| `topicPrefix` | `spring-ai-session` | Prefix of the requests and replies topics |
| `applicationId` | the topic prefix | Streams application id; names the changelog topics |
| `partitions` | 12 | Partitions of the topics, the most instances that share the work; fixed once created |
| `replicationFactor` | 3 | Of every topic and changelog |
| `standbyReplicas` | 1 | Warm copies of each store for fast takeover |
| `stateDirectory` | Streams default | Where RocksDB keeps the stores |
| `operationTimeout` | 30 s | How long a call waits for its reply |
| `contentRetention` | 1 day | Retention of the requests and replies topics, and segment interval and compaction lag of every topic |
| `maxMessageBytes` | 8 MB | Largest request, reply or changelog record |
| `kafkaProperties` | none | Client properties such as security; the store's own settings win |

`isHealthy()` turns false when this instance's Streams engine has stopped after a failure.
Calls keep working, because its partitions move to the other instances, but the instance should be restarted.

## Guarantees

- **Atomic state and reply.** A caller never sees an answer whose store changes were rolled back.
- **Recovery.** Stores restore from their changelogs: only the tail on the same disk, the whole compacted changelog on an empty one.
- **Erasure, eventually.** A delete writes tombstones that compaction applies, and the requests and replies topics expire content by retention. Segment and compaction-lag settings use `contentRetention`, but erasure stays asynchronous: it waits for the segment to close, which needs a new record on the partition, and then for the log cleaner, which runs in the background.
- **Scale.** Data and work are split by partition across instances.

## Limits

- **Latency.** Two Kafka hops and a transaction commit per call: about 2 ms per call and 5 ms per turn on an embedded broker; not measured on a real cluster.
- **Record size.** A reply is about 2.4 times the text it carries, so a full-history read hits the 8 MB limit at roughly 3,500 events of 1 KB. Oversized requests and replies fail with a clear error and the store keeps serving.
- **Partition count.** Fixed at creation; a mismatch stops startup, as does an existing topic or changelog with a smaller record limit or the wrong cleanup policy.
- **Replies go to every instance.** Each instance reads all replies and keeps its own.
- **No JDBC transaction**, like any non-JDBC store.

## Operations

The platform team sees the usual Kafka Streams surface:

- a state directory per instance (a StatefulSet on Kubernetes, or a changelog restore on restart);
- ACLs for the application id, the changelog topics, the consumer group and the transactional ids;
- rebalances on deploys, consumer lag and restore time, and RocksDB memory outside the heap.

The store's topic and record formats are internal; nothing else should write to them.

## Tests

| Suite | What it covers |
|---|---|
| `KafkaSessionRepositoryTests` | The shared contract suite, on 4 partitions |
| `KafkaSessionRepositoryFailureTests` | An instance failing mid-request, malformed requests, client settings and topics that don't fit, a failed start |
| `KafkaSessionRepositoryInstancesTests` | Several instances: races, cross-partition calls, restarts, startup |
| `SessionMemoryAdvisorKafkaTests` | The session advisor's hooks across simulated tool rounds, with constructed model responses |
| `KafkaSessionRepositoryErasureTests` | A deleted session's text leaving every topic |
| `KafkaSessionRepositoryLimitsTests` | Record size limits |
| `KafkaSessionRepositoryLatencyTests` | Call latency and reply size; run with `-Dkafka.session.latency=true` |

All run on an embedded single-node KRaft broker; no Docker is needed.
They don't cover standby takeover, scale-out under load, broker failures, process kills, or production latency.

```shell
./mvnw -pl session-repositories/spring-ai-session-kafka -am test
```

## Design notes

Rules the code keeps, each learned the hard way:

- A record the producer refuses fails the task, and the request with it on every restart. Every request and reply is measured as Kafka will send it, key and headers included, and a write replies with no more than a flag, a count or a timestamp.
- A request that can't be read is rejected while it is parsed, so the task skips it instead of failing on it.
- The repository picks each session's partition itself, so producer partitioner settings can't split a session.
- Creating a topic never changes an existing one, so startup checks the existing topics' partition count, record limit and cleanup policy.
- A stopped Streams engine doesn't fail the instance's calls: the other instances still answer them.

## Still open

- An index for listing a user's sessions (today each partition scans its sessions).
- Bounded reads: a `lastN` window and the expiry sweep still read a partition's whole candidate set; both need large-data tests.
- A shared RocksDB memory cap; today each store has default caches, which grow with partitions and standbys.
- Smaller replies: the codec stores each message's text twice.
- A versioning policy and compatibility fixtures for the store and wire formats.
- Standby takeover, scale-out under load, and a multi-broker Testcontainers run.
- Taking the application's Kafka factories, and a Boot starter with a health indicator on `isHealthy()`.
- The `SessionRepository` Javadoc says event ids are unique across sessions, while the contract test allows ids scoped per session, as this store does.
