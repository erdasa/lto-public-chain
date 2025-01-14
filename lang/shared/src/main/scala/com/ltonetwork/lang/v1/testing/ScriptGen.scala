package com.ltonetwork.lang.v1.testing

import com.ltonetwork.lang.v1.parser.BinaryOperation
import com.ltonetwork.lang.v1.parser.BinaryOperation._
import com.ltonetwork.lang.v1.parser.Expressions._
import com.ltonetwork.lang.v1.parser.Parser.keywords
import org.scalacheck._
import scodec.bits.ByteVector
import scorex.crypto.encode.Base58

import scala.reflect.ClassTag

trait ScriptGen {

  def CONST_LONGgen: Gen[(EXPR, Long)] = Gen.choose(Long.MinValue, Long.MaxValue).map(v => (CONST_LONG(Pos(0, 0), v), v))

  def BOOLgen(gas: Int): Gen[(EXPR, Boolean)] =
    if (gas > 0) Gen.oneOf(GEgen(gas - 1), GTgen(gas - 1), EQ_INTgen(gas - 1), ANDgen(gas - 1), ORgen(gas - 1), IF_BOOLgen(gas - 1))
    else Gen.const((TRUE(Pos(0, 0)), true))

  def SUMgen(gas: Int): Gen[(EXPR, Long)] =
    for {
      (i1, v1) <- INTGen((gas - 2) / 2)
      (i2, v2) <- INTGen((gas - 2) / 2)
    } yield
      if ((BigInt(v1) + BigInt(v2)).isValidLong) {
        (BINARY_OP(Pos(0, 0), i1, SUM_OP, i2), v1 + v2)
      } else {
        (BINARY_OP(Pos(0, 0), i1, SUB_OP, i2), v1 - v2)
      }

  def SUBgen(gas: Int): Gen[(EXPR, Long)] =
    for {
      (i1, v1) <- INTGen((gas - 2) / 2)
      (i2, v2) <- INTGen((gas - 2) / 2)
    } yield
      if ((BigInt(v1) - BigInt(v2)).isValidLong) {
        (BINARY_OP(Pos(0, 0), i1, SUB_OP, i2), v1 - v2)
      } else {
        (BINARY_OP(Pos(0, 0), i1, SUM_OP, i2), v1 + v2)
      }

  def INTGen(gas: Int): Gen[(EXPR, Long)] =
    if (gas > 0)
      Gen.oneOf(
        CONST_LONGgen,
        SUMgen(gas - 1),
        SUBgen(gas - 1),
        IF_INTgen(gas - 1),
        INTGen(gas - 1).filter(v => (-BigInt(v._2)).isValidLong).map(e => (FUNCTION_CALL(Pos(0, 0), PART.VALID(Pos(0, 0), "-"), List(e._1)), -e._2))
      )
    else CONST_LONGgen

  def GEgen(gas: Int): Gen[(EXPR, Boolean)] =
    for {
      (i1, v1) <- INTGen((gas - 2) / 2)
      (i2, v2) <- INTGen((gas - 2) / 2)
    } yield (BINARY_OP(Pos(0, 0), i1, GE_OP, i2), v1 >= v2)

  def GTgen(gas: Int): Gen[(EXPR, Boolean)] =
    for {
      (i1, v1) <- INTGen((gas - 2) / 2)
      (i2, v2) <- INTGen((gas - 2) / 2)
    } yield (BINARY_OP(Pos(0, 0), i1, GT_OP, i2), v1 > v2)

  def EQ_INTgen(gas: Int): Gen[(EXPR, Boolean)] =
    for {
      (i1, v1) <- INTGen((gas - 2) / 2)
      (i2, v2) <- INTGen((gas - 2) / 2)
    } yield (BINARY_OP(Pos(0, 0), i1, EQ_OP, i2), v1 == v2)

  def ANDgen(gas: Int): Gen[(EXPR, Boolean)] =
    for {
      (i1, v1) <- BOOLgen((gas - 2) / 2)
      (i2, v2) <- BOOLgen((gas - 2) / 2)
    } yield (BINARY_OP(Pos(0, 0), i1, AND_OP, i2), v1 && v2)

  def ORgen(gas: Int): Gen[(EXPR, Boolean)] =
    for {
      (i1, v1) <- BOOLgen((gas - 2) / 2)
      (i2, v2) <- BOOLgen((gas - 2) / 2)
    } yield (BINARY_OP(Pos(0, 0), i1, OR_OP, i2), v1 || v2)

  def IF_BOOLgen(gas: Int): Gen[(EXPR, Boolean)] =
    for {
      (cnd, vcnd) <- BOOLgen((gas - 3) / 3)
      (t, vt)     <- BOOLgen((gas - 3) / 3)
      (f, vf)     <- BOOLgen((gas - 3) / 3)
    } yield (IF(Pos(0, 0), cnd, t, f), if (vcnd) { vt } else { vf })

  def IF_INTgen(gas: Int): Gen[(EXPR, Long)] =
    for {
      (cnd, vcnd) <- BOOLgen((gas - 3) / 3)
      (t, vt)     <- INTGen((gas - 3) / 3)
      (f, vf)     <- INTGen((gas - 3) / 3)
    } yield (IF(Pos(0, 0), cnd, t, f), if (vcnd) { vt } else { vf })

  def STRgen: Gen[EXPR] =
    Gen.identifier.map(PART.VALID[String](Pos(0, 0), _)).map(CONST_STRING(Pos(0, 0), _))

  def LETgen(gas: Int): Gen[LET] =
    for {
      name       <- Gen.identifier
      (value, _) <- BOOLgen((gas - 3) / 3)
    } yield LET(Pos(0, 0), PART.VALID(Pos(0, 0), name), value, Seq.empty)

  def REFgen: Gen[EXPR] =
    Gen.identifier.filter(!keywords(_)).map(PART.VALID[String](Pos(0, 0), _)).map(REF(Pos(0, 0), _))

  def BLOCKgen(gas: Int): Gen[EXPR] =
    for {
      let  <- LETgen((gas - 3) / 3)
      body <- Gen.oneOf(BOOLgen((gas - 3) / 3).map(_._1), BLOCKgen((gas - 3) / 3)) // BLOCKGen wasn't add to BOOLGen since issue: NODE-700
    } yield BLOCK(Pos(0, 0), let, body)

  private val spaceChars: Seq[Char] = " \t\n\r"

  val whitespaceChar: Gen[Char] = Gen.oneOf(spaceChars)
  val whitespaces: Gen[String] = for {
    n  <- Gen.choose(1, 5)
    xs <- Gen.listOfN(n, whitespaceChar)
  } yield xs.mkString

  def withWhitespaces(expr: String): Gen[String] =
    for {
      pred <- whitespaces
      post <- whitespaces
    } yield pred + expr + post

  private def toString[T](part: PART[T])(implicit ct: ClassTag[T]): String = part match {
    case PART.VALID(_, x: String)      => x
    case PART.VALID(_, xs: ByteVector) => Base58.encode(xs.toArray)
    case _                                => throw new RuntimeException(s"Can't stringify $part")
  }

  def toString(expr: EXPR): Gen[String] = expr match {
    case CONST_LONG(_, x)       => withWhitespaces(s"$x")
    case REF(_, x)              => withWhitespaces(toString(x))
    case CONST_STRING(_, x)     => withWhitespaces(s"""\"${toString(x)}\"""")
    case CONST_BYTEVECTOR(_, x) => withWhitespaces(s"""base58'${toString(x)}'""")
    case _: TRUE                   => withWhitespaces("true")
    case _: FALSE                  => withWhitespaces("false")
    case BINARY_OP(_, x, op: BinaryOperation, y) =>
      for {
        arg1 <- toString(x)
        arg2 <- toString(y)
      } yield s"($arg1${opsToFunctions(op)}$arg2)"
    case IF(_, cond, x, y) =>
      for {
        c <- toString(cond)
        t <- toString(x)
        f <- toString(y)
      } yield s"(if ($c) then $t else $f)"
    case BLOCK(_, let, body) =>
      for {
        v         <- toString(let.value)
        b         <- toString(body)
        isNewLine <- Arbitrary.arbBool.arbitrary
        sep       <- if (isNewLine) Gen.const("\n") else withWhitespaces(";")
      } yield s"let ${toString(let.name)} = $v$sep$b"

    case FUNCTION_CALL(_, PART.VALID(_, "-"), List(CONST_LONG(_, v))) if v >= 0 =>
      s"-($v)"
    case FUNCTION_CALL(_, op, List(e)) => toString(e).map(e => s"${toString(op)}$e")

    case x => throw new NotImplementedError(s"toString for ${x.getClass.getSimpleName}")
  }
}

trait ScriptGenParser extends ScriptGen {
  override def BOOLgen(gas: Int): Gen[(EXPR, Boolean)] = {
    if (gas > 0)
      Gen.oneOf(GEgen(gas - 1), GTgen(gas - 1), EQ_INTgen(gas - 1), ANDgen(gas - 1), ORgen(gas - 1), IF_BOOLgen(gas - 1), REFgen.map(r => (r, false)))
    else Gen.const((TRUE(Pos(0, 0)), true))
  }

  override def INTGen(gas: Int): Gen[(EXPR, Long)] =
    if (gas > 0) Gen.oneOf(CONST_LONGgen, SUMgen(gas - 1), IF_INTgen(gas - 1), REFgen.map(r => (r, 0L))) else CONST_LONGgen
}
