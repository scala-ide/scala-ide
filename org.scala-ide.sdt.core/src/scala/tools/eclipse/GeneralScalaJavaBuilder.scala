package scala.tools.eclipse

import scala.tools.eclipse.contribution.weaving.jdt.builderoptions.ScalaJavaBuilder
import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.Platform
import java.lang.reflect.InvocationTargetException
import scala.tools.eclipse.util.ReflectionUtils

/** A ScalaJavaBuilder implementation that can work both on Eclipse Helios and
 *  Indigo (3.6 and 3.7). 
 *  
 *  The JavaBuilder in 3.7 introduces build configurations, so instead of setting
 *  the current project we need to create a BuildConfiguration and pass that around.
 * 
 *  We use reflection in order to choose one path over the other, depending on the
 *  JDT minor version. The alternative would be to branch the source code, but this
 *  ensures we can keep one code base.
 */
class GeneralScalaJavaBuilder extends ScalaJavaBuilder {
  lazy val JDTVersion = Platform.getBundle("org.eclipse.jdt.core").getVersion
  lazy val isHelios = JDTVersion.getMinor == 6
  lazy val isIndigo = JDTVersion.getMinor == 7

  // (Indigo) this sets a dummy BuildConfiguration and avoids an NPE in InternalBuilder.getProject
  setProject0(null)
  
  override def setProject0(project: IProject) {
    if (isHelios)
      setProjectHelios(project)
    else if (isIndigo)
      setProjectIndigo(project)
    else
      throw new RuntimeException("Unknown JDT version: " + JDTVersion)
  }
  
  private def setProjectHelios(project: IProject) {
    try ScalaJavaBuilderUtils.setProject(this, project)
    catch {
      case e: IllegalArgumentException =>
        throw new RuntimeException(e)
      case e: IllegalAccessException =>
        throw new RuntimeException(e);
      case e: InvocationTargetException =>
        throw new RuntimeException(e);
    }
  }
  
  private def setProjectIndigo(project: IProject) {
    ScalaJavaBuilderUtils.setBuildConfig(this, project)
  }
}

object ScalaJavaBuilderUtils extends ReflectionUtils {
  private lazy val ibClazz = Class.forName("org.eclipse.core.internal.events.InternalBuilder")
  private lazy val setProjectMethod = getDeclaredMethod(ibClazz, "setProject", classOf[IProject])
  
  private lazy val jbClazz = Class.forName("org.eclipse.jdt.internal.core.builder.JavaBuilder")
  private lazy val initializeBuilderMethod = getDeclaredMethod(jbClazz, "initializeBuilder", classOf[Int], classOf[Boolean])
  
  private lazy val IBuildConfigClass = Class.forName("org.eclipse.core.resources.IBuildConfiguration")
  private lazy val setBuildConfigMethod = getDeclaredMethod(ibClazz, "setBuildConfig", IBuildConfigClass)
  
  
  def setProject(builder : ScalaJavaBuilder, project : IProject) = setProjectMethod.invoke(builder, project)
  def initializeBuilder(builder : ScalaJavaBuilder, kind : Int, forBuild : Boolean) = initializeBuilderMethod.invoke(builder, int2Integer(kind), boolean2Boolean(forBuild))
  
  def setBuildConfig(builder: ScalaJavaBuilder, project: IProject) {
    val BuildConfigClass = Class.forName("org.eclipse.core.internal.resources.BuildConfiguration")
    val buildConfig = BuildConfigClass.getConstructor(classOf[IProject]).newInstance(project).asInstanceOf[AnyRef]
    setBuildConfigMethod.invoke(builder, buildConfig)
  }
}
