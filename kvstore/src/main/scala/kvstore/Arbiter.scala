package kvstore

import akka.actor.{Actor, ActorRef, Terminated}

object Arbiter {

  case object Join

  case object JoinedPrimary

  case object JoinedSecondary

  /**
   * This message contains all replicas currently known to the arbiter, including the primary.
   */
  case class Replicas(replicas: Set[ActorRef])

}

class Arbiter extends Actor {

  import kvstore.Arbiter._

  var leader: Option[ActorRef] = None
  var replicas = Set.empty[ActorRef]

  def receive = {
    case Join =>
      if (leader.isEmpty) {
        leader = Some(sender)
        replicas += sender
        sender ! JoinedPrimary
      } else {
        replicas += sender
        sender ! JoinedSecondary
      }
      context.watch(sender)
      leader foreach (_ ! Replicas(replicas))
  }

}