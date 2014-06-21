package org.scalaide.util.internal

import java.lang.reflect.Field
import java.lang.reflect.Method

import scala.language.dynamics
import scala.reflect.runtime.{universe => u}
import scala.util.Try

/**
 * Enables reflection based accesses to an object `obj`, based on dynamic
 * calls. It is possible to access fields and methods of any visibility and
 * location (current class and superclasses). In case that anything goes wrong
 * an exception is thrown, it is therefore suggested to wrap any calls to this
 * class in a try-catch block or to use the already existing `apply` method,
 * which returns a [[scala.util.Try]].
 *
 * {{{
 * val ret: Try[String] = ReflectAccess(obj) apply { ra =>
 *   // get value of field `intValue` and cast it to `Int`
 *   val i = ra.intValue[Int]
 *   // set value of field `intValue` to `10`
 *   ra.intValue = 10
 *   // call the method `meth` with the arguments `a`, `b` and `c` and cast
 *   // the return value to `String`
 *   ra.meth[String](a, b, c)
 * }
 * }}}
 */
final case class ReflectAccess[A : u.TypeTag : reflect.ClassTag](obj: A) extends Dynamic {

  /** The class to the given object `obj`. */
  val cls = reflect.classTag[A].runtimeClass

  /**
   * Wraps the given block `f` in a [[scala.util.Try]] to prevent exceptions
   * of being thrown to the outside world.
   */
  def apply[B](f: ReflectAccess[A] => B): Try[B] =
    Try(f(this))

  /**
   * Returns all fields that exist in the given class. Finds all fields of
   * the superclasses, even if they are private.
   */
  private lazy val allFields: Seq[Field] = Iterator
    .iterate[Class[_]](cls)(_.getSuperclass())
    .takeWhile(_ != null)
    .flatMap(_.getDeclaredFields())
    .toVector

  /**
   * Returns all methods that exist in the given class. Finds all methods of
   * the superclases, even if they are private.
   */
  private lazy val allMethods: Seq[Method] = Iterator
    .iterate[Class[_]](cls)(_.getSuperclass())
    .takeWhile(_ != null)
    .flatMap(_.getDeclaredMethods())
    .toVector

  /**
   * Allows field accesses to get their values. Throws a `NoSuchFieldException`
   * if the field could not be found and a `ClassCastException` if the fields
   * value could not be casted to the indicated type.
   */
  def selectDynamic[B](name: String): B = {
    allFields.find(_.getName() == name) match {
      case Some(field) =>
        field.setAccessible(true)
        field.get(obj).asInstanceOf[B]

      case _ =>
        throw new NoSuchFieldException(
            s"""|The field '$name' of class '${cls.getName()}' must have changed its name,
                | it could not be found""".stripMargin.replace("\n", ""))
    }
  }

  /**
   * Allows field accesess to set their values. Throws a `NoSuchFieldException`
   * if the field could not be found and `IllegalArgumentException` if the
   * given value could not be casted to the fields type.
   */
  def updateDynamic(name: String)(value: Any): Unit = {
    allFields.find(_.getName() == name) match {
      case Some(field) =>
        field.setAccessible(true)
        field.set(obj, value)

      case _ =>
        throw new NoSuchFieldException(
            s"""|The field '$name' of class '${cls.getName()}' must have changed its name,
                | it could not be found""".stripMargin.replace("\n", ""))
    }
  }

  /**
   * Allows method accesses. Throws a `NoSuchMethodException` if the method
   * could not be found, a `ClassCastException` if the methods return value
   * could not be casted to the indicated type and an `IllegalArgumentException`
   * if the given values could not be casted to the methods expected
   * parameters.
   */
  def applyDynamic[B](name: String)(args: Any*): B = {
    def methodEq(m: Method): Boolean =
      m.getName() == name && m.getParameterTypes().toSeq == args.map(_.getClass())

    allMethods.find(methodEq) match {
      case Some(meth) =>
        meth.setAccessible(true)
        meth.invoke(obj, args map (_.asInstanceOf[AnyRef]): _*).asInstanceOf[B]

      case _ =>
        throw new NoSuchMethodException(
            s"""|The method '$name(${args.map(_.getClass().getName()).mkString(", ")})'
                | of class '${cls.getName()}' must have changed its name,
                | it could not be found""".stripMargin.replace("\n", ""))
    }
  }
}