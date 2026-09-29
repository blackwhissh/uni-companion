from pathlib import Path

from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import PageBreak, Paragraph, SimpleDocTemplate, Spacer


OUTPUT = Path(__file__).resolve().parents[1] / "test-fixtures" / "quality"

DOCUMENTS = {
    "01-raft-consensus.pdf": (
        "Distributed Systems: Consensus and Raft",
        [
            (
                "1. Consensus goals and terminology",
                [
                    "Consensus allows a group of processes to agree on one value despite failures. "
                    "A correct consensus protocol provides agreement (no two correct processes decide "
                    "differently), validity (the decided value was proposed), and termination (every "
                    "correct process eventually decides).",
                    "Safety properties state that nothing bad happens; they must hold even during "
                    "network delays. Liveness properties state that something good eventually happens; "
                    "they generally require timing or availability assumptions.",
                    "Crash faults stop a process but do not make it lie. Byzantine faults permit arbitrary "
                    "behavior and require stronger protocols than Raft.",
                ],
            ),
            (
                "2. Raft roles, terms, and communication",
                [
                    "Raft servers are followers, candidates, or leaders. Normal operation has one leader; "
                    "clients send commands to that leader. Followers are passive and respond to requests.",
                    "Time is divided into monotonically increasing terms. Each term begins with an election. "
                    "A term has at most one leader, but it may end without electing one.",
                    "RequestVote RPCs are used for elections. AppendEntries RPCs replicate log entries and "
                    "also serve as heartbeats.",
                ],
            ),
            (
                "3. Leader election",
                [
                    "A follower becomes a candidate after its randomized election timeout expires without "
                    "a valid leader message. It increments its term, votes for itself, resets its timer, and "
                    "requests votes from the other servers.",
                    "A candidate becomes leader after receiving votes from a majority. Each server grants at "
                    "most one vote per term. Randomized election timeouts reduce repeated split votes.",
                    "A voter grants its vote only when the candidate's log is at least as up to date as its "
                    "own. Raft compares last-log term first, then last-log index.",
                ],
            ),
            (
                "4. Log replication and commitment",
                [
                    "The leader appends a client command to its log, sends AppendEntries to followers, and "
                    "retries until followers contain matching entries. An entry is committed after it is "
                    "stored on a majority and the leader's commitment rule is satisfied.",
                    "Raft leaders commit entries from their current term by counting replicas. Once such an "
                    "entry is committed, preceding entries are committed indirectly, including entries from "
                    "older terms.",
                    "Servers apply committed entries to the state machine in log order. The commit index is "
                    "monotonic and never decreases.",
                ],
            ),
            (
                "5. Log matching and conflict repair",
                [
                    "The Log Matching Property says that if two logs contain an entry with the same index "
                    "and term, the entries are identical and all preceding entries are identical.",
                    "AppendEntries includes the previous log index and term. A follower rejects the request "
                    "when that previous entry does not match. The leader then backs up its nextIndex and retries.",
                    "When a matching prefix is found, conflicting follower entries are deleted and replaced by "
                    "the leader's entries. A leader never overwrites or deletes its own entries.",
                ],
            ),
            (
                "6. Raft safety properties",
                [
                    "Election Safety: at most one leader can be elected in a term. Leader Append-Only: a leader "
                    "never changes existing entries in its own log. Log Matching: equal index and term imply "
                    "equal prefixes.",
                    "Leader Completeness: an entry committed in a term appears in the logs of leaders for all "
                    "higher terms. State Machine Safety: no two servers apply different commands at the same "
                    "log index.",
                    "The voting up-to-date restriction is essential for Leader Completeness because a candidate "
                    "missing a committed entry cannot collect a majority that includes enough replicas of it.",
                ],
            ),
            (
                "7. Membership changes and snapshots",
                [
                    "Changing directly from one server set to another can create two independent majorities. "
                    "Raft avoids this using joint consensus, where decisions require majorities of both the old "
                    "and new configurations during the transition.",
                    "Snapshots compact the prefix of the replicated log after its effects are represented in "
                    "the state machine snapshot. The snapshot records the last included index and term.",
                    "A leader sends InstallSnapshot when a follower is too far behind for retained log entries "
                    "to repair it efficiently.",
                ],
            ),
            (
                "8. Worked election and replication example",
                [
                    "In a five-server cluster, three votes form a majority. If server A receives votes from A, "
                    "B, and C in term 9, it becomes leader even if D and E are unreachable.",
                    "For a term-9 command to be committed by counting replicas, the entry must be stored on at "
                    "least three servers. Losing two servers still leaves a majority available; losing three "
                    "prevents progress but does not permit conflicting committed values.",
                    "Exam distinction: majority replication determines commitment, while application occurs "
                    "after commitment and in log order. Replicated, committed, and applied are different states.",
                ],
            ),
        ],
    ),
    "02-replication-consistency.pdf": (
        "Distributed Systems: Replication and Consistency",
        [
            (
                "1. Why systems replicate data",
                [
                    "Replication places copies of data on multiple nodes to improve availability, fault "
                    "tolerance, and read scalability. It introduces coordination cost and the possibility that "
                    "replicas temporarily disagree.",
                    "Synchronous replication waits for required replicas before acknowledging a write, giving "
                    "stronger guarantees at higher latency. Asynchronous replication acknowledges earlier but "
                    "can lose recent writes if the primary fails.",
                ],
            ),
            (
                "2. Strong consistency models",
                [
                    "Linearizability makes every operation appear to occur atomically between invocation and "
                    "response, while respecting real-time order. Once a write completes, later reads must see it "
                    "or a newer value.",
                    "Sequential consistency preserves each process's program order and provides one common "
                    "operation order, but that common order need not respect real-time order.",
                    "Linearizability is compositional: independently linearizable objects compose into a "
                    "linearizable system.",
                ],
            ),
            (
                "3. Client-centric and eventual consistency",
                [
                    "Eventual consistency guarantees that replicas converge if no new updates occur. It does "
                    "not specify how quickly convergence happens or which intermediate values a read observes.",
                    "Read-your-writes ensures a client sees its own completed updates. Monotonic reads prevent a "
                    "client from observing an older version after observing a newer one.",
                    "Session guarantees are often implemented with sticky sessions or version metadata carried "
                    "between requests.",
                ],
            ),
            (
                "4. Quorum replication",
                [
                    "For N replicas, a read quorum R and write quorum W overlap when R + W > N. Two write "
                    "quorums overlap when W > N/2. These inequalities make at least one replica common to the "
                    "relevant operations.",
                    "With N = 5, R = 3 and W = 3 satisfy both overlap conditions. R = 1 and W = 5 favors cheap "
                    "reads, while R = 5 and W = 1 does not guarantee overlapping writes.",
                    "Quorum overlap alone does not automatically provide linearizability; version selection, "
                    "failure handling, and concurrent-write resolution must also be correct.",
                ],
            ),
            (
                "5. Primary-backup replication",
                [
                    "A primary orders writes and forwards them to backups. A failover protocol chooses a new "
                    "primary when the old one becomes unavailable.",
                    "Split brain occurs when multiple nodes believe they are primary and accept conflicting "
                    "writes. Leases, fencing tokens, and quorum-based leadership prevent stale primaries from "
                    "continuing to modify protected resources.",
                    "A fencing token is monotonically increasing; storage rejects operations carrying a token "
                    "older than the newest token it has observed.",
                ],
            ),
            (
                "6. Conflict detection and repair",
                [
                    "Version vectors track causal update histories. Two versions are concurrent when neither "
                    "version vector dominates the other.",
                    "Read repair updates stale replicas discovered during reads. Anti-entropy compares replicas "
                    "in the background, often using Merkle trees to locate differing key ranges efficiently.",
                    "Last-write-wins is simple but depends on timestamp assumptions and may discard concurrent "
                    "updates. Application-specific merge functions can preserve more information.",
                ],
            ),
            (
                "7. CAP and network partitions",
                [
                    "During a network partition, a replicated system cannot guarantee both linearizable "
                    "consistency and availability for every request. A CP design may reject requests that cannot "
                    "reach a quorum; an AP design may accept operations that require later conflict resolution.",
                    "CAP concerns behavior during partitions, not a permanent three-way design menu. When the "
                    "network is healthy, systems still make latency, consistency, and durability tradeoffs.",
                ],
            ),
            (
                "8. Comparing guarantees",
                [
                    "Linearizability is appropriate when real-time ordering matters, such as lock ownership or "
                    "unique allocation. Eventual consistency is suitable when temporary divergence is acceptable "
                    "and availability or low latency is more important.",
                    "Read-your-writes is weaker than linearizability because it protects one client's session, "
                    "not a single real-time order for all clients.",
                    "Exam method: identify the required observable guarantee first, then choose coordination and "
                    "replication mechanisms capable of enforcing it under the stated failures.",
                ],
            ),
        ],
    ),
    "03-sharding-transactions.pdf": (
        "Distributed Systems: Sharding and Transactions",
        [
            (
                "1. Sharding fundamentals",
                [
                    "Sharding partitions a dataset so different nodes own different subsets. A shard key maps "
                    "each record to a shard and strongly influences load balance and query efficiency.",
                    "Range partitioning supports range scans but can create hot shards for sequential keys. Hash "
                    "partitioning spreads keys more evenly but scatters range queries.",
                    "Resharding moves ownership and data while requests continue. Consistent hashing reduces the "
                    "fraction of keys moved when nodes join or leave.",
                ],
            ),
            (
                "2. Distributed transaction goals",
                [
                    "A distributed transaction spans multiple resource managers but should preserve atomicity: "
                    "all participants commit or all abort.",
                    "Atomic commit is different from consensus. Atomic commit decides commit only if every "
                    "participant can commit; consensus chooses one proposed value despite failures.",
                ],
            ),
            (
                "3. Two-phase commit: prepare",
                [
                    "In phase one, the coordinator sends PREPARE. Each participant validates the transaction, "
                    "forces required state to durable storage, acquires or retains necessary locks, and votes YES "
                    "or NO.",
                    "A YES vote is a promise that the participant can commit later even after recovery. A NO vote "
                    "allows the coordinator to decide ABORT immediately.",
                ],
            ),
            (
                "4. Two-phase commit: decision and blocking",
                [
                    "In phase two, the coordinator records the global decision and sends COMMIT if every vote was "
                    "YES; otherwise it sends ABORT. Participants durably record and acknowledge the decision.",
                    "Two-phase commit can block when the coordinator fails after participants vote YES but before "
                    "they learn the decision. Prepared participants cannot safely decide alone because either "
                    "global decision may already exist.",
                    "Coordinator replication improves availability but does not change the core atomic-commit rule.",
                ],
            ),
            (
                "5. Three-phase commit and timing assumptions",
                [
                    "Three-phase commit adds a pre-commit phase to reduce uncertainty, but non-blocking behavior "
                    "depends on bounded-delay and failure-detection assumptions that do not hold in a fully "
                    "asynchronous network with partitions.",
                    "Therefore 3PC is not a universal replacement for 2PC in practical partitionable systems.",
                ],
            ),
            (
                "6. Sagas",
                [
                    "A saga decomposes a long transaction into local transactions. If a later step fails, "
                    "compensating actions semantically undo earlier completed steps.",
                    "Compensation is not necessarily a physical rollback. Refunding a payment is a new business "
                    "operation and may remain visible in audit history.",
                    "Sagas improve autonomy and availability but expose intermediate states and require explicit "
                    "handling of retries, idempotency, and compensation failures.",
                ],
            ),
            (
                "7. Isolation and anomalies",
                [
                    "Dirty reads observe uncommitted data. Non-repeatable reads observe a changed committed value "
                    "within one transaction. Phantoms occur when repeating a predicate query returns a different "
                    "set of rows.",
                    "Write skew occurs when concurrent transactions read overlapping data and write different "
                    "items, jointly violating an invariant. Snapshot isolation prevents many anomalies but can "
                    "allow write skew.",
                    "Serializable execution is equivalent to some serial order and prevents write skew when the "
                    "invariant is correctly represented.",
                ],
            ),
            (
                "8. MVCC and an exam-oriented comparison",
                [
                    "Multi-version concurrency control stores multiple versions so readers can use a consistent "
                    "snapshot without blocking writers. Garbage collection eventually removes versions no active "
                    "snapshot can require.",
                    "Use 2PC when atomic commitment across participants is mandatory and blocking risk is accepted. "
                    "Use a saga when business-level compensation and temporary intermediate states are acceptable.",
                    "Sharding decides data placement; replication decides how copies are maintained; transaction "
                    "protocols decide how multi-operation updates preserve atomicity and isolation.",
                ],
            ),
        ],
    ),
}


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("Helvetica", 8)
    canvas.setFillColorRGB(0.35, 0.4, 0.43)
    canvas.drawString(18 * mm, 12 * mm, "Uni Companion quality-test fixture")
    canvas.drawRightString(192 * mm, 12 * mm, f"Page {doc.page}")
    canvas.restoreState()


def generate(path: Path, title: str, sections):
    styles = getSampleStyleSheet()
    title_style = ParagraphStyle(
        "FixtureTitle",
        parent=styles["Title"],
        alignment=TA_CENTER,
        fontName="Helvetica-Bold",
        fontSize=20,
        leading=25,
        spaceAfter=12,
    )
    heading_style = ParagraphStyle(
        "FixtureHeading",
        parent=styles["Heading1"],
        fontName="Helvetica-Bold",
        fontSize=16,
        leading=20,
        textColor="#07343c",
        spaceAfter=10,
    )
    body_style = ParagraphStyle(
        "FixtureBody",
        parent=styles["BodyText"],
        fontName="Helvetica",
        fontSize=11,
        leading=16,
        spaceAfter=10,
    )
    document = SimpleDocTemplate(
        str(path),
        pagesize=A4,
        leftMargin=20 * mm,
        rightMargin=20 * mm,
        topMargin=20 * mm,
        bottomMargin=20 * mm,
        title=title,
        author="Uni Companion Quality Test",
    )
    story = []
    for index, (heading, paragraphs) in enumerate(sections):
        if index == 0:
            story.extend(
                [
                    Paragraph(title, title_style),
                    Paragraph("CISS 2026WS - Exam-focused course notes", styles["Heading2"]),
                    Spacer(1, 8 * mm),
                ]
            )
        story.append(Paragraph(heading, heading_style))
        for paragraph in paragraphs:
            story.append(Paragraph(paragraph, body_style))
        story.append(Paragraph(
            "<b>Review checkpoint:</b> distinguish the guarantees, mechanisms, and failure assumptions "
            "introduced on this page.",
            body_style,
        ))
        if index < len(sections) - 1:
            story.append(PageBreak())
    document.build(story, onFirstPage=footer, onLaterPages=footer)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for filename, (title, sections) in DOCUMENTS.items():
        generate(OUTPUT / filename, title, sections)
        print(OUTPUT / filename)


if __name__ == "__main__":
    main()
