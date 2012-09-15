package scala.tools.eclipse

import scala.tools.eclipse.contribution.weaving.jdt.builderoptions.ScalaJavaBuilder
import org.eclipse.core.resources.IProject
import org.eclipse.core.internal.resources.BuildConfiguration
import org.eclipse.core.runtime.Platform
import java.lang.reflect.InvocationTargetException
import scala.tools.eclipse.util.ReflectionUtils

/**
 *  
 *  The JavaBuilder in 3.7 introduces build configurations, so instead of setting
 *  the current project we need to create a BuildConfiguration and pass that around.
 * 
 */
class GeneralScalaJavaBuilder extends ScalaJavaBuilder {
  // (Indigo) this sets a dummy BuildConfiguration and avoids an NPE in InternalBuilder.getProject
  setProject0(null)
  
  override def setProject0(project: IProject) {
    ScalaJavaBuilderUtils.setBuildConfig(this, project)
  }
}

object ScalaJavaBuilderUtils extends ReflectionUtils {
  private lazy val ibClazz = Class.forName("org.eclipse.core.internal.events.InternalBuilder")
  
  private lazy val jbClazz = Class.forName("org.eclipse.jdt.internal.core.builder.JavaBuilder")
  private lazy val initializeBuilderMethod = getDeclaredMethod(jbClazz, "initializeBuilder", classOf[Int], classOf[Boolean])
  
  private lazy val IBuildConfigClass = Class.forName("org.eclipse.core.resources.IBuildConfiguration")
  private lazy val setBuildConfigMethod = getDeclaredMethod(ibClazz, "setBuildConfig", IBuildConfigClass)
  
  def initializeBuilder(builder : ScalaJavaBuilder, kind : Int, forBuild : Boolean) = initializeBuilderMethod.invoke(builder, int2Integer(kind), boolean2Boolean(forBuild))
  
  def setBuildConfig(builder: ScalaJavaBuilder, project: IProject) {
    val buildConfig = new BuildConfiguration(project)
    setBuildConfigMethod.invoke(builder, buildConfig)
  }
}
