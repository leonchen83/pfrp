package nodescala

import nodescala.NodeScala._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.async.Async._
import scala.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

@RunWith(classOf[JUnitRunner])
class NodeScalaSuite extends FunSuite {

  test("A Future should always be completed") {
    val always = Future.always(517)

    assert(Await.result(always, 0 nanos) == 517)
  }

  test("A Future should never be completed") {
    val never = Future.never[Int]

    try {
      Await.result(never, 1 second)
      assert(false)
    } catch {
      case t: TimeoutException => // ok!
    }
  }

  def helper(): Iterator[String] = {
    Iterator.continually("a")
  }

  test("any method works HAPPY") {
    val l = List(Future {
      Thread.sleep(200);
      200
    }, Future {
      Thread.sleep(150);
      150
    }, Future {
      Thread.sleep(199);
      199
    })
    val r = Await.result(Future.any(l), Duration.Inf)
    assert(r == 150)
  }

  test("any method works bad end") {
    val l = List(Future {
      Thread.sleep(9000);
      9
    }, Future {
      Thread.sleep(8000);
      8
    }, Future {
      Thread.sleep(7000);
      7
    }, Future {
      Thread.sleep(6000);
      6
    }, Future {
      Thread.sleep(5000);
      5
    }, Future {
      Thread.sleep(4000);
      4
    }, Future {
      Thread.sleep(3000);
      3
    }, Future {
      Thread.sleep(2000);
      2
    }, Future {
      Thread.sleep(1000);
      1
    })
    val r = Await.result(Future.any(l), Duration.Inf)
    assert(r == 1)
  }

  test("Future.all.failure") {
    val f = Future {
      6
    } :: Future {
      throw new Throwable("error")
    } :: List(1, 2, 3, 4).map(x => Future {
      x
    })

    Future.all(f) onComplete {
      case Success(v) => assert(false)
      case Failure(e) =>
        assert(e.getMessage == "error")
    }
  }

  test("Future.all.success") {
    val f = Future {
      6
    } :: Future {
      7
    } :: List(1, 2, 3, 4).map(x => Future {
      x
    })

    Future.all(f) onComplete {
      case Success(v) =>
        assert(v === List(6, 7, 1, 2, 3, 4))
      case Failure(e) => assert(false)
    }
  }

  test("continueWith should wait for the first future to complete") {
    val delay = Future.delay(1 second)
    val always = (f: Future[Unit]) => 42

    try {
      Await.result(delay.continueWith(always), 500 millis)
      assert(false)
    }
    catch {
      case t: TimeoutException => // ok
    }
  }

  class DummyExchange(val request: Request) extends Exchange {
    @volatile var response = ""
    val loaded = Promise[String]()

    def write(s: String) {
      response += s
    }

    def close() {
      loaded.success(response)
    }
  }

  class DummyListener(val port: Int, val relativePath: String) extends NodeScala.Listener {
    self =>

    @volatile private var started = false
    var handler: Exchange => Unit = null

    def createContext(h: Exchange => Unit) = this.synchronized {
      assert(started, "is server started?")
      handler = h
    }

    def removeContext() = this.synchronized {
      assert(started, "is server started?")
      handler = null
    }

    def start() = self.synchronized {
      started = true
      new Subscription {
        def unsubscribe() = self.synchronized {
          started = false
        }
      }
    }

    def emit(req: Request) = {
      val exchange = new DummyExchange(req)
      if (handler != null) handler(exchange)
      exchange
    }
  }

  class DummyServer(val port: Int) extends NodeScala {
    self =>
    val listeners = mutable.Map[String, DummyListener]()

    def createListener(relativePath: String) = {
      val l = new DummyListener(port, relativePath)
      listeners(relativePath) = l
      l
    }

    def emit(relativePath: String, req: Request) = this.synchronized {
      val l = listeners(relativePath)
      l.emit(req)
    }
  }

  test("Server should be stoppable if receives infinite  response") {
    val dummy = new DummyServer(8191)
    val dummySubscription = dummy.start("/testDir") {
      request => Iterator.continually("a")
    }

    // wait until server is really installed
    Thread.sleep(500)

    val webpage = dummy.emit("/testDir", Map("Any" -> List("thing")))
    try {
      // let's wait some time
      Await.result(webpage.loaded.future, 1 second)
      fail("infinite response ended")
    } catch {
      case e: TimeoutException => println("timeout")
    }

    // stop everything
    dummySubscription.unsubscribe()
    Thread.sleep(500)
    webpage.loaded.future.now // should not get NoSuchElementException
  }

  test("Server should serve requests") {
    val dummy = new DummyServer(8191)
    val dummySubscription = dummy.start("/testDir") {
      request => for (kv <- request.iterator) yield (kv + "\n").toString
    }

    // wait until server is really installed
    Thread.sleep(500)

    def test(req: Request) {
      val webpage = dummy.emit("/testDir", req)
      val content = Await.result(webpage.loaded.future, 1 second)
      val expected = (for (kv <- req.iterator) yield (kv + "\n").toString).mkString
      assert(content == expected, s"'$content' vs. '$expected'")
    }

    test(immutable.Map("StrangeRequest" -> List("Does it work?")))
    test(immutable.Map("StrangeRequest" -> List("It works!")))
    test(immutable.Map("WorksForThree" -> List("Always works. Trust me.")))

    dummySubscription.unsubscribe()
  }

}





