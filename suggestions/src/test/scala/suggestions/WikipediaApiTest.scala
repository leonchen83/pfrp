package suggestions


import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import rx.lang.scala._
import suggestions.gui._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}


@RunWith(classOf[JUnitRunner])
class WikipediaApiTest extends FunSuite {

  object mockApi extends WikipediaApi {
    def wikipediaSuggestion(term: String) = Future {
      if (term.head.isLetter) {
        for (suffix <- List(" (Computer Scientist)", " (Footballer)")) yield term + suffix
      } else {
        List(term)
      }
    }

    def wikipediaPage(term: String) = Future {
      "Title: " + term
    }
  }

  import mockApi._

  test("WikipediaApi should make the stream valid using sanitized") {
    val notvalid = Observable.just("erik", "erik meijer", "martin")
    val valid = notvalid.sanitized

    var count = 0
    var completed = false

    val sub = valid.subscribe(
      term => {
        assert(term.forall(_ != ' '))
        count += 1
      },
      t => assert(false, s"stream error $t"),
      () => completed = true
    )
    assert(completed && count == 3, "completed: " + completed + ", event count: " + count)
  }
  test("WikipediaApi should correctly use concatRecovered") {
    val requests = Observable.just(1, 2, 3)
    val remoteComputation = (n: Int) => Observable.just(0 to n: _*)
    val responses = requests concatRecovered remoteComputation
    val sum = responses.foldLeft(0) { (acc, tn) =>
      tn match {
        case Success(n) => acc + n
        case Failure(t) => throw t
      }
    }
    var total = -1
    val sub = sum.subscribe {
      s => total = s
    }
    assert(total == (1 + 1 + 2 + 1 + 2 + 3), s"Sum: $total")
  }

  test("timeout 1") {
    val s = Observable.just(1, 2, 3, 4, 5, 6, 7, 8).timedOut(5)
    var total = 0
    s.subscribe(term => total += term)
    Thread.sleep(100)
    assert(total == (1 + 2 + 3 + 4 + 5 + 6 + 7 + 8))

  }

  test("timeout 2") {
    val s = Observable.interval(300 millis).timedOut(1)
    var count = 0
    s.subscribe(term => count += 1)
    Thread.sleep(1100)
    assert(count == 3)
  }

  test("concatRecovered") {
    val excp = new Exception
    val s = Observable.just(1, 2, 3).concatRecovered(num => Observable.just(num) ++ Observable.error(excp))
    val list1 = new scala.collection.mutable.MutableList[Try[Int]]
    s.subscribe(term => list1 += term)
    Thread.sleep(100)
    val list2 = new scala.collection.mutable.MutableList[Try[Int]]
    list2 += Success(1) += Failure(excp) += Success(2) += Failure(excp) += Success(3) += Failure(excp)
    assert(list1 == list2)
  }

  test("wrap the stream value in a Try using recovered") {
    val excp = new Exception
    val o = (Observable.just(1, 2) ++ Observable.just(4, 5) ++ Observable.error(excp)).recovered
    val list1 = new scala.collection.mutable.MutableList[Try[Int]]
    o.subscribe(term => list1 += term)
    Thread.sleep(100)
    val list2 = new scala.collection.mutable.MutableList[Try[Int]]
    list2 += Success(1) += Success(2) += Success(4) += Success(5) += Failure(excp)
    assert(list1 == list2)
  }

}
