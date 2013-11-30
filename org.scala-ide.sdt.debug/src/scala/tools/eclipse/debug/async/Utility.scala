package scala.tools.eclipse.debug.async

import com.sun.jdi.ReferenceType
import com.sun.jdi.Method
import scala.collection.JavaConverters._
import scala.tools.eclipse.debug.model.JdiRequestFactory
import scala.tools.eclipse.debug.model.ScalaDebugTarget
import scala.tools.eclipse.logging.HasLogger
import scala.actors.Actor

object Utility extends HasLogger {

  def installMethodBreakpoint(debugTarget: ScalaDebugTarget, app: AsyncProgramPoint, actor: Actor) {
    def isAPP(m: Method): Boolean =
      (!m.isAbstract()
        && m.name().startsWith(app.methodName)
        && !m.name().contains("$default"))

    for {
      tpe <- debugTarget.virtualMachine.classesByName(app.className).asScala
    } {
      val method = tpe.allMethods().asScala.find(isAPP)
      method.foreach { meth =>
        val req = JdiRequestFactory.createMethodEntryBreakpoint(method.get, debugTarget)
        debugTarget.eventDispatcher.setActorFor(actor, req)
        req.putProperty("app", app)
        req.enable()

        logger.debug(s"Installed method breakpoint for ${method.get.declaringType()}.${method.get.name}")
      }
    }
  }
}