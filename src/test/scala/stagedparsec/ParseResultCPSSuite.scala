package stagedparsec

import scala.virtualization.lms.common._
import scala.reflect.SourceContext
import scala.virtualization.lms.internal.GenericCodegen
import lms._
import lms.util._

import java.io.PrintWriter
import java.io.StringWriter
import java.io.FileOutputStream

/** stupid feature import*/
import scala.language.postfixOps

trait ParseResultCPSProg
    extends ParseResultCPS
    with OrderingOps
    with PrimitiveOps
    with NumericOps
    with StringReaderOps {

  import ParseResultCPS._

  /**
   * NOTE: we use `conditional` instead of the classic
   * if then else sugar, because there is either the virtualized
   * version (which required Reps for everything) or classic
   * version (no Reps anywhere)
   */

  /**
   * should generate code in which the reader should be DCE'd
   */
  def singleConditional(in: Rep[Array[Char]], flag: Rep[Boolean]): Rep[Option[Int]] = {
    val tmp = StringReader(in)
    val c = conditional(flag,
      Success(unit(1), tmp),
      Failure[Int](tmp.rest)
    )
    c.toOption
  }

  /**
   * should generate code in which the reader should be DCE'd
   */
  def nestedConditional(in: Rep[Array[Char]], i: Rep[Int]): Rep[Option[Int]] = {
    val tmp = StringReader(in)
    val c: ParseResultCPS[Int] = conditional(
      i <= unit(3),
      conditional(i >= unit(1), Success(i, tmp), Failure(tmp)),
      conditional(i >= unit(5), Success(i, tmp), Failure(tmp))
    )
    c.toOption
  }

  /**
   * should generate code where the notion of option
   * has disappeard
   */
  def mapSuccess(in: Rep[Array[Char]], i: Rep[Int]): Rep[Option[Int]] = {
    val s = Success(i, StringReader(in))
    s.map(x => x * unit(2)).toOption
  }

  /**
   * should generate code where the notion of option
   * has disappeard
   */
  def mapFailure(in: Rep[Array[Char]], i: Rep[Int]): Rep[Option[Int]] = {
    val s = Failure[Int](StringReader(in))
    s.map(x => x * unit(2)).toOption
  }

  /** code for in.rest must be generated in the else branch only */
  def mapConditional(in: Rep[Array[Char]], i: Rep[Int]): Rep[Option[Int]] = {
    val tmp = StringReader(in)
    val s = conditional(i <= unit(3), Success(i, tmp), Failure[Int](tmp.rest))
    s.map(_ * unit(2)).toOption
  }

  def mapConditional2(in: Rep[Array[Char]], i: Rep[Int]): Rep[Option[Int]] = {
    val tmp = StringReader(in)
    val s = conditional(i <= unit(3), Success(i, tmp), Success(i, tmp.rest))
    s.map(_ * unit(3)).toOption
  }

  def mapNestedConditional(in: Rep[Array[Char]], i: Rep[Int]): Rep[Option[Int]] = {
    val tmp = StringReader(in)
    val s: ParseResultCPS[Int] = conditional(
      i <= unit(3),
      conditional(i >= unit(1), Success(i, tmp), Failure(tmp)),
      Success(i * unit(2), tmp)
    )
    s.map(_ * unit(3)).toOption
  }

  /**
   * code similar to an alternating combinator in parser combinators,
   * but manually inlined
   */
/*  def nestedConditional2(in: Rep[Array[Char]]): Rep[Option[Char]] = {
    val tmp = StringReader(in)
    val first: ParseResultCPS[Char] = conditional(
      tmp.atEnd,
      Failure(tmp),
      conditional(
        tmp.first == unit('a'),
        Success(tmp.first, tmp.rest),
        Failure(tmp)
      )
    )

    val second: ParseResultCPS[Char] = first.flatMapWithNext(
      (t, nxt) => Success(t, nxt),
      nxt => conditional(
        nxt.atEnd,
        Failure(nxt),
        conditional(
          nxt.first == unit('b'),
          Success(nxt.first, nxt.rest),
          Failure(nxt)
        )
      )
    )

    second.toOption
  }
*/
/*
  def flatMapSome(in: Rep[Int]): Rep[Option[Int]] = {
    val s = Some(in)
    s.flatMap(x => Some(x * unit(2))).toOption
  }

  def flatMapNone(in: Rep[Int]): Rep[Option[Int]] = {
    val s = None[Int]
    s.flatMap(x => Some(x * unit(2))).toOption
  }

  def flatMapConditional(in: Rep[Int]): Rep[Option[Int]] = {
    val s = conditional(in <= unit(3), Some(in), None[Int])
    s flatMap { x =>
      conditional(
        x >= unit(1),
        Some(x * unit(5)),
        Some(x * unit(10))
      )
    } toOption
  }

  def filtersome(in: Rep[Int]): Rep[Option[Int]] = {
    val s: Rep[Option[Int]] = mkSome(in)
    s.filter(x => x == unit(3)).toOption
  }

  def filternone(in: Rep[Int]): Rep[Option[Int]] = {
    val s = mkNone[Int]
    s.filter(x => x == unit(2)).toOption
  }
*/
}

class ParseResultCPSSuite extends FileDiffSuite {

  val prefix = "test-out/"

  def testParseResultCPS = {
    withOutFile(prefix + "parseresultcps") {
      new ParseResultCPSProg
          with OrderingOpsExpOpt
          with PrimitiveOpsExpOpt
          with NumericOpsExpOpt
          with OptionOpsExp
          with StringReaderOpsExpOpt
          with MyScalaCompile { self =>

        val codegen = new ScalaGenBase
          with ScalaGenIfThenElse
          with ScalaGenBooleanOps
          with ScalaGenOrderingOps
          with ScalaGenEqual
          with ScalaGenVariables
          with ScalaGenPrimitiveOps
          with ScalaGenNumericOps
          with ScalaGenOptionOps
          with ScalaGenStringReaderOps { val IR: self.type = self }

        codegen.emitSource2(singleConditional _, "singleConditional", new java.io.PrintWriter(System.out))
        codegen.reset

        val testcSingleConditional = compile2(singleConditional)
        scala.Console.println(testcSingleConditional("".toArray, true))
        codegen.reset

        codegen.emitSource2(nestedConditional _, "nestedConditional", new java.io.PrintWriter(System.out))
        codegen.reset

        val testcNestedConditional = compile2(nestedConditional)
        scala.Console.println(testcNestedConditional("".toArray, 5))
        codegen.reset

        codegen.emitSource2(mapSuccess _, "mapSuccess", new java.io.PrintWriter(System.out))
        codegen.reset

        val testcMapSuccess = compile2(mapSuccess)
        scala.Console.println(testcMapSuccess("".toArray, 5))
        codegen.reset

        codegen.emitSource2(mapFailure _, "mapFailure", new java.io.PrintWriter(System.out))
        codegen.reset

        val testcMapFailure = compile2(mapFailure)
        scala.Console.println(testcMapFailure("".toArray, 5))
        codegen.reset

        codegen.emitSource2(mapConditional _, "mapConditional", new java.io.PrintWriter(System.out))
        codegen.reset

        val testcMapConditional = compile2(mapConditional)
        scala.Console.println(testcMapConditional("h".toArray, 5))
        scala.Console.println(testcMapConditional("h".toArray, 3))
        codegen.reset

        codegen.emitSource2(mapConditional2 _, "mapConditional2", new java.io.PrintWriter(System.out))
        codegen.reset

        val testcMapConditional2 = compile2(mapConditional2)
        scala.Console.println(testcMapConditional2("h".toArray, 5))
        scala.Console.println(testcMapConditional2("h".toArray, 3))
        codegen.reset

        codegen.emitSource2(mapNestedConditional _, "mapNestedConditional", new java.io.PrintWriter(System.out))
        codegen.reset

        val testcMapNestedConditional = compile2(mapNestedConditional)
        scala.Console.println(testcMapNestedConditional("h".toArray, 5))
        scala.Console.println(testcMapNestedConditional("h".toArray, 3))
        codegen.reset

/*
        codegen.emitSource(flatMapSome _, "flatMapSome", new java.io.PrintWriter(System.out))
        codegen.reset

        val testcFlatMapSome = compile(flatMapSome)
        scala.Console.println(testcFlatMapSome(5))
        codegen.reset

        codegen.emitSource(flatMapNone _, "flatMapNone", new java.io.PrintWriter(System.out))
        codegen.reset

        val testcFlatMapNone = compile(flatMapNone)
        scala.Console.println(testcFlatMapNone(5))
        codegen.reset

        codegen.emitSource(flatMapConditional _, "flatMapConditional", new java.io.PrintWriter(System.out))
        codegen.reset

        val testcFlatMapConditional = compile(flatMapConditional)
        scala.Console.println(testcFlatMapConditional(5))
        scala.Console.println(testcFlatMapConditional(3))
        scala.Console.println(testcFlatMapConditional(0))
        codegen.reset
*/

      }
    }

    assertFileEqualsCheck(prefix + "parseresultcps")
  }
}