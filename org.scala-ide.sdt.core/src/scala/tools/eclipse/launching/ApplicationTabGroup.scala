package scala.tools.eclipse.launching;
import org.eclipse.debug.ui._
import org.eclipse.debug.ui.sourcelookup._
import org.eclipse.jdt.debug.ui.launchConfigurations._
import org.eclipse.jdt.ui._

class ApplicationTabGroup extends AbstractLaunchConfigurationTabGroup {
  override def createTabs(dialog : ILaunchConfigurationDialog, mode : String) = {
    setTabs(Array[ILaunchConfigurationTab](
      new JavaMainTab(),
      new JavaArgumentsTab(),
      new JavaJRETab(),
      new JavaClasspathTab(),
      new SourceLookupTab(),
      new EnvironmentTab(),
      new CommonTab()
    ))
  }
  // to fill in later
  abstract class ScalaMainTab extends AbstractLaunchConfigurationTab {
    def getName = "Main"
    override def getImage = JavaUI.getSharedImages.getImage(ISharedImages.IMG_OBJS_CLASS)
  }
}
