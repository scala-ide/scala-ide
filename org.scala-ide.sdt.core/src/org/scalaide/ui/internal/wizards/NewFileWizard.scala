package org.scalaide.ui.internal.wizards

import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IJavaProject
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
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.widgets.TableItem
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.PartInitException
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.ide.IDE
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin
import org.scalaide.core.ScalaPlugin
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.ScalaImages
import org.scalaide.ui.internal.templates.ScalaTemplateContext
import org.scalaide.ui.internal.templates.ScalaTemplateManager
import org.scalaide.ui.wizards.Invalid
import org.scalaide.ui.wizards.Valid
import org.scalaide.util.internal.eclipse.EditorUtils
import org.scalaide.util.internal.eclipse.ProjectUtils
import org.scalaide.util.internal.eclipse.SWTUtils
import org.scalaide.util.internal.ui.Dialogs

/**
 * Wizard of the Scala IDE to create new files. It can not only create new
 * Scala files, but arbitrary ones as long as an extension exists for them.
 */
trait NewFileWizard extends AnyRef with HasLogger {

  private val Red = new Color(shell.getDisplay(), 255, 0, 0)

  private var btProject: Button = _
  private var cmTemplate: TableCombo = _
  private var lbError: Label = _
  private var tName: Text = _

  private var disposables = Seq[{def dispose(): Unit}](Red)
  /** See [[pathOfCreatedFile]] for the purpose of this variable. */
  private var filePath: IPath = _
  private var selectedProject: IJavaProject = _
  private val fileCreatorMappings = FileCreatorMapping.mappings

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
   * Returns the path to the file created by the wizard. Returns `None` as long
   * as the wizard did not yet create a new file.
   */
  def pathOfCreatedFile: Option[IPath] =
    Option(filePath)

  def createContents(parent: Composite): Control = {
    import SWTUtils._

    val c = new Composite(parent, SWT.NONE)
    c.setLayout(new GridLayout(2, false))
    c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))

    val lbProject = new Label(c, SWT.NONE)
    btProject = new Button(c, SWT.BORDER | SWT.LEFT)
    val lbName = new Label(c, SWT.NONE)
    tName = new Text(c, SWT.BORDER)
    val lbTemplate = new Label(c, SWT.NONE)
    cmTemplate = new TableCombo(c, SWT.BORDER | SWT.READ_ONLY)
    lbError = new Label(c, SWT.NONE)

    lbName.setText("Name:")

    lbProject.setText("Project:")

    btProject.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false))
    btProject.addSelectionListener { e: SelectionEvent =>
      Dialogs.chooseProjectWithDialog(shell) foreach { p =>
        selectedProject = p
        btProject.setText(p.getProject().getName())
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

    lbError.setLayoutData({
      val l = new GridData(SWT.FILL, SWT.FILL, true, false)
      l.horizontalSpan = 2
      l
    })
    lbError.setForeground(Red)

    initComponents()
    c
  }

  private def initComponents() = {
    for {
      r <- ProjectUtils.resourceOfSelection()
      p <- ProjectUtils.projectAsJavaProject(r.getProject())
      creator <- fileCreatorMappings.find(_.id == fileCreatorId)
      path <- creator.withInstance(_.initialPath(r))
    } {
      selectedProject = p

      btProject.setText(p.getProject().getName())

      val str = if (defaultTypeName.isEmpty) path else path + defaultTypeName
      tName.setText(str)
      tName.setSelection(str.length())
    }

    fileCreatorMappings.zipWithIndex foreach {
      case (m, i) =>
        val ti = new TableItem(cmTemplate.getTable(), SWT.NONE)
        ti.setText(m.name)
        val img = ScalaImages.fromCoreBundle(m.iconPath).createImage()
        disposables +:= img
        ti.setImage(0, img)

        if (m.id == fileCreatorId)
          cmTemplate.select(i)
    }

    validateInput()
    selectedFileCreatorMapping.withInstance(_.showErrorMessageAtStartup) foreach { show =>
      if (!show)
        lbError.setText("")
    }

    // select text field on wizard creation
    tName.forceFocus()
  }

  def dispose(): Unit = {
    import scala.language.reflectiveCalls
    disposables foreach { _.dispose() }
  }

  def okPressed(): Unit = {
    val m = selectedFileCreatorMapping

    object PackageVariableResolver extends TemplateVariableResolver {
      setType("package_name")
      setDescription("A dot separated name of the package")

      override def resolve(ctx: TemplateContext) = {
        val pkg = super.resolve(ctx)
        if (pkg.isEmpty()) "" else s"package $pkg"
      }
    }

    def applyTemplate(template: Template, ctx: ScalaTemplateContext) = {
      val doc = ctx.getDocument()
      doc.replace(0, 0, template.getPattern())

      val tb = ctx.evaluate(template)
      val vars = tb.getVariables()
      val replacements = vars flatMap { v =>
        val len = v.getName().length() + "${}".length()
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
      m.withInstance(_.templateVariables(selectedProject.getProject(), tName.getText())) foreach { vars =>
        for ((name, value) <- vars)
          ctx.setVariable(name, value)
      }
      ctx
    }

    val path = m.withInstance(_.createFileFromName(selectedProject.getProject(), tName.getText()))
    path foreach { p =>
      filePath = p
      openEditor(p) { doc =>
        findTemplateById(m.templateId) match {
          case Some(template) =>
            val ctx = createTemplateContext(doc)
            applyTemplate(template, ctx)
          case _ =>
            eclipseLog.error(s"Template '${m.templateId}' not found. Creating an empty document.")
            0
        }
      }
    }
  }

  /**
   * Returns the currently selected file creator mapping of the combo box. It
   * can safely be accessed because the combo box ensures that there is always a
   * selection.
   */
  private def selectedFileCreatorMapping = {
    val text = cmTemplate.getItem(cmTemplate.getSelectionIndex())
    fileCreatorMappings.find(_.name == text).get
  }

  /**
   * Validates the user inserted input and when the validation was invalid an
   * error message is shown and the ok-button is disbled.
   */
  private def validateInput() = {
    def showError(msg: String) = {
      enableOkButton(false)
      lbError.setText(msg)
    }

    def validatedFileName =
      selectedFileCreatorMapping.withInstance {
        _.validateName(selectedProject.getProject(), tName.getText())
      }

    if (selectedProject == null)
      showError("No project selected")
    else
      validatedFileName foreach {
        case Valid =>
          enableOkButton(true)
          lbError.setText("")
        case Invalid(errorMsg) =>
          showError(errorMsg)
      }
  }

  /**
   * Applies `f` to the document whose content is mapped to the newly created
   * file whose location is described by `path` and opens the file afterwards.
   * `f` needs to return the position where the cursor should point to after the
   * file is opened.
   */
  private def openEditor(path: IPath)(f: IDocument => Int): Unit = {
    val page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
    val file = IDEWorkbenchPlugin.getPluginWorkspace().getRoot().getFile(path)

    try {
      val doc = new Document()
      val cursorPos = f(doc)
      file.setContents(
          new java.io.ByteArrayInputStream(doc.get().getBytes()),
          /* force */ true, /* keepHistory*/ false,
          new NullProgressMonitor)

      val e = IDE.openEditor(page, file, /* activate */ true)
      EditorUtils.textEditor(e) foreach { _.selectAndReveal(cursorPos, 0) }
    }
    catch {
      case e: PartInitException =>
        eclipseLog.error(s"Failed to initialize editor for file '$file'", e)
    }
  }

  private def findTemplateById(id: String): Option[Template] =
    Option(ScalaPlugin.plugin.templateManager.templateStore.findTemplateById(id))
}