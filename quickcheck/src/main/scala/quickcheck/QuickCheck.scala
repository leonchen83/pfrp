package quickcheck

import common._

import org.scalacheck._
import Arbitrary._
import Gen._
import Prop._

abstract class QuickCheckHeap extends Properties("Heap") with IntHeap {

  property("min1") = forAll { (e: A) =>
    val h = insert(e, empty)
    findMin(h) == e
  }

  property("gen1") = forAll { (h: H) =>
    val m = if (isEmpty(h)) 0 else findMin(h)
    findMin(insert(m, h)) == m
  }

  property("findMin") = forAll { (e1: A, e2: A) =>
    val h = insert(e1, empty)
    findMin(insert(e2, h)) == scala.math.min(e1, e2)
  }

  property("findMin") = forAll { (e: A) =>
    val h = insert(e, empty)
    isEmpty(deleteMin(h)) == true
  }

  property("deleteMin") = forAll { (e: H) =>
    if (isEmpty(e)) {
      true
    } else {
      val list: List[A] = sort(e)
      list == list.sorted
    }
  }

  property("meld") = forAll { (e1: H, e2: H) =>
    val h = meld(e1, e2)
    if (isEmpty(e1) && isEmpty(e2)) {
      true
    } else if (isEmpty(e1)) {
      findMin(h) == findMin(e2)
    } else if (isEmpty(e2)) {
      findMin(h) == findMin(e1)
    } else {
      findMin(h) == findMin(e1) || findMin(h) == findMin(e2)
    }
  }

  property("meld") = forAll { (e1: H, e2: H) =>
    val h = meld(e1, e2)
    sort(h) == (sort(e1) ::: sort(e2)).sorted
  }

  def sort(h: H): List[A] = {
    if (isEmpty(h)) {
      Nil
    } else {
      findMin(h) :: sort(deleteMin(h))
    }
  }

  lazy val subGenHeap: Gen[H] = {
    for {
      v <- arbitrary[A]
      h <- frequency((1, const(empty)), (9, subGenHeap))
    } yield insert(v, h)
  }

  lazy val genHeap: Gen[H] = oneOf(const(empty), subGenHeap)

  implicit lazy val arbHeap: Arbitrary[H] = Arbitrary(genHeap)

}
