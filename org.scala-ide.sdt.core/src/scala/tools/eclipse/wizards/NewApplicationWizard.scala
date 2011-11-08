package scala.tools.eclipse.wizards

import java.io.StringBufferInputStream

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scala.tools.nsc.util.Chars._
import scala.tools.eclipse.formatter.FormatterPreferences
import scala.tools.eclipse.util.EclipseUtils._
import scala.tools.eclipse.util.Utils._
import scala.tools.eclipse.util.HasLogger
import scala.tools.eclipse.ScalaPlugin
import scalariform.formatter.ScalaFormatter

import org.eclipse.core.resources._
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.ui.IDebugUIConstants
import org.eclipse.jdt.core._
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants
import org.eclipse.jface.viewers._
import org.eclipse.ui.ide.IDE
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard
import org.eclipse.ui.IWorkbench

object NewApplicationWizard {

  private val TEMPLATE_WITHOUT_APP =
    """object %s {
      |  def main(args: Array[String]) {
      |    
      |  }
      |}""".stripMargin

  private val TEMPLATE_WITH_APP =
    """object %s extends App {
      |
      |}""".stripMargin

}

class NewApplicationWizard extends BasicNewResourceWizard with HasLogger {

  import NewApplicationWizard._

  private var page: NewApplicationPage = _

  override def init(workbench: IWorkbench, currentSelection: IStructuredSelection) = {
    super.init(workbench, currentSelection)
    setWindowTitle("New Scala Application")
    setNeedsProgressMonitor(false)
  }

  override def addPages = {
    super.addPages()

    var selection = getSelection
    if (selection.isEmpty)
      selection = getCurrentEditorAsSelection getOrElse selection
    val packageFragments = getPackageFragments(selection)

    page = new NewApplicationPage(packageFragments)
    addPage(page)
  }

  private def createSource(applicationName: String, pkg: IPackageFragment): String = {
    val appExists = try { Class.forName("scala.App"); true } catch { case _ => false }
    val packageDeclaration = if (pkg.isDefaultPackage) "" else "package " + pkg.getElementName + "\n\n"
    val objectTemplate = if (appExists) TEMPLATE_WITH_APP else TEMPLATE_WITHOUT_APP
    val unformatted = packageDeclaration + objectTemplate.format(applicationName)
    ScalaFormatter.format(unformatted, FormatterPreferences.getPreferences(pkg.getResource.getProject))
  }

  private def createApplication(applicationName: String, pkg: IPackageFragment): Boolean = {
    val nameOk = applicationName.nonEmpty && isIdentifierStart(applicationName(0)) &&
      applicationName.tail.forall(isIdentifierPart)
    if (!nameOk) {
      page.setErrorMessage("Not a valid name.")
      return false
    }

    val file = pkg.getResource.asInstanceOf[IFolder].getFile(applicationName + ".scala")
    if (file.exists) {
      page.setErrorMessage("Resource with same name already exists.")
      return false
    }

    val source = createSource(applicationName, pkg)
    file.create(new StringBufferInputStream(source), true, null)
    openInEditor(file)
    addLaunchConfig(applicationName, pkg)
    true
  }

  override def performFinish: Boolean =
    tryExecute(createApplication(page.getApplicationName, page.getSelectedPackage))(orElse = false)

  private def openInEditor(file: IFile) = {
    selectAndReveal(file)
    for {
      workbenchWindow <- Option(getWorkbench.getActiveWorkbenchWindow)
      page <- Option(workbenchWindow.getActivePage)
    } IDE.openEditor(page, file, true)
  }

  private def addLaunchConfig(applicationName: String, pkg: IPackageFragment) {
    val project = ScalaPlugin.plugin.getScalaProject(pkg.getResource.getProject)
    val packageName = pkg.getElementName
    val packagePrefix = if (pkg.isDefaultPackage) "" else packageName + "."
    val typeName = packagePrefix + applicationName

    val launchManager = DebugPlugin.getDefault.getLaunchManager
    val launchName = launchManager.generateLaunchConfigurationName(typeName)
    val launchType = launchManager.getLaunchConfigurationType(ScalaPlugin.plugin.launchTypeId)

    val launchConfig = launchType.newInstance(null, launchName)
    launchConfig.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, project.underlying.getName)
    launchConfig.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, typeName)
    launchConfig.setAttribute(IDebugUIConstants.ATTR_FAVORITE_GROUPS, ArrayBuffer(IDebugUIConstants.ID_RUN_LAUNCH_GROUP))
    launchConfig.doSave()
  }

  private def getCurrentEditorAsSelection: Option[IStructuredSelection] =
    for {
      workbenchWindow <- Option(getWorkbench.getActiveWorkbenchWindow)
      page <- Option(workbenchWindow.getActivePage)
      editor <- Option(page.getActiveEditor)
      resource <- editor.getEditorInput.adaptToSafe[IResource]
    } yield new StructuredSelection(resource)

  private def getPackageFragments(selection: IStructuredSelection): List[IPackageFragment] =
    selection.iterator.toList match {
      case List(packageFragment: IPackageFragment) =>
        List(packageFragment)
      case _ =>
        (for {
          resource <- computeSelectedResources(selection)
          packageFragment <- getPackageFragments(resource.getProject)
        } yield packageFragment).distinct
    }

  private def getPackageFragments(project: IProject): List[IPackageFragment] =
    for {
      packageFragmentRoot <- ScalaPlugin.plugin.getJavaProject(project).getAllPackageFragmentRoots.toList
      if packageFragmentRoot.getKind == IPackageFragmentRoot.K_SOURCE
      child <- packageFragmentRoot.getChildren
      packageFragment <- child.asInstanceOfOpt[IPackageFragment]
    } yield packageFragment

}