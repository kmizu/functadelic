package lms.util

import lms._

import scala.virtualization.lms.common._
import scala.virtualization.lms.internal.GenericCodegen
import scala.reflect.SourceContext

import java.io.PrintWriter

/**
 * Inspired from TupleOps is the delite-develop branch
 */

trait OptionOps extends Base with IfThenElse with BooleanOps with Equal {
  import scala.language.implicitConversions

  implicit def make_opt[A: Manifest](o: Option[Rep[A]])(implicit pos: SourceContext): Rep[Option[A]]

  def none[T: Manifest](): Rep[Option[T]]

  implicit class OptionOpsCls[A: Manifest](o: Rep[Option[A]]) {
    def map[B: Manifest](f: Rep[A] => Rep[B]) = option_map(o, f)
    def isDefined: Rep[Boolean] = option_isDefined(o)
    def get: Rep[A] = option_get(o)
    def flatMap[B: Manifest](f: Rep[A] => Rep[Option[B]]) = option_flatMap(o, f)
    def filter(f: Rep[A] => Rep[Boolean]) = option_filter(o, f)
  }

  def option_isDefined[A: Manifest](o: Rep[Option[A]])(implicit pos: SourceContext): Rep[Boolean]
  def option_get[A: Manifest](o: Rep[Option[A]])(implicit pos: SourceContext): Rep[A]

  def option_map[A: Manifest, B: Manifest](o: Rep[Option[A]], f: Rep[A] => Rep[B]): Rep[Option[B]] =
    if (o.isDefined) Some(f(o.get)) else None.asInstanceOf[Option[Rep[B]]]

  def option_flatMap[A: Manifest, B: Manifest](o: Rep[Option[A]], f: Rep[A] => Rep[Option[B]]): Rep[Option[B]] =
    if (o.isDefined) f(o.get) else None.asInstanceOf[Option[Rep[B]]]

  def option_filter[A: Manifest](o: Rep[Option[A]], p: Rep[A] => Rep[Boolean]): Rep[Option[A]] =
    if (o.isDefined && p(o.get)) o else None.asInstanceOf[Option[Rep[A]]]

}

/**
 * Deliberately not mixing in the opt traits
 * we only want them at the very end, when we generate the program
 */
trait OptionOpsExp
    extends OptionOps
    with IfThenElseExp
    with BooleanOpsExp
    with StructExp
    with EqualExp
    with CastingOpsExp {

  import scala.language.implicitConversions
  import lms.ZeroVal

  implicit def make_opt[A: Manifest](o: Option[Rep[A]])(implicit pos: SourceContext): Exp[Option[A]] =
    struct(classTag[Option[A]],
      "value" -> o.getOrElse(unit(ZeroVal[A])),
      "defined" -> unit(o.isDefined)
    )

  def option_isDefined[A: Manifest](o: Rep[Option[A]])(implicit pos: SourceContext): Rep[Boolean] = field[Boolean](o, "defined")
  def option_get[A: Manifest](o: Rep[Option[A]])(implicit pos: SourceContext): Rep[A] = field[A](o, "value")

  def none[T: Manifest](): Rep[Option[T]] =
    struct(classTag[Option[T]], "value" -> unit(ZeroVal[T]), "defined" -> unit(false))
}

trait OptionGenBase extends GenericCodegen with BaseGenStruct {
  val IR: OptionOpsExp

  override def remap[A](m: Manifest[A]) = m.erasure.getSimpleName match {
    case "Option" => IR.structName(m)
    case _ => super.remap(m)
  }
}

trait OptionOpsExpOpt
    extends OptionOpsExp
    with IfThenElseExpOpt
    with BooleanOpsExpOpt
    with StructExpOptCommon
    with EqualExpOpt

trait ScalaGenOptionOps
    extends ScalaGenBase
    with OptionGenBase
    with ScalaGenStruct
    with ScalaGenCastingOps
    with ScalaGenIfThenElse
    with ScalaGenEqual
    with ScalaGenBooleanOps {
  val IR: OptionOpsExp
}

trait CGenOptionOps
    extends CGenBase
    with OptionGenBase
    with CGenStruct
    with CGenCastingOps
    with CGenIfThenElse
    with CGenEqual
    with CGenBooleanOps {
  val IR: OptionOpsExp
}
