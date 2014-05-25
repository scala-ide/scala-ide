package org.scalaide.ui.internal.wizards

import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jface.dialogs.Dialog
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.text.ITextOperationTarget
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.templates.Template
import org.eclipse.jface.text.templates.TemplateContext
import org.eclipse.jface.text.templates.TemplateProposal
import org.eclipse.jface.text.templates.TemplateVariableResolver
import org.eclipse.nebula.widgets.tablecombo.TableCombo
import org.eclipse.swt.SWT
import org.eclipse.swt.events.KeyAdapter
import org.eclipse.swt.events.KeyEvent
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.Point
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
import org.scalaide.ui.internal.templates.ScalaTemplateManager
import org.scalaide.ui.wizards.Invalid
import org.scalaide.ui.wizards.Valid
import org.scalaide.util.internal.eclipse.ProjectUtils
import org.scalaide.util.internal.eclipse.SWTUtils
import org.scalaide.util.internal.ui.Dialogs

/**
 * Wizard of the Scala IDE to create new files. It can not only create new
 * Scala files, but arbitrary ones as long as an extension exists for them.
 *
 * @constructor Takes a `Shell` to run on and the id of the extension which
 * invoked the wizard. The template provided by this extension is used as the
 * default selection of the template selection choices.
 */
class NewFileWizard(shell: Shell, fileCreatorId: String) extends Dialog(shell) with HasLogger {

  private val Red = new Color(shell.getDisplay(), 255, 0, 0)
  private val TitleText = "New File Wizard"
  private val TemplateId = "org.scala-ide.sdt.core.templates"
  private val MinimalDialogSize = 500

  private var btOk: Button = _
  private var btProject: Button = _
  private var cmTemplate: TableCombo = _
  private var lbError: Label = _
  private var tName: Text = _

  private var disposables = Seq[{def dispose(): Unit}](Red)
  private var selectedProject: IJavaProject = _
  private val fileCreatorMappings = FileCreatorMapping.mappings

  override def createContents(parent: Composite): Control = {
    val c = super.createContents(parent)
    validateInput()
    // don't show any errors at wizard creation
    lbError.setText("")
    c
  }

  override def createDialogArea(parent: Composite): Control = {
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

      tName.setText(path)
      tName.setSelection(path.length())
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

    // select text field on wizard creation
    tName.forceFocus()
  }

  /** Overwritten in order to be able to access the ok button. */
  override def createButtonsForButtonBar(parent: Composite): Unit = {
    btOk = createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true)
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false)
  }

  /** Overwritten in order to set title text. */
  override def configureShell(sh: Shell): Unit = {
    super.configureShell(sh)
    sh.setText(TitleText)
  }

  /** Overwritten in order to be able to set a better dialog size. */
  override def getInitialSize(): Point = {
    val p = super.getInitialSize()
    p.x = math.max(MinimalDialogSize, p.x)
    p
  }

  override def close(): Boolean = {
    import scala.language.reflectiveCalls
    disposables foreach { _.dispose() }
    super.close()
  }

  override def okPressed(): Unit = {
    val m = selectedFileCreatorMapping

    object PackageVariableResolver extends TemplateVariableResolver {
      setType("package_name")
      setDescription("A dot separated name of the package")

      override def resolve(ctx: TemplateContext) = {
        val pkg = super.resolve(ctx)
        if (pkg.isEmpty()) "" else s"package $pkg"
      }
    }

    def createTemplateContext(template: Template, viewer: ITextViewer) = {
      val region = new Region(0, 0)
      val ctx = new ScalaTemplateManager().makeTemplateCompletionProcessor().createContext(viewer, region)
      ctx.getContextType().addResolver(PackageVariableResolver)

      val tp = new TemplateProposal(template, ctx, region, null)
      m.withInstance(_.templateVariables(selectedProject.getProject(), tName.getText())) foreach { vars =>
        for ((name, value) <- vars)
          ctx.setVariable(name, value)
      }
      tp.apply(viewer, 0, 0, 0)
    }

    val path = m.withInstance(_.createFileFromName(selectedProject.getProject(), tName.getText()))
    path foreach { p =>
      openEditor(p) { viewer =>
        findTemplateById(m.templateId) match {
          case Some(template) =>
            createTemplateContext(template, viewer)
          case _ =>
            eclipseLog.error(s"Template '${m.templateId}' not found. Creating an empty document.")
        }
      }
    }

    super.okPressed()
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
      btOk.setEnabled(false)
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
          btOk.setEnabled(true)
          lbError.setText("")
        case Invalid(errorMsg) =>
          showError(errorMsg)
      }
  }

  /**
   * Opens the file of a given path in an editor and applies `f` if the opening
   * succeeded.
   */
  private def openEditor(path: IPath)(f: ITextViewer => Unit): Unit = {
    val page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
    val file = IDEWorkbenchPlugin.getPluginWorkspace().getRoot().getFile(path)

    try {
      val e = IDE.openEditor(page, file, /* activate */ true)
      f(e.getAdapter(classOf[ITextOperationTarget]).asInstanceOf[ITextViewer])
    }
    catch {
      case e: PartInitException =>
        eclipseLog.error(s"Failed to initialize editor for file '$file'", e)
        None
    }
  }

  private def findTemplateById(id: String): Option[Template] =
    Option(ScalaPlugin.plugin.templateManager.templateStore.findTemplateById(id))
}