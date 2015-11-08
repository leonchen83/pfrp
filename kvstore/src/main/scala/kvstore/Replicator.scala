package kvstore

import akka.actor.{Actor, ActorRef, Props, ReceiveTimeout}
import akka.event.LoggingReceive

import scala.concurrent.duration._

object Replicator {

  case class Replicate(key: String, valueOption: Option[String], id: Long)

  case class Replicated(key: String, id: Long)

  case class Snapshot(key: String, valueOption: Option[String], seq: Long)

  case class SnapshotAck(key: String, seq: Long)

  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor {

  import kvstore.Replicator._

  var acks = Map.empty[Long, (ActorRef, Replicate)]

  var pending: Set[Snapshot] = Set.empty[Snapshot]

  var _seqCounter = 0L

  def nextSeq = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }

  context.setReceiveTimeout(200.millis)

  def receive: Receive = LoggingReceive {
    case Replicate(k, v, id) => {
      val newSeq = nextSeq
      acks += newSeq ->(sender, Replicate(k, v, id))
      pending = pending.filterNot {
        case Snapshot(kk, _, ss) if (kk == k && ss <= newSeq) => true
        case _ => false
      }
      pending += Snapshot(k, v, newSeq)
      replica ! Snapshot(k, v, newSeq)
    }
    case SnapshotAck(key, seq) => {
      pending = pending.filterNot {
        case Snapshot(kk, _, ss) if (kk == key && ss <= seq) => true
        case _ => false
      }
      val request = acks(seq)._2
      acks(seq)._1 ! Replicated(request.key, request.id)
    }
    case ReceiveTimeout => {
      pending.foreach(replica ! _)
    }
  }
}
