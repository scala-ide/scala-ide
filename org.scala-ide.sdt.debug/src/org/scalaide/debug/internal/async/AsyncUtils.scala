package org.scalaide.debug.internal.async

import scala.collection.JavaConverters._

import org.scalaide.debug.internal.model.JdiRequestFactory
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.Suppress

import com.sun.jdi.Method

import com.sun.jdi.ReferenceType
import com.sun.jdi.ThreadReference
import com.sun.jdi.request.BreakpointRequest

object AsyncUtils extends HasLogger {

  def findAsyncProgramPoint(app: AsyncProgramPoint, tpe: ReferenceType): Option[Method] = {
    def isAsyncProgramPoint(m: Method): Boolean =
      (!m.isAbstract()
        && m.name().startsWith(app.methodName)
        && !m.name().contains("$default"))

     tpe.allMethods().asScala.find(isAsyncProgramPoint)
  }

  def installMethodBreakpoint(
      debugTarget: ScalaDebugTarget,
      app: AsyncProgramPoint,
      actor: Suppress.DeprecatedWarning.Actor,
      threadRef: ThreadReference = null
      ): Seq[BreakpointRequest] = {

    for {
      tpe <- debugTarget.virtualMachine.classesByName(app.className).asScala.toSeq
      method <- findAsyncProgramPoint(app, tpe)
    } yield {
      val req = JdiRequestFactory.createMethodEntryBreakpoint(method, debugTarget)
      debugTarget.eventDispatcher.setActorFor(actor, req)
      req.putProperty("app", app)
      if (threadRef ne null)
        req.addThreadFilter(threadRef)
      req.enable()

      logger.debug(s"Installed method breakpoint for ${method.declaringType()}.${method.name}")
      req
    }
  }
}
