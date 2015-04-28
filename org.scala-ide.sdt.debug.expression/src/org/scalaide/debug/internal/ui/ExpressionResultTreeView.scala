/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.ui

import scala.collection.JavaConverters.asScalaBufferConverter

import org.eclipse.debug.core.DebugException
import org.eclipse.debug.internal.ui.DebugPluginImages
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants
import org.eclipse.jface.viewers.DoubleClickEvent
import org.eclipse.jface.viewers.IDoubleClickListener
import org.eclipse.jface.viewers.ILazyTreeContentProvider
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.jface.viewers.Viewer
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.SashForm
import org.eclipse.swt.dnd.Clipboard
import org.eclipse.swt.dnd.TextTransfer
import org.eclipse.swt.events.MenuDetectEvent
import org.eclipse.swt.events.MenuDetectListener
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Menu
import org.eclipse.swt.widgets.MenuItem
import org.eclipse.swt.widgets.Tree
import org.eclipse.ui.ISharedImages
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.internal.console.ConsolePluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.scalaide.debug.internal.expression.TypeNames.javaNameToScalaName
import org.scalaide.debug.internal.model.ScalaArrayElementVariable
import org.scalaide.debug.internal.model.ScalaArrayReference
import org.scalaide.debug.internal.model.ScalaFieldVariable
import org.scalaide.debug.internal.model.ScalaLogicalStructureProvider
import org.scalaide.debug.internal.model.ScalaObjectReference
import org.scalaide.debug.internal.model.ScalaValue
import org.scalaide.debug.internal.model.ScalaVariable
import org.scalaide.debug.internal.model.ThreadNotSuspendedException
import org.scalaide.debug.internal.preferences.ExpressionEvaluatorPreferences
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.repl.StyledTextWithSimpleMenu
import org.scalaide.util.eclipse.SWTUtils

import com.sun.jdi.ClassNotLoadedException
import com.sun.jdi.Field
import com.sun.jdi.IncompatibleThreadStateException
import com.sun.jdi.VMDisconnectedException

/**
 * View which shows expandable tree for specified ObjectReference or uneditable text for given error message
 */
class ExpressionResultTreeView(parent: Composite) {

  import ExpressionResultTreeView._
  import SWTUtils._

  // orientation is not important in this place, it will be set using value from preferences
  protected val treeViewPanel = new SashForm(parent, SWT.VERTICAL)
  protected val treeViewer: TreeViewer = new TreeViewer(treeViewPanel, SWT.VIRTUAL | SWT.BORDER)
  protected val tree: Tree = treeViewer.getTree()

  protected val errorText: StyledTextWithSimpleMenu =
    new StyledTextWithSimpleMenu(treeViewPanel, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)

  protected var treeViewModel = TreeViewModel.empty

  initView()

  def setVisible(visible: Boolean): Unit = treeViewPanel.setVisible(visible)

  /**
   * Builds TreeViewModel once again using current expression evaluator preferences
   */
  def refresh(): Unit = treeViewer.refresh()

  /**
   * Shows tree view which allows us to drill down fields of specified object reference
   */
  def reloadWithResult(value: ScalaValue, rootLabel: String): Unit = withVisualizedExceptions {
    val treeViewModel = TreeViewModel(Some(value, rootLabel))
    treeViewer.setInput(treeViewModel)
    tree.setVisible(true)
    errorText.setVisible(false)
    refreshLayout()
  }

  /**
   * Shows text with error message and hides tree
   */
  def reloadWithErrorText(errorMessage: String): Unit = {
    showErrorMessage(errorMessage)
    clearTree()
    refreshLayout()
  }

  def clear(): Unit = {
    errorText.clear()
    clearTree()
  }

  def dispose(): Unit = {
    treeViewPanel.dispose()
    errorText.dispose()
  }

  protected def refreshLayout(): Unit = treeViewPanel.layout()

  protected def initView(): Unit = {
    treeViewPanel.setLayout(new FillLayout)

    initErrorTextWidget()
    initTreeWidget()
  }

  private def showErrorMessage(errorMessage: String): Unit = {
    errorText.setVisible(true)
    errorText.setText(errorMessage)
    tree.setVisible(false)
  }

  private def initErrorTextWidget(): Unit = {
    errorText.setAlwaysShowScrollBars(false)
    errorText.setEditable(false)
    errorText.setVisible(false)
    val errorTextColor = new Color(parent.getDisplay(), 128, 0, 64) // maroon
    errorText.setForeground(errorTextColor)
  }

  private def initTreeWidget(): Unit = {
    tree.setLayout(new FillLayout)
    treeViewer.setUseHashlookup(true)

    treeViewer.setContentProvider(createTreeContentProvider())
    treeViewer.setLabelProvider(createLabelProviderForTree())

    treeViewer.addDoubleClickListener(createDoubleClickListenerForTree())
    tree.addMenuDetectListener(createMenuDetectListenerForTree())
  }

  private def clearTree(): Unit = treeViewer.setInput(TreeViewModel.empty)

  private def createTreeContentProvider() =
    new ILazyTreeContentProvider {

      override def getParent(element: Object): Object =
        element match {
          case node: TreeNode => node.parent.getOrElse(treeViewModel)
          case _ => treeViewModel
        }

      override def updateElement(parent: Object, index: Int): Unit = withVisualizedExceptions {
        val element = parent.asInstanceOf[HasTreeNodeChildren].children(index)
        treeViewer.replace(parent, index, element)
        updateChildCount(element, -1)
      }

      override def updateChildCount(element: Object, currentChildCount: Int): Unit = withVisualizedExceptions {
        val parent = element.asInstanceOf[HasTreeNodeChildren]
        parent.scheduleChildrenUpdate()
        treeViewer.setChildCount(element, parent.childrenCount)
      }

      override def inputChanged(viewer: Viewer, oldInput: Object, newInput: Object): Unit = withVisualizedExceptions {
        treeViewModel = newInput.asInstanceOf[TreeViewModel]
      }

      override def dispose(): Unit = {}
    }

  private def createLabelProviderForTree() = {
    val sharedImages = PlatformUI.getWorkbench().getSharedImages()

    new LabelProvider() {
      private val rootImage = sharedImages.getImage(ISharedImages.IMG_OBJ_ELEMENT)
      private val intermediateNodeImage = DebugPluginImages.getImage(IInternalDebugUIConstants.IMG_ELCL_HIERARCHICAL)
      private val leafImage = sharedImages.getImage(ISharedImages.IMG_TOOL_FORWARD)
      private val infoImage = sharedImages.getImage(ISharedImages.IMG_OBJS_INFO_TSK)

      override def getText(element: Object) = element.asInstanceOf[TreeNode].label

      override def getImage(element: Object) = {
        val node = element.asInstanceOf[TreeNode]

        if (treeViewModel.children.contains(node)) rootImage
        else if (node.hasChildren) intermediateNodeImage
        else node match {
          case n: PlainTextNode if (n.onlyMessage) => infoImage
          case _ => leafImage
        }
      }
    }
  }

  /**
   * Listener shows/hides children after double click
   */
  private def createDoubleClickListenerForTree(): IDoubleClickListener = (event: DoubleClickEvent) => {
      val viewer = event.getViewer().asInstanceOf[TreeViewer]
      val selection = event.getSelection().asInstanceOf[IStructuredSelection]
      val selectedNode = selection.getFirstElement()
      viewer.setExpandedState(selectedNode, !viewer.getExpandedState(selectedNode))
    }

  /**
   * Listener creates menu related to current selected node
   */
  private def createMenuDetectListenerForTree() =
    new MenuDetectListener {
      private val copyImage = PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_TOOL_COPY)
      private val clearImage = ConsolePluginImages.getImage(IInternalConsoleConstants.IMG_ELCL_CLEAR)
      private val refreshImage = DebugPluginImages.getImage(IInternalDebugUIConstants.IMG_OBJS_REFRESH_TAB)

      override def menuDetected(event: MenuDetectEvent): Unit = {
        val selection = event.widget.asInstanceOf[Tree].getSelection()

        val oldMenu = Option(tree.getMenu())
        val menu = new Menu(tree)
        tree.setMenu(menu)
        oldMenu.foreach(_.dispose())

        if (!selection.isEmpty) {
          val selectedTree = selection.head

          val valueString = selectedTree.getData().asInstanceOf[TreeNode].valueString
          if (valueString != "") {
            val copyValueItem = new MenuItem(menu, SWT.NORMAL)
            copyValueItem.setText("Copy value")
            copyValueItem.setImage(copyImage)
            copyValueItem.addSelectionListener(() => copyToClipboard(valueString))
          }

          val copyLabelItem = new MenuItem(menu, SWT.NORMAL)
          copyLabelItem.setText("Copy label")
          copyLabelItem.setImage(copyImage)
          copyLabelItem.addSelectionListener(() => copyToClipboard(selectedTree.getText()))

          new MenuItem(menu, SWT.SEPARATOR)
        }

        val clearTreeItem = new MenuItem(menu, SWT.NORMAL)
        clearTreeItem.setText("Clear")
        clearTreeItem.setImage(clearImage)

        if (!treeViewModel.hasChildren)
          clearTreeItem.setEnabled(false)
        else {
          clearTreeItem.addSelectionListener(() => clear())

          val refreshTreeItem = new MenuItem(menu, SWT.PUSH)
          refreshTreeItem.setText("Refresh tree")
          refreshTreeItem.setImage(refreshImage)
          refreshTreeItem.addSelectionListener(() => refresh())
        }
      }

      private def copyToClipboard(text: String): Unit = {
        val clipboard = new Clipboard(parent.getDisplay())
        clipboard.setContents(Array(text), Array(TextTransfer.getInstance()))
        clipboard.dispose()
      }
    }

  private def withVisualizedExceptions[T](body: => T): T =
    try {
      body
    } catch {
      case e: Throwable =>
        showErrorMessage("Unexpected error occurred")
        refreshLayout()
        throw e // it will be shown in ErrorLog
    }
}

/**
 * Contains model for tree view which uses partially model for variables view so we have consistent names etc.
 */
object ExpressionResultTreeView extends HasLogger {
  import org.scalaide.debug.internal.expression.TypeNames.javaNameToScalaName

  private val sessionDisconnectedText = "Session disconnected"
  private val cannotCreateLogicalStructureText = "Cannot create logical structure"

  case class TreeViewModel(rootValueAndLabel: Option[(ScalaValue, String)]) extends HasTreeNodeChildren {

    override val childrenCount = rootValueAndLabel.map(_ => 1).getOrElse(0)

    override def createChildren() = rootValueAndLabel.map {
      case (value, label) => Seq(createNodeForScalaValue(value, label))
    }.getOrElse(Seq.empty)
  }

  object TreeViewModel {
    lazy val empty: TreeViewModel = TreeViewModel(rootValueAndLabel = None)
  }

  trait HasTreeNodeChildren {
    val childrenCount: Int

    private var shouldUpdateChildren = true
    private var cachedChildren: Seq[TreeNode] = Seq.empty

    def hasChildren: Boolean = childrenCount > 0
    def createChildren(): Seq[TreeNode]

    def children: Seq[TreeNode] = {
      if (shouldUpdateChildren) {
        cachedChildren = createChildren()
        shouldUpdateChildren = false
      }
      cachedChildren
    }

    def scheduleChildrenUpdate(): Unit = {
      shouldUpdateChildren = true
    }
  }

  sealed trait TreeNode extends HasTreeNodeChildren {
    val label: String

    /**
     * String representation of underlying object's value
     */
    val valueString: String

    val parent: Option[TreeNode]
  }

  abstract class ScalaValueNode(value: ScalaValue) extends TreeNode {
    override val valueString = value.getValueString()
  }

  /**
   * Default node implementation using ScalaValue. It's enough for cases other than ScalaObjectReference.
   */
  case class SimpleScalaValueNode(value: ScalaValue, label: String, parent: Option[TreeNode])
    extends ScalaValueNode(value) {

    override val childrenCount = 0
    override val createChildren = Seq.empty
  }

  case class ObjectReferenceNode(objectRef: ScalaObjectReference, label: String, parent: Option[TreeNode] = None,
      additionalMessageNodeLabel: Option[String] = None)
    extends ScalaValueNode(objectRef) {

    private val fieldsToShow = objectRef.wrapJDIException("Exception while retrieving object fields") {
      val showStaticFields = ExpressionEvaluatorPreferences.showStaticFieldsInTreeView
      val showSyntheticFields = ExpressionEvaluatorPreferences.showSyntheticFieldsInTreeView
      objectRef.underlying.referenceType().allFields().asScala.filter(fieldShouldBeShown(showStaticFields, showSyntheticFields))
    }

    private val realChildrenCount = fieldsToShow.length
    override val childrenCount = realChildrenCount + additionalMessageNodeLabel.map(_ => 1).getOrElse(0)

    override def createChildren() =
      additionalMessageNodeLabel.map(PlainTextNode(_, parent = Some(this), onlyMessage = true)).toSeq ++
      createRealChildren

    private def createRealChildren() = fieldsToShow.map(createChildrenForField).sortBy(_.label)

    private def createChildrenForField(field: Field): TreeNode = withHandledKnownExceptions {
      val scalaVariable = new ScalaFieldVariable(field, objectRef)
      createNodeForScalaVariable(scalaVariable, parent = this)
    }(s"${field.name()}: ${javaNameToScalaName(field.typeName())}", parent = Some(this))

    private def fieldShouldBeShown(showStaticFields: Boolean, showSyntheticFields: Boolean)(field: Field) =
      (!field.isStatic() || showStaticFields) && (!field.isSynthetic() || showSyntheticFields)
  }

  class ArrayLikeCollectionStructureNode private(collectionRef: ScalaObjectReference, val label: String,
      val parent: Option[TreeNode] = None, val arrayRef: ScalaArrayReference)
    extends ScalaValueNode(collectionRef)
    with GroupedArrayChildrenView

  object ArrayLikeCollectionStructureNode {
    def tryCreate(collectionRef: ScalaObjectReference, label: String, parent: Option[TreeNode] = None): ScalaValueNode =
      tryCallToArray(collectionRef) map { arrayRef =>
        new ArrayLikeCollectionStructureNode(collectionRef, label, parent, arrayRef)
      } getOrElse {
        ObjectReferenceNode(collectionRef, label, parent, additionalMessageNodeLabel = Some(cannotCreateLogicalStructureText))
      }
  }

  case class ArrayReferenceNode(arrayRef: ScalaArrayReference, label: String, parent: Option[TreeNode] = None)
    extends ScalaValueNode(arrayRef)
    with GroupedArrayChildrenView

  trait GroupedArrayChildrenView extends TreeNode {
    val arrayRef: ScalaArrayReference

    private val arrayLength = arrayRef.getSize()
    private val valuesGroupSize = ExpressionEvaluatorPreferences.collectionAndArrayValuesGroupSize

    override val childrenCount =
      if (arrayLength <= valuesGroupSize || valuesGroupSize == 1) arrayLength
      else math.ceil(arrayLength.toDouble / valuesGroupSize).toInt // numer of groups containing values

    override def createChildren() =
      if (hasGroups) createNestedChildrenStructureWithGroups()
      else ArrayElementsUtils.createFlatChildrenStructure(arrayRef, parent = this)

    private def createNestedChildrenStructureWithGroups(): Seq[TreeNode] =
      (0 to childrenCount).map { i =>
        val startChildIndex = i * valuesGroupSize
        val endChildIndex = math.min(startChildIndex + valuesGroupSize - 1, arrayLength - 1)
        createGroupForChildren(startChildIndex, endChildIndex)
      }

    private def createGroupForChildren(startChildIndex: Int, endChildIndex: Int) = {
      val childrenCountInGroup = endChildIndex - startChildIndex + 1
      GroupNode(s"[$startChildIndex - $endChildIndex]", Some(this), childrenCountInGroup)(
        ArrayElementsUtils.createChildren(arrayRef, startChildIndex, endChildIndex, parent = this))
    }

    private def hasGroups = childrenCount != arrayLength
  }

  /**
   * Loads at once certain specific, maximum count of values. Another ones can be loaded by expanding nested nodes.
   * @param traversableLikeRef collection for which we'll load first elements and we'll create nested node for another ones
   * @param visibleElemsArrayRef first part of collection after applying toArray on it
   * @param remainingCollectionPart second part of collection which can be expanded as nested node
   * @param label label shown in tree
   * @param parent parent node in tree
   * @param realStartIndex index of first child of this node in original collection
   */
  class IncrementalCollectionStructureNode private(traversableLikeRef: ScalaObjectReference, visibleElemsArrayRef: ScalaArrayReference,
      remainingCollectionPart: ScalaObjectReference, val label: String, val parent: Option[TreeNode] = None, realStartIndex: Int = 0)
    extends ScalaValueNode(traversableLikeRef) {

    private val visibleElemsArrayLength = visibleElemsArrayRef.getSize()
    private val hasFurtherElems = !ScalaLogicalStructureProvider.callIsEmpty(remainingCollectionPart)

    override val childrenCount = if (hasFurtherElems) 2 else visibleElemsArrayLength

    override def createChildren() =
      if (hasFurtherElems) createNestedChildrenStructure()
      else createFlatStructureForArray()

    private def createNestedChildrenStructure(): Seq[TreeNode] = {
      val increment = createNodeForIncrement()

      if (visibleElemsArrayLength == 1) // avoid creating group for the only one element
        createFlatStructureForArray() :+ increment
      else
        Seq(createGroupForChildren(), increment)
    }

    private def createFlatStructureForArray() =
      ArrayElementsUtils.createFlatChildrenStructure(visibleElemsArrayRef, this, indexLabelAddition = realStartIndex)

    private def createGroupForChildren() =
      new GroupNode(
        s"[${realStartIndex} - ${realStartIndex + visibleElemsArrayLength - 1}]",
        parent = Some(this),
        visibleElemsArrayLength)(
        ArrayElementsUtils.createChildren(visibleElemsArrayRef, 0, visibleElemsArrayLength - 1, parent = this, indexLabelAddition = realStartIndex))

    private def createNodeForIncrement(): TreeNode = {
      val newRealStartIndex = realStartIndex + visibleElemsArrayLength
      val childLabel = s"[$newRealStartIndex - ?]"
      withHandledKnownExceptions {
        IncrementalCollectionStructureNode.tryCreate(remainingCollectionPart, childLabel, Some(this), newRealStartIndex)
      }(childLabel, parent = Some(this))
    }
  }

  object IncrementalCollectionStructureNode {
    def tryCreate(traversableLikeRef: ScalaObjectReference, label: String, parent: Option[TreeNode] = None, realStartIndex: Int = 0): ScalaValueNode = {
      val loadedValuesGroupSize = ExpressionEvaluatorPreferences.collectionAndArrayValuesGroupSize
      val (collectionToShow, remainingCollectionPart) = ScalaLogicalStructureProvider.splitCollection(traversableLikeRef, loadedValuesGroupSize)

      tryCallToArray(collectionToShow) map { visibleElemsArrayRef =>
        new IncrementalCollectionStructureNode(traversableLikeRef, visibleElemsArrayRef, remainingCollectionPart, label, parent, realStartIndex)
      } getOrElse {
        ObjectReferenceNode(traversableLikeRef, label, parent, additionalMessageNodeLabel = Some(cannotCreateLogicalStructureText))
      }
    }
  }

  case class PlainTextNode(label: String, parent: Option[TreeNode], onlyMessage: Boolean = false) extends TreeNode {
    override val valueString = ""
    override val childrenCount = 0
    override val createChildren = Seq.empty
  }

  /**
   * Node type used to group certain de facto children of its parent
   */
  case class GroupNode(label: String, parent: Option[TreeNode], childrenCount: Int)(groupChildren: => Seq[TreeNode]) extends TreeNode {
    override val valueString = ""
    override def createChildren() = groupChildren
  }

  private def createNodeForScalaVariable(variable: ScalaVariable, parent: TreeNode, forcedVariableName: Option[String] = None): TreeNode = {
    val value = variable.getValue().asInstanceOf[ScalaValue]
    val valueType = value.getReferenceTypeName()
    val shownType = if (valueType == "null") javaNameToScalaName(variable.getReferenceTypeName()) else valueType
    val name = forcedVariableName.getOrElse(variable.getName())
    val label = s"$name: $shownType = ${value.getValueString()}"
    createNodeForScalaValue(value, label, Some(parent))
  }

  private def createNodeForScalaValue(value: ScalaValue, label: String, parent: Option[TreeNode] = None): TreeNode = withHandledKnownExceptions {
    value match {
      case arrayRef: ScalaArrayReference =>
        ArrayReferenceNode(arrayRef, label, parent)
      case objRef: ScalaObjectReference if shouldShowCollectionLogicalStructure(objRef) =>
        createProperCollectionNodeType(objRef, label, parent)
      case objRef: ScalaObjectReference =>
        ObjectReferenceNode(objRef, label, parent)
      case _ =>
        SimpleScalaValueNode(value, label, parent)
    }
  }(label, parent)

  private def createProperCollectionNodeType(collectionRef: ScalaObjectReference, label: String, parent: Option[TreeNode]): TreeNode = {
    if (ScalaLogicalStructureProvider.hasDefiniteSize(collectionRef)) { // List, Vector etc.
      ArrayLikeCollectionStructureNode.tryCreate(collectionRef, label, parent)
    } else if (ScalaLogicalStructureProvider.isTraversableLike(collectionRef)) // e.g. Stream
      IncrementalCollectionStructureNode.tryCreate(collectionRef, label, parent)
    else // e.g. some custom infinite collection
      ObjectReferenceNode(collectionRef, label, parent)
  }

  private object ArrayElementsUtils {

    def createFlatChildrenStructure(arrayRef: ScalaArrayReference, parent: TreeNode, indexLabelAddition: Int = 0): Seq[TreeNode] =
      createChildren(arrayRef, 0, arrayRef.getSize() - 1, parent, indexLabelAddition)

    def createChildren(arrayRef: ScalaArrayReference, startChildIndex: Int, endChildIndex: Int, parent: TreeNode, indexLabelAddition: Int = 0): Seq[TreeNode] =
      (startChildIndex to endChildIndex).map(createChildForValue(arrayRef, _, parent, indexLabelAddition))

    private def createChildForValue(arrayRef: ScalaArrayReference, childIndex: Int, parent: TreeNode, indexLabelAddition: Int): TreeNode = {
      val name = s"(${childIndex + indexLabelAddition})"
      withHandledKnownExceptions {
        val scalaVariable = new ScalaArrayElementVariable(childIndex, arrayRef)
        createNodeForScalaVariable(scalaVariable, parent, forcedVariableName = Some(name))
      }(name, Some(parent))
    }
  }

  private def shouldShowCollectionLogicalStructure(objRef: ScalaObjectReference) =
    ExpressionEvaluatorPreferences.showCollectionsLogicalStructure && ScalaLogicalStructureProvider.isScalaCollection(objRef)

  private def withHandledKnownExceptions(body: => TreeNode)(errorLabelPrefix: => String, parent: Option[TreeNode]): TreeNode =
    try {
      body
    } catch {
      case e: DebugException =>
        // for instance when user stopped debug mode
        PlainTextNode(s"$errorLabelPrefix - ${errorMessageForDebugException(e)}", parent)

      // Following exceptions are thrown during calling via JDI methods which are needed to show logical collections structure
      // (also to check whether ScalaObjectReference is a reference of collection at all) when there's problem related to selected thread.
      // It's JDI limitation related to thread changing during debug and cannot be fixed by us.
      case _: ThreadNotSuspendedException =>
        PlainTextNode(s"$errorLabelPrefix - Chosen thread is not suspended", parent)
      case _: IncompatibleThreadStateException =>
        PlainTextNode(s"$errorLabelPrefix - Problem with thread for which evaluation was processed (did you select other thread?)", parent)
    }

  private def errorMessageForDebugException(e: DebugException) =
    e.getCause() match {
      case _: VMDisconnectedException => sessionDisconnectedText
      case _ =>
        logger.error("Error during loading value", e)
        e.getMessage()
    }

  private def tryCallToArray(collectionRef: ScalaObjectReference): Option[ScalaArrayReference] =
    try {
      Some(ScalaLogicalStructureProvider.callToArray(collectionRef))
    } catch {
      case e: ClassNotLoadedException =>
        logger.error(s"Error during calling toArray on collection $collectionRef", e)
        None
    }
}
