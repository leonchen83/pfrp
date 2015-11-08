package kvstore

import akka.actor._
import akka.event.LoggingReceive
import kvstore.Arbiter._
import kvstore.Persistence.{Persist, Persisted}
import kvstore.Replica._
import kvstore.Replicator.{Replicate, Replicated}

import scala.concurrent.duration._

object Replica {

  sealed trait Operation {
    def key: String

    def id: Long
  }

  case class Insert(key: String, value: String, id: Long) extends Operation

  case class Remove(key: String, id: Long) extends Operation

  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply

  case class OperationAck(id: Long) extends OperationReply

  case class OperationFailed(id: Long) extends OperationReply

  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {

  import kvstore.Persistence._
  import kvstore.Replicator._

  override def preStart(): Unit = {
    arbiter ! Join
  }

  override def postStop(): Unit = {
    persistence ! PoisonPill
  }

  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]

  var persistence = context.actorOf(persistenceProps, "persistence")

  def persist = context.actorOf(Props(new PrimaryPersist(persistence)))

  def primaryReplica = replicators.map(e => context.actorOf(Props(new PrimaryReplicate(e))))

  def receive = LoggingReceive {
    case JoinedPrimary => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  context.setReceiveTimeout(1.seconds)

  val leader: Receive = {
    LoggingReceive {
      case Replicas(replicas) => receiveReplicas(replicas)
      case Insert(k, v, id) => operation(k, Some(v), id, () => kv += k -> v)
      case Remove(k, id) => operation(k, None, id, () => kv -= k)
      case Get(k, id) => sender ! GetResult(k, kv.get(k), id)
    }
  }

  def operation(k: String, v: Option[String], id: Long, callback: () => Unit): Unit = {
    val client = sender
    var acks = Set.empty[ActorRef]
    persist ! Persist(k, v, id)
    primaryReplica.foreach(_ ! Replicate(k, v, id))
    context.become(LoggingReceive {
      case Replicas(replicas) => receiveReplicas(replicas)
      case ref: ActorRef => {
        acks += ref
        sender ! PoisonPill
        if (!(replicators + persistence).exists(!acks.contains(_))) {
          callback()
          client ! OperationAck(id)
          context.unbecome()
        }
      }
      case ReceiveTimeout => {
        if (!(replicators + persistence).exists(!acks.contains(_))) {
          callback()
          client ! OperationAck(id)
        } else {
          client ! OperationFailed(id)
        }
        context.unbecome()
      }
      case Get(k, id) => client ! GetResult(k, kv.get(k), id)
    }, false)
  }

  def receiveReplicas(replicas: Set[ActorRef]): Unit = {
    val joined = (replicas - self) -- secondaries.keySet
    val leaved = secondaries.keySet -- (replicas - self)
    joined.foreach(j => {
      val replicator: ActorRef = context.actorOf(Replicator.props(j))
      kv.foreach(e => replicator ! Replicate(e._1, Some(e._2), -1L))
      secondaries += (j -> replicator)
      replicators += replicator
    })
    leaved.foreach(l => {
      secondaries.get(l) match {
        case Some(v) =>
          replicators -= v
          v ! PoisonPill
        case None => //nothing to do
      }
      secondaries -= l
    })
  }

  var expectedSeq = 0L

  val replica: Receive = {
    case Snapshot(k, v, seq) => {
      if (seq > expectedSeq) {

      } else if (seq < expectedSeq) {
        sender ! SnapshotAck(k, seq)
      } else {
        v match {
          case Some(value) => kv += (k -> value)
          case None => kv -= k
        }
        val replicator = sender
        persist ! Persist(k, v, -1L)
        context.become(LoggingReceive {
          case ref: ActorRef => {
            expectedSeq += 1
            replicator ! SnapshotAck(k, seq)
            sender ! PoisonPill
            context.unbecome()
          }
          case Get(k, id) => sender ! GetResult(k, kv.get(k), id)
        }, false)
      }
    }
    case Get(k, id) => sender ! GetResult(k, kv.get(k), id)
  }
}

class PrimaryPersist(persistence: ActorRef) extends Actor {
  context.setReceiveTimeout(100.millis)

  def receive: Receive = LoggingReceive {
    case Persist(k, v, id) => {
      persistence ! Persist(k, v, id)
      context.become({
        LoggingReceive {
          case Persisted(k, id) => context.parent ! persistence
          case ReceiveTimeout => persistence ! Persist(k, v, id)
        }
      })
    }
  }
}

class PrimaryReplicate(replicator: ActorRef) extends Actor {
  def receive: Receive = LoggingReceive {
    case Replicate(k, v, id) => {
      replicator ! Replicate(k, v, id)
      context.watch(replicator)
      context.become({
        LoggingReceive {
          case Replicated(k, id) =>
            context.unwatch(replicator)
            context.parent ! replicator
          case Terminated(dead) => context.parent ! dead
        }
      })
    }
  }
}