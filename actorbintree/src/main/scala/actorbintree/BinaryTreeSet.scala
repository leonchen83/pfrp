/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import akka.actor._
import akka.event.LoggingReceive

import scala.collection.immutable.Queue

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef

    def id: Int

    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection */
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply

  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}


class BinaryTreeSet extends Actor {

  import actorbintree.BinaryTreeNode._
  import actorbintree.BinaryTreeSet._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot

  // optional
  def receive = normal

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  /** Accepts `Operation` and `GC` messages. */
  val normal: Receive = LoggingReceive {
    case operation: Operation => {
      root ! operation
    }
    case GC => {
      val newRoot = createRoot
      root ! CopyTo(newRoot)
      context.become(garbageCollecting(newRoot))
    }
  }

  // optional
  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = LoggingReceive {
    case CopyFinished => {
      root ! PoisonPill
      root = newRoot
      while (pendingQueue.nonEmpty) {
        root ! pendingQueue.head
        pendingQueue = pendingQueue.tail
      }
      context.become(normal)
    }
    case operation: Operation => {
      pendingQueue = pendingQueue :+ operation
    }
    case GC => {
    }
  }

}

object BinaryTreeNode {

  trait Position

  case object Left extends Position

  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)

  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode], elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {

  import actorbintree.BinaryTreeNode._
  import actorbintree.BinaryTreeSet._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = normal

  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = LoggingReceive {
    case Insert(requester, id, value) =>
      if (value == elem) {
        removed = false
        requester ! OperationFinished(id)
      } else if (value > elem) {
        subtrees.get(Right) match {
          case Some(ref: ActorRef) => ref ! Insert(requester, id, value)
          case None => {
            val child: ActorRef = context.actorOf(BinaryTreeNode.props(value, initiallyRemoved = false))
            subtrees += (Right -> child)
            child ! Insert(requester, id, value)
          }
        }
      } else if (value < elem) {
        subtrees.get(Left) match {
          case Some(ref: ActorRef) => ref ! Insert(requester, id, value)
          case None => {
            val child: ActorRef = context.actorOf(BinaryTreeNode.props(value, initiallyRemoved = false))
            subtrees += (Left -> child)
            child ! Insert(requester, id, value)
          }
        }
      }

    case Contains(requester, id, value) =>
      if (value == elem) {
        if (removed) {
          requester ! ContainsResult(id, false)
        } else {
          requester ! ContainsResult(id, true)
        }
      } else if (value > elem) {
        subtrees.get(Right) match {
          case Some(ref: ActorRef) => ref ! Contains(requester, id, value)
          case None => {
            requester ! ContainsResult(id, false)
          }
        }
      } else if (value < elem) {
        subtrees.get(Left) match {
          case Some(ref: ActorRef) => ref ! Contains(requester, id, value)
          case None => {
            requester ! ContainsResult(id, false)
          }
        }
      }

    case Remove(requester, id, value) =>
      if (value == elem) {
        removed = true
        requester ! OperationFinished(id)
      } else if (value > elem) {
        subtrees.get(Right) match {
          case Some(ref: ActorRef) => ref ! Remove(requester, id, value)
          case None =>
            requester ! OperationFinished(id)
        }
      } else if (value < elem) {
        subtrees.get(Left) match {
          case Some(ref: ActorRef) => ref ! Remove(requester, id, value)
          case None => {
            requester ! OperationFinished(id)
          }
        }
      }

    case CopyTo(newRoot) => {
      var count = 0
      subtrees.get(Right) match {
        case Some(ref: ActorRef) => {
          count += 1
          ref ! CopyTo(newRoot)
        }
        case None =>
      }
      subtrees.get(Left) match {
        case Some(ref: ActorRef) => {
          count += 1
          ref ! CopyTo(newRoot)
        }
        case None =>
      }
      if (!removed) {
        newRoot ! Insert(self, elem, elem)
      } else {
        self ! OperationFinished(elem)
      }
      count += 1
      context.become(copying(count))
    }
  }

  var doneSize = 0

  def copying(expected: Int): Receive = {
    case OperationFinished(elem) => self ! CopyFinished
    case CopyFinished => {
      doneSize += 1
      if (doneSize == expected) {
        context.parent ! CopyFinished
      }
    }
  }
}
