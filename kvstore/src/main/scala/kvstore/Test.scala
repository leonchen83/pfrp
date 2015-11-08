package com.anwee.json.pointer
/**
 *
 * A implementation of RFC6901.
 * Provide a convenient way to access json object using the path which like file path.
 *
 * ===RFC6901 example===
 * @example {{{
 * // For example, given the JSON document
 * {
 *   "foo": ["bar", "baz"],
 *   "": 0,
 *   "a/b": 1,
 *   "c%d": 2,
 *   "e^f": 3,
 *   "g|h": 4,
 *   "i\\j": 5,
 *   "k\"l": 6,
 *   " ": 7,
 *   "m~n": 8
 * }
 *
 * // The following JSON strings evaluate to the accompanying values:
 *
 * ""           // the whole document
 * "/foo"       ["bar", "baz"]
 * "/foo/0"     "bar"
 * "/"          0
 * "/a~1b"      1
 * "/c%d"       2
 * "/e^f"       3
 * "/g|h"       4
 * "/i\\j"      5
 * "/k\"l"      6
 * "/ "         7
 * "/m~0n"      8
 * }}}
 *
 * usage
 * {{{
 *   val json =
 *     """
 *       |{
 *       |  "store": {
 *       |    "book": [
 *       |      { "category": "reference",
 *       |        "author": "Nigel Rees",
 *       |        "title": "Sayings of the Century",
 *       |        "price": 8.95
 *       |      },
 *       |      { "category": "fiction",
 *       |        "author": "Evelyn Waugh",
 *       |        "title": "Sword of Honour",
 *       |        "price": 12.99
 *       |      },
 *       |      { "category": "fiction",
 *       |        "author": "Herman Melville",
 *       |        "title": "Moby Dick",
 *       |        "isbn": "0-553-21311-3",
 *       |        "price": 8.99
 *       |      },
 *       |      { "category": "fiction",
 *       |        "author": "J. R. R. Tolkien",
 *       |        "title": "The Lord of the Rings",
 *       |        "isbn": "0-395-19395-8",
 *       |        "price": 22.99
 *       |      }
 *       |    ],
 *       |    "bicycle": {
 *       |      "color": "red",
 *       |      "price": 19.95
 *       |    }
 *       |  }
 *       |}
 *     """.stripMargin
 *   val jp = JSONPointer(json)
 *   val value = jp.path("/store/book/0-2/isbn")
 * }}}
 *
 * @author leon.chen
 * @version 0.0.1
 * @since   0.0.1
 * @see [[http://tools.ietf.org/html/rfc6901 "JSON Pointer (RFC 6901)"]]
 * @see [[http://json.org "JSON (JavaScript Object Notation)"]]
 *
 */

abstract class JSONType

case class JSONObject(map: Map[String, Any]) extends JSONType

case class JSONArray(list: List[Any]) extends JSONType

sealed case class Rule(rule: Any)

case object NotFound

case object JSONPointerParser {
  def apply(str: String): JSONPointerParser = new JSONPointerParser(str)
}

object JSONParser {
  def apply(json: String): JSONParser = new JSONParser(json)
}

object JSONPointer {
  def apply(json: String): JSONPointer = new JSONPointer(json)
}

class JSONParser(json: String) {

  private[pointer] var it: Iterator[Char] = json.iterator

  private[pointer] def parser(): JSONType = {
    next() match {
      case '{' => parseObject()
      case '[' => parseArray()
      case e => throw new Exception(e.toString)
    }
  }

  private[pointer] def parseObject(): JSONObject = {
    var map = Map.empty[String, Any]
    next() match {
      case '}' => JSONObject(map)
      case c =>
        back(c)
        map += parseItem()
        next() match {
          case ',' =>
            back(',')
            var ch = next()
            while (ch == ',') {
              map += parseItem()
              ch = next()
            }
            ch match {
              case '}' =>
                JSONObject(map)
            }
          case '}' =>
            JSONObject(map)
          case e => throw new Exception(e.toString)
        }
    }
  }

  private[pointer] def parseArray(): JSONArray = {
    var list = List.empty[Any]
    next() match {
      case ']' =>
        JSONArray(list)
      case ch =>
        back(ch)
        list = list :+ parseValue()
        next() match {
          case ',' =>
            back(',')
            var ch = next()
            while (ch == ',') {
              list = list :+ parseValue()
              ch = next()
            }
            ch match {
              case ']' =>
                JSONArray(list)
            }
          case ']' =>
            JSONArray(list)
        }
    }
  }

  private[pointer] def parseValue(): Any = {
    next() match {
      case '"' =>
        back('"')
        parseString()
      case 't' =>
        back('t')
        parseTrue()
      case 'f' =>
        back('f')
        parseFalse()
      case 'n' =>
        back('n')
        parseNull()
      case '{' =>
        parseObject()
      case '[' =>
        parseArray()
      case n if (n == '-' || (n >= '0' && n <= '9')) =>
        back(n)
        parseNumber()
      case e => throw new Exception(e.toString)
    }
  }

  private[pointer] def parseItem(): (String, Any) = {
    next() match {
      case '"' =>
        back('"')
        val key = parseString()
        next() match {
          case ':' => (key, parseValue())
          case e => throw new Exception(e.toString)
        }
      case e => throw new Exception(e.toString)
    }
  }

  private[pointer] def parseNull(): Any = {
    nextChar() match {
      case 'n' => nextChar() match {
        case 'u' => nextChar() match {
          case 'l' => nextChar() match {
            case 'l' => null
            case e => throw new Exception(e.toString)
          }
          case e => throw new Exception(e.toString)
        }
        case e => throw new Exception(e.toString)
      }
      case e => throw new Exception(e.toString)
    }
  }

  private[pointer] def parseFalse(): Boolean = {
    nextChar() match {
      case 'f' => nextChar() match {
        case 'a' => nextChar() match {
          case 'l' => nextChar() match {
            case 's' => nextChar() match {
              case 'e' => false
              case e => throw new Exception(e.toString)
            }
            case e => throw new Exception(e.toString)
          }
          case e => throw new Exception(e.toString)
        }
        case e => throw new Exception(e.toString)
      }
      case e => throw new Exception(e.toString)
    }
  }

  private[pointer] def parseTrue(): Boolean = {
    nextChar() match {
      case 't' => nextChar() match {
        case 'r' => nextChar() match {
          case 'u' => nextChar() match {
            case 'e' => true
            case e => throw new Exception(e.toString)
          }
          case e => throw new Exception(e.toString)
        }
        case e => throw new Exception(e.toString)
      }
      case e => throw new Exception(e.toString)
    }
  }

  private[pointer] def parseString(): String = {
    val sb: StringBuilder = new StringBuilder
    nextChar()
    var ch = nextChar()
    while (ch != '"') {
      ch match {
        case '\\' =>
          sb.append('\\')
          ch = nextChar()
          ch match {
            case e if (e == '"' || e == '\\' || e == '/' || e == 'b' || e == 'f' || e == 'n' || e == 'r' || e == 't') =>
              sb.append(e)
              ch = nextChar()
            case '"' =>
              sb.append('\"')
              ch = nextChar()
            case '\\' =>
              sb.append('\\')
              ch = nextChar()
            case '/' =>
              sb.append('/')
              ch = nextChar()
            case 'b' =>
              sb.append('\b')
              ch = nextChar()
            case 'f' =>
              sb.append('\f')
              ch = nextChar()
            case 'F' =>
              sb.append('\f')
              ch = nextChar()
            case 'n' =>
              sb.append('\n')
              ch = nextChar()
            case 'r' =>
              sb.append('\r')
              ch = nextChar()
            case 't' =>
              sb.append('\t')
              ch = nextChar()
            case 'u' =>
              val u1 = nextChar()
              val u2 = nextChar()
              val u3 = nextChar()
              val u4 = nextChar()
              val s = Integer.valueOf(new String(Array(u1, u2, u3, u4)), 16).toChar
              sb.append(s)
              ch = nextChar()
          }
        case e =>
          sb.append(ch)
          ch = nextChar()
      }
    }
    sb.toString()
  }

  private[pointer] def parseNumber(): AnyVal = {
    val sb = new StringBuilder
    var next = nextChar()
    if (next == '-') {
      sb.append('-')
      next = nextChar()
    }
    if (next == '0') {
      sb.append('0')
      next = nextChar()
    } else if (next > '0' && next <= '9') {
      sb.append(next)
      next = nextChar()
      while (parseDigit(next, sb)) {
        next = nextChar()
      }
    }

    if (next == '.' || next == 'e' || next == 'E') {
      if (next == '.') {
        sb.append(next)
        next = nextChar()
        while (parseDigit(next, sb)) {
          next = nextChar()
        }
      }
      if (next == 'e' || next == 'E') {
        sb.append(next)
        next = nextChar()
        if (next == '+' || next == '-') {
          sb.append(next)
          next = nextChar()
        }
        while (parseDigit(next, sb)) {
          next = nextChar()
        }
      }
      back(next)
      sb.toString.toDouble
    } else {
      back(next)
      sb.toString.toLong match {
        case value if value >= Int.MinValue && value <= Int.MaxValue => sb.toString.toInt
        case value => value
      }
    }
  }

  private[pointer] def parseDigit(c: Char, sb: StringBuilder): Boolean = {
    c match {
      case c if c >= '0' && c <= '9' =>
        sb.append(c)
        true
      case e =>
        false
    }
  }

  private[pointer] def nextChar(): Char = {
    if (backBuffer.nonEmpty) {
      val c = backBuffer.head
      backBuffer = backBuffer.tail
      c
    } else {
      it.next()
    }
  }

  private[pointer] def next(): Char = {
    if (backBuffer.nonEmpty) {
      val c = backBuffer.head
      backBuffer = backBuffer.tail
      c
    } else {
      var c = it.next()
      while (ignoreLetter(c)) {
        c = it.next()
      }
      c
    }
  }

  private[pointer] def ignoreLetter(c: Char): Boolean = {
    c == ' ' || c == '\r' || c == '\n' || c == '\t'
  }

  private[pointer] var backBuffer = List.empty[Char]

  private[pointer] def back(char: Char): Unit = {
    var c = char
    while (ignoreLetter(c)) {
      c = it.next()
    }
    backBuffer = backBuffer :+ c
  }
}

class JSONPointerParser(str: String) {

  private[pointer] var it: Iterator[Char] = str.iterator

  private[pointer] def parsePath(): List[Rule] = {
    if (!hasNext()) {
      List.empty[Rule]
    } else {
      next() match {
        case '/' =>
          back('/')
          var rules = List.empty[Rule]
          while (hasNext() && next() == '/') {
            val rule = parseRule()
            rules = rules :+ rule
          }
          rules
        case '.' =>
          if (hasNext()) {
            var ch = next()
            if (ch == '.') {
              if (hasNext()) {
                ch = next()
                if (ch == '/') {
                  back('/')
                  new Rule("..") +: parsePath()
                } else {
                  throw new Exception(ch.toString)
                }
              } else {
                throw new Exception(ch.toString)
              }
            } else if (ch == '/') {
              back('/')
              new Rule(".") +: parsePath()
            } else {
              throw new Exception(ch.toString)
            }
          } else {
            throw new Exception(".")
          }
      }
    }
  }

  private[pointer] def parseRule(): Rule = {
    if (!hasNext()) {
      return new Rule("")
    }
    var ch = next()
    if (ch == '/') {
      back('/')
      new Rule("")
    } else if (ch > '0' && ch <= '9') {
      val sb = new StringBuilder
      sb.append(ch)
      while (hasNext() && {
        ch = next()
        ch >= '0' && ch <= '9'
      }) {
        sb.append(ch)
      }
      if (!hasNext()) {
        new Rule(sb.toString.toInt)
      } else if (ch == '/') {
        back('/')
        new Rule(sb.toString.toInt)
      } else {
        parseString(ch, sb)
        new Rule(sb.toString)
      }
    } else if (ch == '0') {
      val sb = new StringBuilder
      sb.append(ch)
      if (!hasNext()) {
        new Rule(sb.toString.toInt)
      } else {
        ch = next()
        if (!hasNext()) {
          new Rule(sb.toString.toInt)
        } else if (ch == '/') {
          back('/')
          new Rule(sb.toString.toInt)
        } else {
          parseString(ch, sb)
          new Rule(sb.toString)
        }
      }
    } else {
      val sb = new StringBuilder
      parseString(ch, sb)
      new Rule(sb.toString)
    }
  }

  private[pointer] def parseString(ch: Char, sb: StringBuilder): Unit = {
    back(ch)
    var c: Char = 0
    while (hasNext() && {
      c = next()
      c != '/'
    }) {
      if (c == '~') {
        if (hasNext()) {
          c = next()
          if (c == '0') {
            sb.append('~')
          } else if (c == '1') {
            sb.append('/')
          } else {
            sb.append('~')
            sb.append(c)
          }
        } else {
          sb.append('~')
        }
      } else {
        sb.append(c)
      }
    }
    if (!hasNext()) {
    }
    if (c == '/') {
      back(c)
    }
  }

  private[pointer] def hasNext(): Boolean = {
    backBuffer.nonEmpty || it.hasNext
  }

  private[pointer] def next(): Char = {
    if (backBuffer.nonEmpty) {
      val c = backBuffer.head
      backBuffer = backBuffer.tail
      c
    } else {
      it.next()
    }
  }

  private[pointer] var backBuffer = List.empty[Char]

  private[pointer] def back(char: Char): Unit = {
    backBuffer = backBuffer :+ char
  }
}

class JSONPointer(str: String) {
  private[pointer] val json: JSONType = JSONParser(str).parser()

  def path(path: String): Any = {
    var temp: Any = json
    val rules = JSONPointerParser(path).parsePath()
    rules.foreach(rule => temp = filter(rule, temp))
    temp
  }

  private[pointer] def filter(rule: Rule, temp: Any): Any = {
    temp match {
      case jsonAry: JSONArray =>
        rule.rule match {
          case index: Int => jsonAry.list(index)
          case str: String =>
            if (str.indexOf('-') >= 0) {
              val ary = str.split("-")
              if (ary.length != 2) {
                throw new Exception()
              }
              try {
                val from = ary(0).toInt
                val until = ary(1).toInt
                if (from > until + 1) {
                  throw new Exception
                }
                jsonAry.list.slice(from, until + 1)
              } catch {
                case e: NumberFormatException => throw new Exception
              }
            } else if (str.indexOf(',') >= 0) {
              val ary = str.split(",")
              if (ary.length < 2) {
                throw new Exception()
              }
              try {
                ary.map(e => jsonAry.list(e.toInt)).toList
              } catch {
                case e: NumberFormatException => throw new Exception
              }
            } else if (str.trim == "*") {
              jsonAry.list
            } else {
              NotFound
            }
        }
      case jsonObj: JSONObject =>
        rule.rule match {
          case index: Int => jsonObj.map.getOrElse(index.toString, NotFound)
          case str: String => jsonObj.map.getOrElse(str, {
            if (str.indexOf(",") >= 0) {
              val keys = str.split(",").toList
              keys.map(jsonObj.map.getOrElse(_, NotFound))
            } else if (str.trim == "*") {
              jsonObj.map.values.toList
            } else {
              NotFound
            }
          })
        }
      case list: List[Any] => {
        val rs = list.map(filter(rule, _))
        rs.size match {
          case 1 => rs(0)
          case _ => rs
        }
      }
      case _ => NotFound
    }
  }
}

