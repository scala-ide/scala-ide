/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.ui

import scala.collection.JavaConversions.asScalaBuffer

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
import org.scalaide.debug.internal.ScalaDebugPlugin
import org.scalaide.debug.internal.preferences.DebuggerPreferencePage
import org.scalaide.ui.internal.repl.SimpleSelectionListener
import org.scalaide.ui.internal.repl.StyledTextWithSimpleMenu

import com.sun.jdi.ArrayReference
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.Value

/**
 * View which shows expandable tree for specified ObjectReference or uneditable text for given error message
 */
class ExpressionResultTreeView(parent: Composite) {
  // TODO add some nice presentation for collections (wich can be turned off if someone really wants to do that)

  import ExpressionResultTreeView.TreeNode
  import ExpressionResultTreeView.ValueNode
  import ExpressionResultTreeView.ObjectReferenceNode
  import ExpressionResultTreeView.ArrayReferenceNode

  // orientation is not important in this place, it will be set using value from preferences
  protected val treeViewPanel = new SashForm(parent, SWT.VERTICAL)
  protected val treeViewer: TreeViewer = new TreeViewer(treeViewPanel, SWT.VIRTUAL | SWT.BORDER)
  protected val tree: Tree = treeViewer.getTree()

  protected val errorText: StyledTextWithSimpleMenu =
    new StyledTextWithSimpleMenu(treeViewPanel, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)

  // we need one layer over the root which is different than root itself
  // by the way it let us have more than one top level node but we don't use that
  protected var rootNodes: Seq[TreeNode] = null

  initView()

  def setVisible(visible: Boolean): Unit = treeViewPanel.setVisible(visible)

  /**
   * Shows tree view which allows us to drill down fields of specified object reference
   */
  def reloadWithResult(treeRoot: ObjectReference, outputText: Option[String]): Unit = {
    val rootNode = treeRoot match {
      case arrayRef: ArrayReference => ArrayReferenceNode(arrayRef, forcedLabel = outputText)
      case _ => ObjectReferenceNode(objectRef = treeRoot, forcedLabel = outputText)
    }
    treeViewer.setInput(Seq(rootNode))
    tree.setVisible(true)
    errorText.setVisible(false)
    refreshLayout()
  }

  /**
   * Shows text with error message and hides tree
   */
  def reloadWithErrorText(errorMessage: String): Unit = {
    errorText.setVisible(true)
    errorText.setText(errorMessage)
    tree.setVisible(false)
    clearTree()
    refreshLayout()
  }

  def clear(): Unit = {
    errorText.clear()
    clearTree()
  }

  protected def refreshLayout(): Unit = treeViewPanel.layout()

  protected def initView(): Unit = {
    treeViewPanel.setLayout(new FillLayout)

    initErrorTextWidget()
    initTreeWidget()
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

  private def clearTree(): Unit = treeViewer.setInput(Seq.empty)

  private def createTreeContentProvider() =
    new ILazyTreeContentProvider {

      override def getParent(element: Object): Object = element match {
        case node: TreeNode => node.parent.getOrElse(rootNodes)
        case _ => rootNodes
      }

      override def updateElement(parent: Object, index: Int): Unit = {
        val element = parent match {
          case node: TreeNode => node.children(index)
          case _ => rootNodes(index)
        }
        treeViewer.replace(parent, index, element)
        updateChildCount(element, -1)
      }

      override def updateChildCount(element: Object, currentChildCount: Int): Unit = {
        val childrenCount = element match {
          case node: TreeNode => node.childrenCount
          case _ => rootNodes.length
        }
        treeViewer.setChildCount(element, childrenCount)
      }

      override def inputChanged(viewer: Viewer, oldInput: Object, newInput: Object): Unit = {
        rootNodes = newInput.asInstanceOf[Seq[TreeNode]]
      }

      override def dispose(): Unit = {}
    }

  private def createLabelProviderForTree() = {
    val sharedImages = PlatformUI.getWorkbench().getSharedImages()

    new LabelProvider() {
      private val rootImage = sharedImages.getImage(ISharedImages.IMG_OBJ_ELEMENT)
      private val intermediateNodeImage = DebugPluginImages.getImage(IInternalDebugUIConstants.IMG_ELCL_HIERARCHICAL)
      private val leafImage = sharedImages.getImage(ISharedImages.IMG_TOOL_FORWARD)

      override def getText(element: Object) = element.asInstanceOf[TreeNode].label

      override def getImage(element: Object) = {
        val node = element.asInstanceOf[TreeNode]

        if (rootNodes.contains(node)) rootImage
        else if (node.hasChildren) intermediateNodeImage
        else leafImage
      }
    }
  }

  /**
   * Listener shows/hides children after double click
   */
  private def createDoubleClickListenerForTree() =
    new IDoubleClickListener() {
      override def doubleClick(event: DoubleClickEvent): Unit = {
        val viewer = event.getViewer().asInstanceOf[TreeViewer]
        val selection = event.getSelection().asInstanceOf[IStructuredSelection]
        val selectedNode = selection.getFirstElement()
        viewer.setExpandedState(selectedNode, !viewer.getExpandedState(selectedNode))
      }
    }

  /**
   * Listener creates menu related to current selected node
   */
  private def createMenuDetectListenerForTree() =
    new MenuDetectListener {
      private val clipboard: Clipboard = new Clipboard(parent.getDisplay())
      private val copyImage = PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_TOOL_COPY)
      private val clearImage = ConsolePluginImages.getImage(IInternalConsoleConstants.IMG_ELCL_CLEAR)

      override def menuDetected(event: MenuDetectEvent): Unit = {
        val selection = event.widget.asInstanceOf[Tree].getSelection()

        val menu = new Menu(tree)
        tree.setMenu(menu)

        if (!selection.isEmpty) {
          val selectedTree = selection.head

          val copyToStringItem = new MenuItem(menu, SWT.NORMAL)
          copyToStringItem.setText("Copy toString() text")
          copyToStringItem.setImage(copyImage)

          val copyLabelItem = new MenuItem(menu, SWT.NORMAL)
          copyLabelItem.setText("Copy label")
          copyLabelItem.setImage(copyImage)

          new MenuItem(menu, SWT.SEPARATOR)

          copyToStringItem.addSelectionListener(new SimpleSelectionListener(copyToClipboard(selectedTree.getData().asInstanceOf[TreeNode].toStringText)))
          copyLabelItem.addSelectionListener(new SimpleSelectionListener(copyToClipboard(selectedTree.getText())))
        }

        val clearTreeItem = new MenuItem(menu, SWT.PUSH)
        clearTreeItem.setText("Clear")
        clearTreeItem.setImage(clearImage)

        if (selection.isEmpty)
          clearTreeItem.setEnabled(false)
        else
          clearTreeItem.addSelectionListener(new SimpleSelectionListener(clear()))
      }

      private def copyToClipboard(text: String): Unit = clipboard.setContents(Array(text), Array(TextTransfer.getInstance()))
    }
}

object ExpressionResultTreeView {
  import org.scalaide.debug.internal.expression.TypeNameMappings.javaNameToScalaName

  private val sessionDisconnectedText = "Cannot load node info. Session disconnected."
  private val preferenceStore = ScalaDebugPlugin.plugin.getPreferenceStore()

  sealed trait TreeNode {
    val label: String

    /**
     * String for underlying object
     */
    val toStringText: String

    val parent: Option[TreeNode]
    val childrenCount: Int
    def hasChildren: Boolean = childrenCount > 0
    val children: Seq[TreeNode]
  }

  /**
   * Case for other Value subtypes than ObjectReference
   */
  case class ValueNode(value: Value, fieldNameAndTypePrefix: String, parent: Option[TreeNode]) extends TreeNode {
    override val label = s"$fieldNameAndTypePrefix = $value"
    override val toStringText = value.toString()
    override val childrenCount = 0
    override val children = Seq.empty
  }

  case class ObjectReferenceNode(objectRef: ObjectReference, fieldName: Option[String] = None, parent: Option[TreeNode] = None,
    forcedLabel: Option[String] = None) extends TreeNode {

    private val referenceType = objectRef.referenceType()

    private val fieldsToShow = referenceType.allFields().filter(fieldShouldBeShown)

    override val label = createLabelWithReferenceType(objectRef, referenceType, fieldName, forcedLabel)

    override val toStringText = objectRef.toString()

    override val childrenCount = fieldsToShow.length

    override lazy val children = fieldsToShow.map(createChildrenForField).sortBy(_.label)

    private def createChildrenForField(field: Field): TreeNode =
      try {
        objectRef.getValue(field) match {
          case arrayRef: ArrayReference => ArrayReferenceNode(arrayRef, Some(field.name()), Some(this))
          case objRef: ObjectReference => ObjectReferenceNode(objRef, Some(field.name()), Some(this))
          case value =>
            val fieldTypeName = javaNameToScalaName(field.typeName())
            val fieldNameAndTypePrefix = s"${field.name()}: $fieldTypeName"
            Option(value).map { // e.g. for ClassObjectReference there often was null
              ValueNode(_, fieldNameAndTypePrefix, Some(this))
            }.getOrElse {
              PlainTextNode(s"$fieldNameAndTypePrefix = null", Some(this))
            }
        }
      } catch {
        // for instance when someone stopped debug mode
        case _: VMDisconnectedException =>
          val fieldTypeName = javaNameToScalaName(field.typeName())
          PlainTextNode(s"${field.name()}: $fieldTypeName - $sessionDisconnectedText", Some(this))
      }

    private def fieldShouldBeShown(field: Field) = !field.isStatic() && !field.isSynthetic()
  }

  case class ArrayReferenceNode(arrayRef: ArrayReference, fieldName: Option[String] = None, parent: Option[TreeNode] = None,
    forcedLabel: Option[String] = None) extends TreeNode {

    private val referenceType = arrayRef.referenceType()
    private val arrayLength = arrayRef.length()
    private val valuesGroupSize = preferenceStore.getInt(DebuggerPreferencePage.EXP_EVAL_ARRAY_VALUES_GROUP_SIZE)

    override val label = createLabelWithReferenceType(arrayRef, referenceType, fieldName, forcedLabel)

    override val toStringText = arrayRef.toString()

    override val childrenCount =
      if (arrayLength <= valuesGroupSize || valuesGroupSize == 1) arrayLength
      else math.ceil(arrayLength.toDouble / valuesGroupSize).toInt // numer of groups containing values

    override lazy val children =
      if (hasGroups) createNestedChildrenStructureWithGroups()
      else createFlatChildrenStructure()

    private def createFlatChildrenStructure(): Seq[TreeNode] =
      createChildren(0, arrayLength - 1)

    private def createNestedChildrenStructureWithGroups(): Seq[TreeNode] =
      (0 to childrenCount).map { i =>
        val startChildIndex = i * valuesGroupSize
        val endChildIndex = math.min(startChildIndex + valuesGroupSize - 1, arrayLength - 1)
        createGroupForChildren(startChildIndex, endChildIndex)
      }

    private def createChildren(startChildIndex: Int, endChildIndex: Int): Seq[TreeNode] =
      (startChildIndex to endChildIndex).map(createChildForValue)

    private def createChildForValue(childIndex: Int): TreeNode =
      try {
        arrayRef.getValue(childIndex) match {
          case ref: ArrayReference => ArrayReferenceNode(ref, Some(s"[$childIndex]"), Some(this))
          case objectRef: ObjectReference => ObjectReferenceNode(objectRef, Some(s"[$childIndex]"), Some(this))
          case value =>
            Option(value).map {
              ValueNode(_, s"[$childIndex]", Some(this))
            }.getOrElse {
              PlainTextNode(s"[$childIndex] = null", Some(this))
            }
        }
      } catch {
        // for instance when someone stopped debug mode
        case _: VMDisconnectedException => PlainTextNode(s"[$childIndex] - $sessionDisconnectedText", Some(this))
      }

    private def createGroupForChildren(startChildIndex: Int, endChildIndex: Int) = {
      val childrenCountInGroup = endChildIndex - startChildIndex + 1
      new GroupNode(s"[$startChildIndex - $endChildIndex]", Some(this), childrenCountInGroup, createChildren(startChildIndex, endChildIndex))
    }

    private def hasGroups = childrenCount != arrayLength
  }

  case class PlainTextNode(text: String, parent: Option[TreeNode]) extends TreeNode {
    override val label = text
    override val toStringText = text
    override val childrenCount = 0
    override val children = Seq.empty
  }

  /**
   * Node type used to group certain de facto children of its parent
   */
  class GroupNode(text: String, val parent: Option[TreeNode], val childrenCount: Int, groupChildren: => Seq[TreeNode]) extends TreeNode {
    override val label = text
    override val toStringText = text
    override lazy val children = groupChildren
  }

  private def createLabelWithReferenceType(objectRef: ObjectReference, referenceType: ReferenceType, fieldName: Option[String], forcedLabel: Option[String]) =
    forcedLabel.getOrElse {
      val typeName = javaNameToScalaName(referenceType.name())
      val prefix = fieldName.map(n => s"$n: ${typeName} =").getOrElse(s"{${typeName}} =")
      s"${prefix} $objectRef"
    }
}
