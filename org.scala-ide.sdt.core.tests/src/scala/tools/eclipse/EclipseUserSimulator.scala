package scala.tools.eclipse

import org.eclipse.core.internal.resources.{ Workspace, Project, File }
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.ui.PlatformUI;
import org.eclipse.jdt.core.search.SearchRequestor
import org.eclipse.jdt.core.search.SearchMatch

class EclipseUserSimulator {
  import org.eclipse.jdt.core._;

  var root: IPackageFragmentRoot = null;
  var workspace: IWorkspace = null;

  def createProjectInWorkspace(projectName: String, withSourceRoot: Boolean = true) = {
    import org.eclipse.core.resources.ResourcesPlugin;
    import org.eclipse.pde.internal.core.util.CoreUtility;
    import org.eclipse.jdt.internal.core.JavaProject;
    import org.eclipse.jdt.core._;
    import org.eclipse.jdt.launching.JavaRuntime;
    import scala.collection.mutable._;

    workspace = ResourcesPlugin.getWorkspace();
    val workspaceRoot = workspace.getRoot();
    val project = workspaceRoot.getProject(projectName);
    project.create(null);
    project.open(null);

    val description = project.getDescription();
    description.setNatureIds(Array(ScalaPlugin.plugin.natureId, JavaCore.NATURE_ID));
    project.setDescription(description, null);

    val javaProject = JavaCore.create(project);
    javaProject.setOutputLocation(new Path("/" + projectName + "/bin"), null);

    var entries = new ArrayBuffer[IClasspathEntry]();
    val vmInstall = JavaRuntime.getDefaultVMInstall();
    val locations = JavaRuntime.getLibraryLocations(vmInstall);
    for (element <- locations)
      entries += JavaCore.newLibraryEntry(element.getSystemLibraryPath(), null, null);

    if (withSourceRoot) {
      val sourceFolder = project.getFolder("/src");
      sourceFolder.create(false, true, null);
      root = javaProject.getPackageFragmentRoot(sourceFolder);
      entries += JavaCore.newSourceEntry(root.getPath());
    }
    entries += JavaCore.newContainerEntry(Path.fromPortableString(ScalaPlugin.plugin.scalaLibId))
    javaProject.setRawClasspath(entries.toArray[IClasspathEntry], null);

    ScalaPlugin.plugin.getScalaProject(project);
  }

  def createPackage(packageName: String) =
    root.createPackageFragment(packageName, false, null);

  def createCompilationUnit(pack: IPackageFragment, name: String, sourceCode: String) = {
    val cu = pack.createCompilationUnit(name, sourceCode, false, null);
    Thread.sleep(200)
    cu;
  }

  def buildWorkspace {
    import org.eclipse.core.internal.resources.Workspace;
    import org.eclipse.core.resources.IncrementalProjectBuilder;

    workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor())
  }

  var fileEditorInput: FileEditorInput = null;
  var currentEditor: ScalaSourceFileEditor = null;
  def openInEditor(compilationUnit: ICompilationUnit) = {
    import org.eclipse.ui.internal.WorkbenchPage;

    val activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    val editorManager = activePage.asInstanceOf[WorkbenchPage].getEditorManager()

    val root = workspace.getRoot()
    val path = workspace.asInstanceOf[Workspace].getFileSystemManager().locationFor(compilationUnit.getResource)

    fileEditorInput = new FileEditorInput(root.getFileForLocation(path));
    editorManager.openEditor("scala.tools.eclipse.ScalaSourceFileEditor",
      fileEditorInput,
      true, null)

    currentEditor = activePage.getActiveEditor().asInstanceOf[ScalaSourceFileEditor];
  }

  def saveCurrentEditor() =
    currentEditor.doSave(new NullProgressMonitor())

  def setContentOfCurrentEditor(code: String) =
    currentEditor.getDocumentProvider().getDocument(fileEditorInput).set(code)

  def searchType(typeName: String) = {
    import org.eclipse.jdt.core.search._
    val searchPattern = SearchPattern.createPattern(typeName,
      IJavaSearchConstants.CLASS,
      IJavaSearchConstants.REFERENCES,
      SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE);
    val engine = new SearchEngine;
    val requestor = new ClassSearchRequestor
    engine.search(searchPattern, Array(SearchEngine.getDefaultSearchParticipant()), SearchEngine.createWorkspaceScope, requestor, null)

    requestor.matches
  }

  class ClassSearchRequestor extends SearchRequestor {

    var matches = List.empty[SearchMatch]

    def acceptSearchMatch(sm: SearchMatch) {
      matches = sm :: matches;
    }
  }
}
