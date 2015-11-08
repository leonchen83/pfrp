/**
 * Created by leon on 15-5-12.
 */
object Test {
  def main(args: Array[String]): Unit = {
    test({
      println("before")
      "hello world"
    })
  }

  var s: () => String = _

  def test(block: => String): Unit = {
    println("test")
    s = () => block
    test1()
  }

  def test1(): Unit = {
    println("test1")
    println(s())
  }

}
