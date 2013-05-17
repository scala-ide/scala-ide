package scala.tools.eclipse.refactoring.method.ui

import org.eclipse.jface.viewers.ColumnLabelProvider
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredContentProvider
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.jface.viewers.Viewer
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage
import org.eclipse.swt.events.MouseAdapter
import org.eclipse.swt.events.MouseEvent
import org.eclipse.swt.events.MouseListener
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.SWT
import scala.tools.eclipse.refactoring.ScalaIdeRefactoring
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaPreviewerFactory
import scala.tools.eclipse.refactoring.method.MethodSignatureIdeRefactoring

/**
 * Generates the generic wizard page for method signature refactorings.
 * Subtraits only have to fill in the specific details for actual refactorings.
 */
trait MethodSignatureRefactoringConfigurationPageGenerator {

  // Using this self type we get access to refactoring.global
  this: ScalaIdeRefactoring =>

  import refactoring.global.{DefDef, ValDef}

  /**
   * Represents a separator of two parameter lists of in a method signature.
   * The parameter lists will be represented by List[Either[ValDef, ParamListSeparator]]
   */
  sealed trait ParamListSeparator
  /**
   * Represents a separator that existed before the refactoring.
   */
  case class OriginalSeparator(number: Int) extends ParamListSeparator
  /**
   * Represents a separator that was inserted by the refactoring.
   * @param paramListIndex Indicates in which parameter list the separater is inserted
   * @param splitPosition Indicates the position within the parameter list where the
   *        separator is inserted
   */
  case class InsertedSeparator(paramListIndex: Int, splitPosition: Int) extends ParamListSeparator

  /**
   * Convenience type to shorten type notation
   */
  type ParamOrSeparator = Either[ValDef, ParamListSeparator]

  type MSRefactoringParameters

  private[method] val refactoringCaption: String

  // Generates the wizard.
  def mkConfigPage(method: DefDef, paramsObs: MSRefactoringParameters => Unit): UserInputWizardPage

  /**
   * Generic wizard page for method signature refactorings.
   * Consists of:
   * - a descriptive header label
   * - a table displaying the parameters of all parameter lists
   * - two buttons to operate on the parameter table
   * - a preview of the refactored method signature
   */
  abstract class MethodSignatureRefactoringConfigurationPage(
      method: DefDef,
      paramsObs: MSRefactoringParameters => Unit) extends UserInputWizardPage(refactoringCaption) {

    // Descriptive header label text
    val headerLabelText: String

    // Captions of the buttons that operate on the parameter table
    val firstBtnText: String = "Split"
    val secondBtnText: String = "Merge"

    def createControl(parent: Composite) {
      initializeDialogUnits(parent)

      // this represents all parameter lists of the method
      var paramsWithSeparators: List[ParamOrSeparator] = intersperse(method.vparamss, nr => OriginalSeparator(nr))
      // function that provides the parameters to the parameter table
      val methodProvider: () => List[ParamOrSeparator] = () => paramsWithSeparators
      // the currently selected item in the parameter table
      var selection: ParamOrSeparator = Right(OriginalSeparator(0))

      type SelectedParamHandler = ParamOrSeparator => Unit

      // Convenience implicit conversion of SelectedParamHandlers to MouseListeners
      implicit def partial2MouseUpListener(f: SelectedParamHandler): MouseListener = new MouseAdapter {
        override def mouseUp(me: MouseEvent) {
          f(selection)
        }
      }

      val composite = new Composite(parent, SWT.NONE)

      val layout = new GridLayout(2, false)

      composite.setLayout(layout)

      val headerLabel = new Label(composite, SWT.WRAP)
      headerLabel.setText(headerLabelText)
      headerLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1))

      val paramsTable = new ParamsTable(composite, methodProvider)
      paramsTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 3))

      val firstBtn = new Button(composite, SWT.NONE)
      firstBtn.setText(firstBtnText)
      firstBtn.setEnabled(false)
      firstBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1))

      val secondBtn = new Button(composite, SWT.NONE)
      secondBtn.setText(secondBtnText)
      secondBtn.setEnabled(false)
      secondBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1))

      val spacingLabel = new Label(composite, SWT.NONE)
      spacingLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1))

      def setBtnStatesForSelection(sel: ParamOrSeparator): Unit = sel match {
        case Left(param) => setBtnStatesForParameter(param, paramsWithSeparators, firstBtn, secondBtn)
        case Right(separator) => setBtnStatesForSeparator(separator, paramsWithSeparators, firstBtn, secondBtn)
      }

      // Observer for selection changed events from the parameter table.
      // Calls setBtnStatesForParameter or setBtnStatesForSeparator to update
      // the button states when a parameter or a separator is selected, respectively.
      def selectionObs(structuredSel: IStructuredSelection) {
        if(structuredSel.size > 0) {
          structuredSel.getFirstElement match {
            case Left(param: ValDef) => {
              setBtnStatesForParameter(param, paramsWithSeparators, firstBtn, secondBtn)
              selection = Left(param)
            }
            case Right(separator: ParamListSeparator) => {
              setBtnStatesForSeparator(separator, paramsWithSeparators, firstBtn, secondBtn)
              selection = Right(separator)
            }
            case _ => // unknown item selected
          }
        }
      }
      paramsTable.selectionObs = selectionObs

      val methodPreview = ScalaPreviewerFactory.createPreviewer(
          composite,
          ScalaPlugin.plugin.getPreferenceStore,
          previewString(method, paramsWithSeparators))
      methodPreview.getControl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1))

      // triggers an update of the parameter table and everything that depends on it
      def updateParamsAndSeparators() {
        paramsTable.updateTable()
        methodPreview.getDocument.set(previewString(method, paramsWithSeparators))

        val parameters = computeParameters(paramsWithSeparators)
        paramsObs(parameters)
      }

      // listener for the first button, delegates to handleFirstBtn
      val firstBtnListener: SelectedParamHandler = selection => {
        paramsWithSeparators = handleFirstBtn(selection, paramsWithSeparators)
        updateParamsAndSeparators
        setBtnStatesForSelection(selection)
      }
      firstBtn.addMouseListener(firstBtnListener)

      // listener for the second button, delegates to handleSecondBtn
      val secondBtnListener: SelectedParamHandler = selection => {
        paramsWithSeparators = handleSecondBtn(selection, paramsWithSeparators)
        updateParamsAndSeparators
        setBtnStatesForSelection(selection)
      }
      secondBtn.addMouseListener(secondBtnListener)

      setControl(composite)
    }

    // responsible for setting the states of the two buttons when a parameter is selected in the parameter table
    def setBtnStatesForParameter(
      param: ValDef,
      paramsWithSeparators: List[ParamOrSeparator],
      firstBtn: Button,
      secondBtn: Button): Unit

    // responsible for setting the states of the two buttons when a ParamListSeparator is selected in the parameter table
    def setBtnStatesForSeparator(
      separator: ParamListSeparator,
      paramsWithSeparators: List[ParamOrSeparator],
      firstBtn: Button,
      seecondBtn: Button): Unit

    // responsible to compute the refactoring parameters that correspond to the current state of this wizard
    def computeParameters(paramsWithSeparators: List[ParamOrSeparator]): MSRefactoringParameters

    def handleFirstBtn(selection: ParamOrSeparator, paramsWithSeparators: List[ParamOrSeparator]): List[ParamOrSeparator]

    def handleSecondBtn(selection: ParamOrSeparator, paramsWithSeparators: List[ParamOrSeparator]): List[ParamOrSeparator]
  }

  /**
   * Flattens a List[List[A]] to List[Either[A, B]]. The original lists are separated
   * by elements of Right() and flattened to Left(A).
   */
  protected def intersperse[A, B](lists: List[List[A]], insertProvider: (Int) => B, nrInserted: Int = 1): List[Either[A, B]] = lists match {
    case Nil => Nil
    case ls::Nil => ls.map(Left(_))
    case ls::lss => ls.map(Left(_)) ::: Right(insertProvider(nrInserted))::intersperse(lss, insertProvider, nrInserted + 1)
  }

  /**
   * Reconstructs the list of parameter lists from the flattened withSeparators list.
   * Inverse operation to intersperse.
   */
  protected def extractParamLists(withSeparators: List[ParamOrSeparator]): List[List[ValDef]] = withSeparators match {
    case Nil => Nil
    case _ => {
      val vals = withSeparators.takeWhile(_.isLeft) collect { case Left(v) => v}
      val tail = withSeparators.dropWhile(_.isLeft).dropWhile(_.isRight)
      vals::extractParamLists(tail)
    }
  }

  /**
   * Finds the separator of type S that follows the given item of type A in the given list.
   */
  protected def followingSeparator[A, S](item: A, separatedItems: List[Either[A, S]]): Option[S] = {
    separatedItems.sliding(2).collect({ case Left(p)::Right(sep)::Nil if p == item => sep}).toList.headOption
  }

  /**
   * Finds the separator of type S that precedes the given item of type A in the given list.
   */
  protected def precedingSeparator[A, S](item: A, separatedItems: List[Either[A, S]]): Option[S] = {
    separatedItems.sliding(2).collect({case Right(sep)::Left(p)::Nil if p == item => sep}).toList.headOption
  }

  /**
   * Checks whether the given element is followed by a separator in the given list.
   */
  protected def isBeforeSeparator[A, S](item: A, separatedItems: List[Either[A, S]]): Boolean = {
    followingSeparator(item, separatedItems).isDefined
  }

  /**
   * Checks whether the given element is preceded by a separator in the given list.
   */
  protected def isAfterSeparator[A, S](item: A, separatedItems: List[Either[A, S]]): Boolean = {
    precedingSeparator(item, separatedItems).isDefined
  }

  /**
   * Checks whether the given parameter is the first one in its parameter list.
   */
  protected def isFirstInParamList(param: ValDef, paramsWithSeparators: List[ParamOrSeparator]): Boolean = {
    isAfterSeparator(param, Right(OriginalSeparator(0))::paramsWithSeparators)
  }

  /**
   * Checks whether the given parameter is the last one in its parameter list.
   */
  protected def isLastInParamList(param: ValDef, paramsWithSeparators: List[ParamOrSeparator]): Boolean = {
    isBeforeSeparator(param, paramsWithSeparators ::: List(Right(OriginalSeparator(0))))
  }

  /**
   * Checks if the parameter list can be splitted after the given parameter.
   */
  protected def isInSplitPosition(param: ValDef, paramsWithSeparators: List[ParamOrSeparator]): Boolean =
    !isLastInParamList(param, paramsWithSeparators)

  /**
   * Inserts the given separator after the given parameter in paramsWithSeparators.
   */
  protected def insertSeparatorAfter(
      param: ValDef,
      separator: ParamListSeparator,
      paramsWithSeparators: List[ParamOrSeparator]): List[ParamOrSeparator] = {
    paramsWithSeparators.foldRight(Nil: List[ParamOrSeparator])((el, acc) => el match {
      case Left(p) if p == param => el::Right(separator)::acc
      case _ => el::acc
    })
  }

  /**
   * Inserts a new separator after the given parameter in paramsWithSeparators.
   */
  protected def addSplitPositionAfter(param: ValDef, paramsWithSeparators: List[ParamOrSeparator]): List[ParamOrSeparator] = {
    def computePos(paramListIndex: Int, posCounter: Int, m: List[ParamOrSeparator]): Option[(Int, Int)] = m match {
      case Nil => None
      case Left(p)::ms if p == param => Some(paramListIndex, posCounter + 1)
      case Left(_)::ms => computePos(paramListIndex, posCounter + 1, ms)
      case Right(OriginalSeparator(_))::ms => computePos(paramListIndex + 1, 0, ms)
      case Right(InsertedSeparator(_, _))::ms => computePos(paramListIndex, posCounter, ms)
    }

    val posOpt = computePos(0, 0, paramsWithSeparators)
    posOpt.map(pos => insertSeparatorAfter(param, InsertedSeparator(pos._1, pos._2), paramsWithSeparators)) getOrElse paramsWithSeparators
  }

  /**
   * Removes the given separator from paramsWithSeparators
   */
  protected def removeSeparator(separator: ParamListSeparator, paramsWithSeparators: List[ParamOrSeparator]): List[ParamOrSeparator] = {
    paramsWithSeparators.filter(_ != Right(separator))
  }

  /**
   * Generates the preview string for the refactored method signature
   */
  protected def previewString(method: DefDef, paramsWithSeparators: List[ParamOrSeparator]): String = {
    val paramLists = extractParamLists(paramsWithSeparators)
    val paramListStrings = paramLists.map(params => params.map(p => p.name + ": " + p.tpt.symbol.name)).map(_.mkString(", "))
    "def " + method.name + "(" + paramListStrings.mkString(")(") + "): " + method.tpt.symbol.name
  }

  /**
   * The parameter table.
   */
  class ParamsTable(
      parent: Composite,
      methodProvider: () => List[ParamOrSeparator]) extends Composite(parent, SWT.NONE) {

    var selectionObs: IStructuredSelection => Unit = _

    private val viewer = new TableViewer(this, SWT.SINGLE | SWT.BORDER)

    setup()

    private def setup() {

      def mkTableViewerColumn(title: String) = {
        val viewerColumn = new TableViewerColumn(viewer, SWT.NONE)
        val column = viewerColumn.getColumn
        column.setText(title)
        column.setWidth(100)
        column.setResizable(true)
        column.setMoveable(true)
        viewerColumn
      }

      object ParamsContentProvider extends IStructuredContentProvider {
        override def getElements(paramsWithSeparators: AnyRef): Array[AnyRef] = {
          val elems = paramsWithSeparators.asInstanceOf[List[ParamOrSeparator]]
          Array(elems: _*)
        }
        override def inputChanged(viewer: Viewer, oldInput: Any, newInput: Any) {}
        override def dispose {}
      }

      val gridLayout = new GridLayout
      setLayout(gridLayout)

      val nameColumn = mkTableViewerColumn("Name")
      val typeColumn = mkTableViewerColumn("Type")

      nameColumn.setLabelProvider(new ColumnLabelProvider {
        override def getText(element: Any): String = {
          val row = element.asInstanceOf[ParamOrSeparator]
          row match {
            case Left(param) => param.symbol.nameString
            case Right(separator) => "-"
          }
        }
      })

      typeColumn.setLabelProvider(new ColumnLabelProvider {
        override def getText(element: Any): String = {
          val row = element.asInstanceOf[ParamOrSeparator]
          row match {
            case Left(param) => param.tpt.symbol.nameString
            // maybe display original/inserted separators differently
            case Right(separator) => separator match {
              case OriginalSeparator(_) => "-"
              case InsertedSeparator(_, _) => "-"
            }
          }
        }
      })

      val table = viewer.getTable
      table.setHeaderVisible(true)
      table.setLinesVisible(true)

      viewer.setContentProvider(ParamsContentProvider)
      viewer.getControl.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
      updateTable()

      viewer.addSelectionChangedListener(new ISelectionChangedListener {
        override def selectionChanged(event: SelectionChangedEvent) {
          event.getSelection match {
            case structuredSel: IStructuredSelection => selectionObs(structuredSel)
            case _ => // can't handle unstructured selections
          }
        }
      })
    }

    private[method] def updateTable() {
      viewer.setInput(methodProvider.apply)
      viewer.refresh()
    }
  }
}