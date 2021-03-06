package stagedparsec

import lms._
import lms.util._

import scala.lms.common._
import scala.lms.internal.GenericCodegen
import scala.reflect.SourceContext

import java.io.PrintWriter


/**
 * A CPS encoding for Parse Results
 * @see http://manojo.github.io/2015/09/02/staged-parser-combinators/ for more
 * information.
 */
trait ParseResultCPS
    extends Base
    with IfThenElse
    with BooleanOps
    with LiftVariables
    with OptionOps
    with ZeroVal { self: ReaderOps =>

  /**
   * CPS encoding for ParseResult
   * there are two ways to construct a ParseResult:
   *  1. a value and a next reader for a Success
   *  2. a next reader for a Failure
   * For now, the Reader remains a struct
   */
  abstract class ParseResultCPS[T: Typ] { self =>

    def apply[X: Typ](
      success: (Rep[T], Rep[Input]) => Rep[X],
      failure: Rep[Input] => Rep[X]
    ): Rep[X]

    /**
     * Usual suspect 1
     */
    def map[U: Typ](f: Rep[T] => Rep[U]) = new ParseResultCPS[U] {
      def apply[X: Typ](
        success: (Rep[U], Rep[Input]) => Rep[X],
        failure: Rep[Input] => Rep[X]
      ): Rep[X] = self.apply(
        (t: Rep[T], in: Rep[Input]) => success(f(t), in),
        failure
      )
    }

    /**
     * usual suspect 2, but modified
     * resembles its namesake from the standard parser combinator library
     * @see https://github.com/scala/scala-parser-combinators/blob/master/src/main/scala/scala/util/parsing/combinator/Parsers.scala#L112
     */
    def flatMapWithNext[U: Typ](f: (Rep[T], Rep[Input]) => ParseResultCPS[U])
        = new ParseResultCPS[U] {

      def apply[X: Typ](
        success: (Rep[U], Rep[Input]) => Rep[X],
        failure: Rep[Input] => Rep[X]
      ): Rep[X] = self.apply(
        (t: Rep[T], in: Rep[Input]) => f(t, in).apply(success, failure),
        failure
      )
    }

    /**
     * resembles the `append` from the standard parser combinator library
     * I (manojo) think `orElse` is a better name
     * @see https://github.com/scala/scala-parser-combinators/blob/master/src/main/scala/scala/util/parsing/combinator/Parsers.scala#L116
     */
    def orElse(that: => ParseResultCPS[T]) = new ParseResultCPS[T] {
      def apply[X: Typ](
        success: (Rep[T], Rep[Input]) => Rep[X],
        failure: Rep[Input] => Rep[X]
      ): Rep[X] = self.apply(
        (t: Rep[T], in: Rep[Input]) => success(t, in),
        (nxt: Rep[Input]) => that.apply(
          success,
          failure
        )
      )
    }

    def toOption: Rep[Option[T]] = {
      var isEmpty = unit(true); var value = zeroVal[T]
      self.apply(
        (t, _) => { isEmpty = unit(false); value = t },
        _ => unit(())
      )
      if (isEmpty) none[T]() else make_opt(Some(readVar(value)))
    }
  }

  /**
   * A conditional variant of ParseResult
   */
  case class ParseResultCPSCond[T: Typ](
    cond: Rep[Boolean],
    t: ParseResultCPS[T],
    e: ParseResultCPS[T]
  ) extends ParseResultCPS[T] { self =>

    /**
     * naive apply function
     */
    def apply[X: Typ](
      success: (Rep[T], Rep[Input]) => Rep[X],
      failure: Rep[Input] => Rep[X]
    ): Rep[X] = if (cond) t(success, failure) else e(success, failure)


    override def map[U: Typ](f: Rep[T] => Rep[U]) = new ParseResultCPS[U] {
      def apply[X: Typ](
        success: (Rep[U], Rep[Input]) => Rep[X],
        failure: Rep[Input] => Rep[X]
      ): Rep[X] = {
        var isEmpty = unit(true); var value = zeroVal[T]; var rdr = zeroVal[Input]

        self.apply[Unit](
          (x, next) => { isEmpty = unit(false); value = x; rdr = next },
          next => rdr = next
        )

        if (isEmpty) failure(rdr) else success(f(value), rdr)
      }
    }

    override def flatMapWithNext[U: Typ](f: (Rep[T], Rep[Input]) => ParseResultCPS[U])
        = new ParseResultCPS[U] {

      def apply[X: Typ](
        success: (Rep[U], Rep[Input]) => Rep[X],
        failure: Rep[Input] => Rep[X]
      ): Rep[X] = {

        var isEmpty = unit(true); var value = zeroVal[T]; var rdr = zeroVal[Input]

        self.apply[Unit](
          (x, next) => { isEmpty = unit(false); value = x; rdr = next },
          next => rdr = next
        )

        if (isEmpty) failure(rdr) else f(value, rdr).apply(success, failure)
      }
    }

/*
    override def orElse(that: ParseResultCPS[T]) = new ParseResultCPS[T] {
      def apply[X: Typ](
        success: (Rep[T], Rep[Input]) => Rep[X],
        failure: Rep[Input] => Rep[X]
      ): Rep[X] = {
        var isEmpty = unit(true); var value = zeroVal[T]; var rdr = zeroVal[Input]

        self.apply(
          (x, next) => { isEmpty = unit(false); value = x; rdr = next },
          next => rdr = next
        )

        if (isEmpty) that.apply(success, failure) else success(value, rdr)

      }
    }
*/

    override def orElse(that: => ParseResultCPS[T]): ParseResultCPS[T] = {
      var isEmpty = unit(true); var value = zeroVal[T]; var rdr = zeroVal[Input]

      self.apply[Unit](
        (x, next) => { isEmpty = unit(false); value = x; rdr = next },
        next => rdr = next
      )

      conditional(isEmpty, that, ParseResultCPS.Success(value, rdr))
    }
  }


  /**
   * Companion object
   */
  object ParseResultCPS {
    def Success[T: Typ](t: Rep[T], next: Rep[Input]) = new ParseResultCPS[T] {
      def apply[X: Typ](
        success: (Rep[T], Rep[Input]) => Rep[X],
        failure: (Rep[Input]) => Rep[X]
      ): Rep[X] = success(t, next)
    }

    def Failure[T: Typ](next: Rep[Input]) = new ParseResultCPS[T] {
      def apply[X: Typ](
        success: (Rep[T], Rep[Input]) => Rep[X],
        failure: (Rep[Input]) => Rep[X]
      ): Rep[X] = failure(next)
    }
  }

  /**
   * Conditional expression
   */
  def conditional[T: Typ](
    cond: Rep[Boolean],
    thenp: => ParseResultCPS[T],
    elsep: => ParseResultCPS[T]
  ): ParseResultCPS[T] = ParseResultCPSCond(cond, thenp, elsep)
}

trait ParseResultCPSExp
  extends ParseResultCPS
  with IfThenElseExp
  with BooleanOpsExp
  with OptionOpsExp
  with ZeroValExp { self: ReaderOps => }

trait ParseResultCPSExpOpt
  extends ParseResultCPSExp
  with IfThenElseExpOpt
  with BooleanOpsExpOpt
  with OptionOpsExpOpt { self: ReaderOps => }

trait ScalaGenParseResultCPS
    extends ScalaGenBase
    with ScalaGenIfThenElse
    with ScalaGenBooleanOps
    with ScalaGenOptionOps {
  val IR: ParseResultCPSExp
}
