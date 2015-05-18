package org.scalaide.ui.internal.wizards

import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jface.text.Document
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.templates.GlobalTemplateVariables
import org.eclipse.jface.text.templates.Template
import org.eclipse.jface.text.templates.TemplateContext
import org.eclipse.jface.text.templates.TemplateVariableResolver
import org.eclipse.nebula.widgets.tablecombo.TableCombo
import org.eclipse.swt.SWT
import org.eclipse.swt.events.KeyAdapter
import org.eclipse.swt.events.KeyEvent
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.widgets.TableItem
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.ide.IDE
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.templates.ScalaTemplateContext
import org.scalaide.ui.internal.templates.ScalaTemplateManager
import org.scalaide.ui.wizards.Invalid
import org.scalaide.ui.wizards.Valid
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.util.eclipse.FileUtils
import org.scalaide.util.eclipse.OSGiUtils
import org.scalaide.util.internal.eclipse.ProjectUtils
import org.scalaide.util.internal.ui.AutoCompletionOverlay
import org.scalaide.util.internal.ui.Dialogs

/**
 * Wizard of the Scala IDE to create new files. It can not only create new
 * Scala files, but arbitrary ones as long as an extension exists for them.
 */
trait NewFileWizard extends AnyRef with HasLogger {

  private var btProject: Button = _
  private var cmTemplate: TableCombo = _
  private var tName: Text = _

  private var disposables = Seq[{def dispose(): Unit}]()
  /** See [[pathOfCreatedFile]] for the purpose of this variable. */
  private var file: IFile = _
  private var selectedFolder: IContainer = _
  private val fileCreatorMappings = FileCreatorMapping.mappings
  /** Code completion component for the text field. */
  private var completionOverlay: AutoCompletionOverlay = _

  /** The `Shell` to be used by this wizard. */
  def shell: Shell

  /** The id of the component which created this wizard. */
  def fileCreatorId: String

  /**
   * A type name, which is shown additionally to the default type path in the
   * type name text field immediately after creation of the wizard. The default
   * type path is computed by [[org.scalaide.ui.wizards.FileCreator#initialPath]]
   * of the given `fileCreatorId`.
   */
  def defaultTypeName: String

  /**
   * The ok button is not controlled by this wizard, therefore this method
   * allows to set the state of the ok button.
   */
  def enableOkButton(b: Boolean): Unit

  /**
   * Shows an error message in the wizard. If the error message should be
   * removed, an empty string needs to be passed.
   */
  def showErrorMessage(msg: String): Unit

  /**
   * Returns the path to the file created by the wizard. Returns `None` as long
   * as the wizard did not yet create a new file.
   */
  def createdFile: Option[IFile] =
    Option(file)

  def createContents(parent: Composite): Control = {
    import org.scalaide.util.eclipse.SWTUtils.fnToSelectionAdapter

    val c = new Composite(parent, SWT.NONE)
    c.setLayout(new GridLayout(2, false))
    c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))

    val lbTemplate = new Label(c, SWT.NONE)
    cmTemplate = new TableCombo(c, SWT.BORDER | SWT.READ_ONLY)
    val lbProject = new Label(c, SWT.NONE)
    btProject = new Button(c, SWT.BORDER | SWT.LEFT)
    val lbName = new Label(c, SWT.NONE)
    tName = new Text(c, SWT.BORDER)
    completionOverlay = new AutoCompletionOverlay(tName)

    lbName.setText("Name:")

    lbProject.setText("Source Folder:")

    btProject.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false))
    btProject.addSelectionListener { e: SelectionEvent =>
      Dialogs.chooseSourceFolderWithDialog(shell) foreach { srcPkg =>
        val project = srcPkg.getJavaProject().getProject()
        val srcDir = srcPkg.getPath().removeFirstSegments(1)
        setFolderName(project.getFolder(srcDir))
        validateInput()
      }
      tName.forceFocus()
    }

    tName.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false))
    tName.addKeyListener(new KeyAdapter {
      override def keyReleased(e: KeyEvent) = {

        def rotate(i: Int): Int = {
          val n = cmTemplate.getItemCount()
          val j = cmTemplate.getSelectionIndex() + i
          if (j < 0) j + n else j % n
        }

        def checkKeys() = {
          e.keyCode match {
            case SWT.ARROW_UP if cmTemplate.getItemCount() > 1 =>
              cmTemplate.select(rotate(-1))
            case SWT.ARROW_DOWN if cmTemplate.getItemCount() > 1 =>
              cmTemplate.select(rotate(1))
            case _ =>
          }
        }

        // CR is sent at creation of the wizard, we don't want to handle that one
        if (e.keyCode != SWT.CR) {
          if (!completionOverlay.isPopupOpened)
            checkKeys()
          validateInput()
        }
      }
    })

    lbTemplate.setText("Kind:")

    cmTemplate.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false))
    cmTemplate.addSelectionListener { e: SelectionEvent =>
      validateInput()
    }

    initComponents()
    c
  }

  private def initComponents() = {
    for {
      r <- ProjectUtils.resourceOfSelection()
      creator <- fileCreatorMappings.find(_.id == fileCreatorId)
      path <- creator.withInstance(_.initialPath(r))
    } {

      val srcDirs = ProjectUtils.sourceDirs(r.getProject())

      if (srcDirs.nonEmpty) {
        val fullPath = r.getFullPath()
        val root = ResourcesPlugin.getWorkspace().getRoot()

        val srcDir =
          if (fullPath.segmentCount() > 1)
            srcDirs.find(_.isPrefixOf(fullPath)).getOrElse(srcDirs.head)
          else
            srcDirs.head

        if (srcDir != r.getProject.getFullPath)
          setFolderName(root.getFolder(srcDir))
        else
          setFolderName(r.getProject)
      }

      val str = if (defaultTypeName.isEmpty) path else path + defaultTypeName
      tName.setText(str)
      tName.setSelection(str.length())
    }

    fileCreatorMappings.zipWithIndex foreach {
      case (m, i) =>
        val ti = new TableItem(cmTemplate.getTable(), SWT.NONE)
        ti.setText(m.name)
        val img = OSGiUtils.getImageDescriptorFromBundle(m.bundleId, m.iconPath).createImage()
        disposables +:= img
        ti.setImage(0, img)

        if (m.id == fileCreatorId)
          cmTemplate.select(i)
    }

    validateInput()
    selectedFileCreatorMapping.withInstance(_.showErrorMessageAtStartup) foreach { show =>
      if (!show)
        showErrorMessage("")
    }

    // select text field on wizard creation
    tName.forceFocus()
  }

  def dispose(): Unit = {
    disposables foreach { _.dispose() }
  }

  def okPressed(): Unit = {
    val m = selectedFileCreatorMapping

    object PackageVariableResolver extends TemplateVariableResolver {
      setType("package_name")
      setDescription("A dot separated name of the package")

      override def resolve(ctx: TemplateContext) =
        Option(super.resolve(ctx))
          .filter(_.nonEmpty)
          .map(pkg => s"package $pkg")
          .getOrElse("")
    }

    def applyTemplate(template: Template, ctx: ScalaTemplateContext) = {
      val doc = ctx.getDocument()
      doc.replace(0, 0, template.getPattern())

      val tb = ctx.evaluate(template)
      val vars = tb.getVariables()
      val replacements = vars flatMap { v =>
        val len = v.getName().length() + 3 // "${}".length = 3
        val value = v.getDefaultValue()
        v.getOffsets() map (off => (off, len, value))
      }

      replacements.sortBy(_._1) foreach {
        case (off, len, value) =>
          doc.replace(off, len, value)
      }

      val cursorPos = vars
          .find(_.getType() == GlobalTemplateVariables.Cursor.NAME)
          .map(_.getOffsets().head)
          .getOrElse(tb.getString().length())

      cursorPos
    }

    def createTemplateContext(doc: IDocument): ScalaTemplateContext = {
      val tm = new ScalaTemplateManager()
      val ctxType = tm.contextTypeRegistry.getContextType(tm.CONTEXT_TYPE)
      val ctx = new ScalaTemplateContext(ctxType, doc, 0, 0)

      ctx.getContextType().addResolver(PackageVariableResolver)
      m.withInstance(_.templateVariables(selectedFolder, chosenName)) foreach { vars =>
        for ((name, value) <- vars)
          ctx.setVariable(name, value)
      }
      ctx
    }

    val file = m.withInstance(_.create(selectedFolder, chosenName))
    file foreach { f =>
      FileUtils.createFile(f) match {
        case util.Success(_) =>
          this.file = f
          openEditor(f) { doc =>
            findTemplateById(m.templateId) match {
              case Some(template) =>
                val ctx = createTemplateContext(doc)
                applyTemplate(template, ctx)
              case _ =>
                eclipseLog.error(s"Template '${m.templateId}' not found. Creating an empty document.")
                0
            }
          }
        case util.Failure(f) =>
          eclipseLog.error("An error occurred while trying to create a file.", f)
      }
    }
  }

  /** Stores `folder` and shows it in the wizard. */
  private def setFolderName(folder: IContainer): Unit = {
    selectedFolder = folder
    btProject.setText(selectedFolder.getFullPath().makeRelative().toString())
  }

  /** The file name the user has inserted. */
  private def chosenName: String =
    tName.getText()

  /**
   * Returns the currently selected file creator mapping of the combo box. It
   * can safely be accessed because the combo box ensures that there is always a
   * selection.
   */
  private def selectedFileCreatorMapping: FileCreatorMapping = {
    val text = cmTemplate.getItem(cmTemplate.getSelectionIndex())
    fileCreatorMappings.find(_.name == text).get
  }

  /**
   * Validates the user inserted input and when the validation was invalid an
   * error message is shown and the ok-button is disbled.
   */
  private def validateInput(): Unit = {
    def handleError(msg: String) = {
      enableOkButton(msg.isEmpty())
      showErrorMessage(msg)
    }

    def validatedFileName =
      selectedFileCreatorMapping.withInstance {
        _.validateName(selectedFolder, chosenName)
      }

    if (selectedFolder == null)
      handleError("No folder selected")
    else
      validatedFileName foreach {
        case Valid =>
          handleError("")
          val completions = selectedFileCreatorMapping.withInstance(
              _.completionEntries(selectedFolder, chosenName))

          completions foreach completionOverlay.setProposals
        case Invalid(errorMsg) =>
          handleError(errorMsg)
      }
  }

  /**
   * Applies `f` to the document whose content is mapped to the newly created
   * file whose location is described by `file` and opens the file afterwards.
   * `f` needs to return the position where the cursor should point to after the
   * file is opened.
   */
  private def openEditor(file: IFile)(f: IDocument => Int): Unit = {

    def openFileInSrcDir(): Unit = {
      val doc = new Document()
      val cursorPos = f(doc)
      file.setContents(
          new java.io.ByteArrayInputStream(doc.get().getBytes()),
          /* force */ true, /* keepHistory */ false,
          new NullProgressMonitor)

      val window = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
      val e = IDE.openEditor(window.getActivePage(), file, /* activate */ true)
      BasicNewResourceWizard.selectAndReveal(file, window)
      EditorUtils.textEditor(e) foreach { _.selectAndReveal(cursorPos, 0) }
    }

    try openFileInSrcDir()
    catch {
      case e: Exception =>
        eclipseLog.error(s"Failed to initialize editor for file '$file'", e)
    }
  }

  private def findTemplateById(id: String): Option[Template] =
    Option(ScalaPlugin().templateManager.templateStore.findTemplateById(id))
}
