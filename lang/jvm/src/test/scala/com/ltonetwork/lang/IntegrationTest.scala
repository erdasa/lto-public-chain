package com.ltonetwork.lang

import cats.data.EitherT
import cats.kernel.Monoid
import com.ltonetwork.lang.Common._
import com.ltonetwork.lang.v1.CTX
import com.ltonetwork.lang.v1.compiler.Terms._
import com.ltonetwork.lang.v1.compiler.Types.{FINAL, LONG}
import com.ltonetwork.lang.v1.compiler.{CompilerV1, Terms}
import com.ltonetwork.lang.v1.evaluator.EvaluatorV1
import com.ltonetwork.lang.v1.evaluator.ctx._
import com.ltonetwork.lang.v1.evaluator.ctx.impl.PureContext
import com.ltonetwork.lang.v1.parser.Parser
import com.ltonetwork.lang.v1.testing.ScriptGen
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class IntegrationTest extends AnyPropSpec with ScalaCheckDrivenPropertyChecks with ScriptGen with Matchers with NoShrink {

  property("simple let") {
    val src =
      """
        |let a = 1
        |let b = a + a
        |b + b + b
      """.stripMargin
    eval[Long](src) shouldBe Right(6)
  }

  property("proper error message") {
    val src =
      """
        |match p {
        |  case pa: PointA => let x = 3
        |  case _ => throw()
        |}
      """.stripMargin
    eval[Boolean](src) should produce("can't parse the expression")
  }

  property("patternMatching") {
    val sampleScript =
      """match p {
        |  case pa: PointA => 0
        |  case pa: PointB => 1
        |  case pa: PointC => 2
        |}""".stripMargin
    eval[Long](sampleScript, Some(pointAInstance)) shouldBe Right(0)
    eval[Long](sampleScript, Some(pointBInstance)) shouldBe Right(1)
  }

  property("patternMatching with named union types") {
    val sampleScript =
      """match p {
        |  case pa: PointA => 0
        |  case pa: PointBC => 1
        |}""".stripMargin
    eval[Long](sampleScript, Some(pointAInstance)) shouldBe Right(0)
    eval[Long](sampleScript, Some(pointBInstance)) shouldBe Right(1)
  }

  property("union types have fields") {
    val sampleScript =
      """match p {
        |  case pa: PointA => pa.X
        |  case pb: PointBC => pb.YB
        |}""".stripMargin
    eval[Long](sampleScript, Some(pointAInstance)) shouldBe Right(3)
    eval[Long](sampleScript, Some(pointBInstance)) shouldBe Right(41)
    eval[Long](sampleScript, Some(pointCInstance)) shouldBe Right(42)
  }

  property("union types have  only common fields") {
    val sampleScript =
      """match p {
        |  case pa: PointA => pa.X
        |  case pb: PointBC => pb.X
        |}""".stripMargin
    eval[Long](sampleScript, Some(pointCInstance)) should produce("Compilation failed: Undefined field `X`")
  }

  property("patternMatching _") {
    val sampleScript =
      """|
         |match p {
         |  case _: PointA => 0
         |  case _: PointB  => 1
         |  case _: PointC => 2
         |}
         |
      """.stripMargin
    eval[Long](sampleScript, Some(pointAInstance)) shouldBe Right(0)
    eval[Long](sampleScript, Some(pointBInstance)) shouldBe Right(1)
  }

  property("patternMatching any type") {
    val sampleScript =
      """|
         |match p {
         |  case _: PointA => 0
         |  case _  => 1
         |}
         |
      """.stripMargin
    eval[Long](sampleScript, Some(pointAInstance)) shouldBe Right(0)
    eval[Long](sampleScript, Some(pointBInstance)) shouldBe Right(1)
  }

  property("patternMatching block") {
    val sampleScript =
      """|
         |match (let x = 1; p) {
         |  case _  => 1
         |}
         |
      """.stripMargin
    eval[Long](sampleScript, Some(pointBInstance)) shouldBe Right(1)
  }

  private def eval[T](code: String, pointInstance: Option[CaseObj] = None, pointType: FINAL = AorBorC): Either[String, T] = {
    val untyped                                      = Parser(code).get.value
    val lazyVal                                      = LazyVal(EitherT.pure(pointInstance.orNull))
    val stringToTuple: Map[String, (FINAL, LazyVal)] = Map(("p", (pointType, lazyVal)))
    val ctx: CTX =
      Monoid.combine(PureContext.ctx, CTX(sampleTypes, stringToTuple, Seq.empty))
    val typed = CompilerV1(ctx.compilerContext, untyped)
    typed.flatMap(v => EvaluatorV1[T](ctx.evaluationContext, v._1))
  }

  property("function call") {
    eval[Long]("10 + 2") shouldBe Right(12)
  }

  property("max values and operation order") {
    val longMax = Long.MaxValue
    val longMin = Long.MinValue
    eval(s"$longMax + 1 - 1") shouldBe Left("long overflow")
    eval(s"$longMin - 1 + 1") shouldBe Left("long overflow")
    eval(s"$longMax - 1 + 1") shouldBe Right(longMax)
    eval(s"$longMin + 1 - 1") shouldBe Right(longMin)
    eval(s"$longMax / $longMin + 1") shouldBe Right(0)
    eval(s"($longMax / 2) * 2") shouldBe Right(longMax - 1)
    eval[Long]("fraction(9223372036854775807, 3, 2)") shouldBe Left(s"Long overflow: value `${BigInt(Long.MaxValue) * 3 / 2}` greater than 2^63-1")
    eval[Long]("fraction(-9223372036854775807, 3, 2)") shouldBe Left(s"Long overflow: value `${-BigInt(Long.MaxValue) * 3 / 2}` less than -2^63-1")
    eval[Long](s"$longMax + fraction(-9223372036854775807, 3, 2)") shouldBe Left(
      s"Long overflow: value `${-BigInt(Long.MaxValue) * 3 / 2}` less than -2^63-1")
    eval[Long](s"2 + 2 * 2") shouldBe Right(6)
    eval("2 * 3 == 2 + 4") shouldBe Right(true)
  }

  property("equals works on primitive types") {
    eval[Boolean]("base58'3My3KZgFQ3CrVHgz6vGRt8687sH4oAA1qp8' == base58'3My3KZgFQ3CrVHgz6vGRt8687sH4oAA1qp8'") shouldBe Right(true)
    eval[Boolean]("1 == 2") shouldBe Right(false)
    eval[Boolean]("3 == 3") shouldBe Right(true)
    eval[Boolean]("false == false") shouldBe Right(true)
    eval[Boolean]("true == false") shouldBe Right(false)
    eval[Boolean]("true == true") shouldBe Right(true)
    eval[Boolean]("""   "x" == "x"     """) shouldBe Right(true)
    eval[Boolean]("""   "x" == "y"     """) shouldBe Right(false)
    eval[Boolean]("""   "x" != "y"     """) shouldBe Right(true)
    eval[Boolean]("""   "x" == 3     """) should produce("Can't match inferred types")
    eval[Boolean]("""   "x" != 3     """) should produce("Can't match inferred types")
    eval[Boolean](""" let union = if(true) then "x" else 3; union == "x"   """) shouldBe Right(true)
  }

  property("equals some lang structure") {
    eval[Boolean]("let x = (-7763390488025868909>-1171895536391400041); let v = false; (v&&true)") shouldBe Right(false)
    eval[Boolean]("let mshUmcl = (if(true) then true else true); true || mshUmcl") shouldBe Right(true)
    eval[Long]("""if(((1+-1)==-1)) then 1 else (1+1)""") shouldBe Right(2)
    eval[Boolean]("""((((if(true) then 1 else 1)==2)||((if(true)
        |then true else true)&&(true||true)))||(if(((1>1)||(-1>=-1)))
        |then (-1>=1) else false))""".stripMargin) shouldBe Right(true)
  }

  property("sum/mul/div/mod/fraction functions") {
    eval[Long]("(10 + 10)#jhk\n ") shouldBe Right(20)
    eval[Long]("(10 * 10)") shouldBe Right(100)
    eval[Long]("(10 / 3)") shouldBe Right(3)
    eval[Long]("(10 % 3)") shouldBe Right(1)
    eval[Long]("fraction(9223372036854775807, -2, -4)") shouldBe Right(Long.MaxValue / 2)
  }

  def compile(script: String): Either[String, Terms.EXPR] = {
    val compiler = new CompilerV1(CTX.empty.compilerContext)
    compiler.compile(script, List.empty)
  }

  property("wrong script return type") {
    compile("1") should produce("should return boolean")
    compile(""" "string" """) should produce("should return boolean")
    compile(""" base58'string' """) should produce("should return boolean")
  }

  property("equals works on elements from Gens") {
    List(CONST_LONGgen, SUMgen(50), INTGen(50)).foreach(gen =>
      forAll(for {
        (expr, res) <- gen
        str         <- toString(expr)
      } yield (str, res)) {
        case (str, res) =>
          withClue(str) {
            eval[Long](str) shouldBe Right(res)
          }
    })

    forAll(for {
      (expr, res) <- BOOLgen(50)
      str         <- toString(expr)
    } yield (str, res)) {
      case (str, res) =>
        withClue(str) {
          eval[Boolean](str) shouldBe Right(res)
        }
    }
  }

  property("Match with not case types") {
    eval[Long]("""
        |
        |let a = if (true) then 1 else ""
        |
        |match a {
        | case x: Int => x 
        | case y: String => 2
        |}""".stripMargin) shouldBe Right(1)
  }

  property("allow unions in pattern matching") {
    val sampleScript =
      """match p {
        |  case p1: PointBC => {
        |    match p1 {
        |      case pb: PointB => pb.X
        |      case pc: PointC => pc.YB
        |    }
        |  }
        |  case other => throw()
        |}""".stripMargin
    eval[Long](sampleScript, Some(pointBInstance)) shouldBe Right(3)
    eval[Long](sampleScript, Some(pointCInstance)) shouldBe Right(42)
  }

  property("different types, same name of field") {
    val sampleScript =
      """match (p.YB) {
        | case l: Int => l
        | case u: Unit => 1
        | }
      """.stripMargin
    eval[Long](sampleScript, Some(pointCInstance), CorD) shouldBe Right(42)
    eval[Long](sampleScript, Some(pointDInstance1), CorD) shouldBe Right(43)
    eval[Long](sampleScript, Some(pointDInstance2), CorD) shouldBe Right(1)

    eval[Long]("p.YB", Some(pointCInstance), CorD) shouldBe Right(42)
    eval[Long]("p.YB", Some(pointDInstance1), CorD) shouldBe Right(43)
    eval[Unit]("p.YB", Some(pointDInstance2), CorD) shouldBe Right(())
  }

  property("throw") {
    val script =
      """
        |let result = match p {
        |  case a: PointA => 0
        |  case b: PointB => throw()
        |  case c: PointC => throw("arrgh")
        |}
        |result
      """.stripMargin
    eval[Long](script, Some(pointAInstance)) shouldBe Right(0)
    eval[Long](script, Some(pointBInstance)) shouldBe Left("Explicit script termination")
    eval[Long](script, Some(pointCInstance)) shouldBe Left("arrgh")
  }

  property("context won't change after inner let") {
    val script = "{ let x = 3; x } + { let x = 5; x}"
    eval[Long](script, Some(pointAInstance)) shouldBe Right(8)
  }

  property("contexts of different if parts do not affect each other") {
    val script = "if ({let x= 0; x > 0 }) then { let x = 3; x } else { let x = 5; x}"
    eval[Long](script, Some(pointAInstance)) shouldBe Right(5)
  }

  property("context won't change after execution of a user function") {
    val doubleFst = UserFunction("ID", LONG, "x" -> LONG) {
      FUNCTION_CALL(PureContext.sumLong.header, List(REF("x"), REF("x")))
    }

    val context = Monoid.combine(
      PureContext.evalContext,
      EvaluationContext(
        typeDefs = Map.empty,
        letDefs = Map("x"                -> LazyVal(EitherT.pure(3l))),
        functions = Map(doubleFst.header -> doubleFst)
      )
    )

    val expr = FUNCTION_CALL(PureContext.sumLong.header, List(FUNCTION_CALL(doubleFst.header, List(CONST_LONG(1000l))), REF("x")))
    ev[Long](context, expr) shouldBe Right(2003l)
  }

  property("context won't change after execution of an inner block") {
    val context = Monoid.combine(
      PureContext.evalContext,
      EvaluationContext(
        typeDefs = Map.empty,
        letDefs = Map("x" -> LazyVal(EitherT.pure(3l))),
        functions = Map.empty
      )
    )

    val expr = FUNCTION_CALL(
      function = PureContext.sumLong.header,
      args = List(
        BLOCK(
          let = LET("x", CONST_LONG(5l)),
          body = REF("x")
        ),
        REF("x")
      )
    )
    ev[Long](context, expr) shouldBe Right(8)
  }

}
