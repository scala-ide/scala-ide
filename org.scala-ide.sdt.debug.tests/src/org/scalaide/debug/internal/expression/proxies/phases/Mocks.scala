/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.context.ValueProxifier
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.JdiProxyWrapper
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.CharJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.DoubleJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.FloatJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.LongJdiProxy

import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value

trait Mocks extends PrimitiveMocks {

  final case class MockCall(methodName: String, returnValue: JdiProxy, args: Any*)

  final case class MockVariable(name: String, tpe: String, calls: MockCall*) extends JdiProxy {
    override val underlying: ObjectReference = null

    var baseContext: JdiContext = _

    override lazy val context: JdiContext = baseContext
  }

  final case class MockJdiContext(
    variables: Set[MockVariable] = Set.empty,
    objects: Set[MockVariable] = Set.empty)
    extends JdiContext(null, null) {

    var calledObject: Set[MockVariable] = Set()

    var calledVals: Set[MockVariable] = Set()

    override def show(proxy: JdiProxy, withType: Boolean) = proxy match {
      case mv: MockVariable => mv.name
      case BoxedMock(value) => value.toString
      case wrapper: JdiProxyWrapper => show(wrapper.outer, withType)
    }

    override def getType(name: String): Option[String] =
      variables.find(_.name == name).map(_.tpe)

    override def getThisPackage: Option[String] = None

    private def initVariable(variable: MockVariable) {
      if (variable.baseContext == null) {
        variable.baseContext = this
        variable.calls.foreach(c => c.returnValue match {
          case v: MockVariable => initVariable(v)
          case _ =>
        })
      }
    }

    val realObjects = objects.map(old => MockVariable(old.name, JdiContext.toObject(old.tpe), old.calls: _*))

    realObjects.foreach(initVariable)
    variables.foreach(initVariable)

    override def objectProxy(name: String): JdiProxy = {
      val ret = realObjects.filter(_.name == name).headOption
        .getOrElse(throw new RuntimeException(s"object: $name not found"))
      calledObject += ret
      ret
    }

    override val currentThread: ThreadReference = null

    override def valueProxy(name: String): JdiProxy = {
      val ret = variables.filter(_.name == name)
        .head
      calledObject += ret
      ret
    }

    override def invokeMethod[Result <: JdiProxy](on: JdiProxy, name: String, args: Seq[Seq[JdiProxy]], implicits: Seq[JdiProxy]): Result = {

      def extractCalls(proxy: JdiProxy): Seq[MockCall] = proxy match {
        case m: MockVariable => m.calls
        case p: JdiProxyWrapper => extractCalls(p.outer)
      }

      val calls = extractCalls(on)
      val methods = calls.filter(m => m.methodName == name)
      val method = methods.head
      val ret = method.returnValue
      ret.asInstanceOf[Result]
    }

    // Mocking proxies
    override def proxy[ValueType, ProxyType](value: ValueType)(implicit proxifier: ValueProxifier[ValueType, ProxyType]): ProxyType = (value match {
      case v: String => StringMock(v)
      case v: Int => IntMock(v)
      case v: Char => CharMock(v)
      case v: Double => DoubleMock(v)
      case v: Float => FloatMock(v)
      case v: Long => LongMock(v)
      case v: Boolean => BooleanMock(v)
    }).asInstanceOf[ProxyType]
  }
}

trait PrimitiveMocks {

  trait BoxedMock {
    self: JdiProxy =>

    val value: Any
  }

  object BoxedMock {
    def unapply(mock: BoxedMock) = Some(mock.value)
  }

  class StringMock(val value: Any) extends StringJdiProxy(null, null) with BoxedMock

  object StringMock {
    def apply(value: Any) = new StringMock(value)
  }

  class IntMock(val value: Any) extends IntJdiProxy(null, null) with BoxedMock

  object IntMock {
    def apply(value: Any) = new IntMock(value)
  }

  class CharMock(val value: Any) extends CharJdiProxy(null, null) with BoxedMock

  object CharMock {
    def apply(value: Any) = new CharMock(value)
  }

  class DoubleMock(val value: Any) extends DoubleJdiProxy(null, null) with BoxedMock

  object DoubleMock {
    def apply(value: Any) = new DoubleMock(value)
  }

  class FloatMock(val value: Any) extends FloatJdiProxy(null, null) with BoxedMock

  object FloatMock {
    def apply(value: Any) = new FloatMock(value)
  }

  class LongMock(val value: Any) extends LongJdiProxy(null, null) with BoxedMock

  object LongMock {
    def apply(value: Any) = new LongMock(value)
  }

  class BooleanMock(val value: Any) extends BooleanJdiProxy(null, null) with BoxedMock

  object BooleanMock {
    def apply(value: Any) = new BooleanMock(value)
  }

}
