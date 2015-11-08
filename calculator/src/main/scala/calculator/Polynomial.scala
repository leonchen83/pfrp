package calculator

object Polynomial {
  def computeDelta(a: Signal[Double], b: Signal[Double],
                   c: Signal[Double]): Signal[Double] = {
    Signal({
      val vb = b();
      vb * vb - 4 * a() * c()
    })
  }

  def computeSolutions(a: Signal[Double], b: Signal[Double],
                       c: Signal[Double], delta: Signal[Double]): Signal[Set[Double]] = {
    Signal({
      if (delta() < 0) {
        Set()
      } else {
        val sqrtDelta = Math.sqrt(delta())
        Set((-b() + sqrtDelta) / (2 * a()), (-b() - sqrtDelta) / (2 * a()))
      }
    })
  }
}
