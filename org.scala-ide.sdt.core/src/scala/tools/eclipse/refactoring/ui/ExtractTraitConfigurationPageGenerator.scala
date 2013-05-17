package scala.tools.eclipse.refactoring.ui

import org.eclipse.ltk.ui.refactoring.UserInputWizardPage
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.jface.viewers.IStructuredContentProvider
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.jface.viewers.ColumnLabelProvider
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.IStructuredSelection
import scala.tools.eclipse.refactoring.ScalaIdeRefactoring

/**
 * Generates the wizard page for a ExtractTrait refactoring.
 */
trait ExtractTraitConfigurationPageGenerator {

  // This gives us access to refactoring.global
  this: ScalaIdeRefactoring =>

  import refactoring.global._

  def mkConfigPage(
      extractableMembers: List[ValOrDefDef],
      selectedMembersObs: List[ValOrDefDef] => Unit,
      extractedNameObs: String => Unit) = {
    new ExtractTraitConfigurationPage(extractableMembers, selectedMembersObs, extractedNameObs)
  }

  /**
   * The wizard page for ExtractTrait
   * @param extractableMembers The members that can be extracted.
   * @param selectedMembersObs Observer for the members currently selected for extraction.
   * @param extractedNameObs Observer for the currently chosen name of the extracted trait.
   */
  class ExtractTraitConfigurationPage(
    extractableMembers: List[ValOrDefDef],
    selectedMembersObs: List[ValOrDefDef] => Unit,
    extractedNameObs: String => Unit) extends UserInputWizardPage("Extract trait") {

    def createControl(parent: Composite) {
      initializeDialogUnits(parent)

      val composite = new Composite(parent, SWT.NONE)
      composite.setLayout(new GridLayout(1, false))

      // Gets the name of the extracted trait.
      val traitNamePart = new LabeledTextField(composite, extractedNameObs, "Trait name: ", "Extracted")
      val traitNamePartLayoutData = new GridData(SWT.FILL, SWT.CENTER, true, false)
      traitNamePart.setLayoutData(traitNamePartLayoutData)

      val selectMembersLbl = new Label(composite, SWT.NONE)
      selectMembersLbl.setText("Select members for extraction: ")
      selectMembersLbl.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false))

      // Table presenting the extractable members for selection.
      val membersTable = new MembersTable(composite, extractableMembers, selectedMembersObs)
      membersTable.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true))

      setControl(composite)
    }

    // Presents the extractable members along with checkboxes for selection.
    private class MembersTable(
      parent: Composite,
      members: List[ValOrDefDef],
      selectedMembersObs: List[ValOrDefDef] => Unit) extends Composite(parent, SWT.NONE) {

      object ListContentProvider extends IStructuredContentProvider {
        override def getElements(members: AnyRef): Array[AnyRef] = {
          val elems = members.asInstanceOf[List[ValOrDefDef]]
          Array(elems: _*)
        }
        override def inputChanged(viewer: Viewer, oldInput: Any, newInput: Any) {}
        override def dispose {}
      }

      def mkTableViewerColumn(title: String) = {
        val viewerColumn = new TableViewerColumn(viewer, SWT.NONE)
        val column = viewerColumn.getColumn
        column setText title
        column setWidth 100
        column setResizable true
        column setMoveable true
        viewerColumn
      }

      val viewer = new TableViewer(this, SWT.CHECK | SWT.BORDER)

      val gridLayout = new GridLayout
      setLayout(gridLayout)

      val nameColumn = mkTableViewerColumn("Member")
      nameColumn.setLabelProvider(new ColumnLabelProvider() {
        override def getText(element: Any): String = element match {
          case v @ ValDef(_, name, tpt, _) => {
            val varType = if(v.symbol.isMutable) {"var "} else {"val "}
            varType + name.toString.trim + ": " + tpt.symbol.nameString
          }
          case DefDef(_, name, tparams, vparamss, tpt, _) =>
            val tparamsStr = tparams match {
              case Nil => ""
              case _ => "[" + tparams.map(_.symbol.nameString).mkString(", ") + "]"
            }
            val vparamssStr = vparamss.map( vparams =>
              "(" + vparams.map(_.tpt.symbol.nameString).mkString(", ") + ")"
            ).mkString("")
            "def " + name + tparamsStr + vparamssStr + ": " + tpt.symbol.nameString
          case _ => ""
        }
      })

      val table = viewer.getTable
      table setLinesVisible true

      viewer setContentProvider ListContentProvider
      viewer.getControl.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
      viewer.setInput(members)

      val rows = List(table.getItems: _*)

      viewer.addSelectionChangedListener(new ISelectionChangedListener() {
        override def selectionChanged(event: SelectionChangedEvent) {
          val selectedMembersIndices = rows.map(_.getChecked).zipWithIndex.collect { case (true, index) => index }
          val selectedMembers = selectedMembersIndices.map(members)
          selectedMembersObs(selectedMembers)
        }
      })

    }

  }
}

