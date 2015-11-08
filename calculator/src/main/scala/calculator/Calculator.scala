package calculator

sealed abstract class Expr

final case class Literal(v: Double) extends Expr

final case class Ref(name: String) extends Expr

final case class Plus(a: Expr, b: Expr) extends Expr

final case class Minus(a: Expr, b: Expr) extends Expr

final case class Times(a: Expr, b: Expr) extends Expr

final case class Divide(a: Expr, b: Expr) extends Expr

object Calculator {
  def computeValues(namedExpressions: Map[String, Signal[Expr]]): Map[String, Signal[Double]] = {
    namedExpressions.map(e => e._1 -> Signal(eval(e._2(), namedExpressions)))
  }

  def eval(expr: Expr, references: Map[String, Signal[Expr]]): Double = {
    expr match {
      case Plus(a, b) => eval(a, references) + eval(b, references)
      case Minus(a, b) => eval(a, references) - eval(b, references)
      case Times(a, b) => eval(a, references) * eval(b, references)
      case Divide(a, b) => eval(a, references) / eval(b, references)
      case Literal(v) => v
      case Ref(name) => if (checkCircle(name, references)) eval(Literal(Double.NaN), references) else eval(getReferenceExpr(name, references), references)
    }
  }

  def checkCircle(name: String, references: Map[String, Signal[Expr]]): Boolean = {
    val expr = getReferenceExpr(name, references)
    def circle(expr: Expr): Boolean = {
      expr match {
        case Literal(v) => v == Double.NaN
        case Plus(a, b) => (circle(a) || circle(b)) == true
        case Minus(a, b) => (circle(a) || circle(b)) == true
        case Times(a, b) => (circle(a) || circle(b)) == true
        case Divide(a, b) => (circle(a) || circle(b)) == true
        case Ref(n) => if (name == n) true else circle(getReferenceExpr(n, references))
      }
    }
    circle(expr)
  }

  /** Get the Expr for a referenced variables.
    * If the variable is not known, returns a literal NaN.
    */
  private def getReferenceExpr(name: String,
                               references: Map[String, Signal[Expr]]) = {
    references.get(name).fold[Expr] {
      Literal(Double.NaN)
    } { exprSignal =>
      exprSignal()
    }
  }
}
