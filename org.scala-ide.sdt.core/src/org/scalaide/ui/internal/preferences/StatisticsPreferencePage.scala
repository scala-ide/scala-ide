package org.scalaide.ui.internal.preferences

import org.eclipse.jface.layout.TableColumnLayout
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.jface.viewers.ColumnWeightData
import org.eclipse.jface.viewers.IStructuredContentProvider
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.ViewerSorter
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Table
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.statistics.Stat
import org.scalaide.util.eclipse.SWTUtils._

class StatisticsPreferencePage extends PreferencePage with IWorkbenchPreferencePage {

  private val data = ScalaPlugin().statistics.data.toArray

  def createContents(parent: Composite): Control = {
    val base = new Composite(parent, SWT.NONE)
    base.setLayout(new GridLayout(1, true))

    val tableComposite = new Composite(base, SWT.NONE)
    tableComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1))

    val table = new Table(tableComposite, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL)
    table.setHeaderVisible(true)
    table.setLinesVisible(true)

    val tcl = new TableColumnLayout
    tableComposite.setLayout(tcl)

    val viewer = new TableViewer(table)
    viewer.setContentProvider(ContentProvider)
    viewer.setSorter(ColumnSorter)

    val columnFeature = new TableViewerColumn(viewer, SWT.NONE)
    columnFeature.getColumn.setText("Feature")
    columnFeature.onLabelUpdate(_.asInstanceOf[Stat].feature.description)
    columnFeature.getColumn.addSelectionListener { e: SelectionEvent ⇒
      ColumnSorter.doSort(ColumnSorter.Column.Feature)
      viewer.refresh()
    }
    tcl.setColumnData(columnFeature.getColumn, new ColumnWeightData(3, true))

    val columnGroup = new TableViewerColumn(viewer, SWT.NONE)
    columnGroup.getColumn.setText("Group")
    columnGroup.onLabelUpdate(_.asInstanceOf[Stat].feature.group.description)
    columnGroup.getColumn.addSelectionListener { e: SelectionEvent ⇒
      ColumnSorter.doSort(ColumnSorter.Column.Group)
      viewer.refresh()
    }
    tcl.setColumnData(columnGroup.getColumn, new ColumnWeightData(2, true))

    val columnUsed = new TableViewerColumn(viewer, SWT.NONE)
    columnUsed.getColumn.setText("Used")
    columnUsed.onLabelUpdate(_.asInstanceOf[Stat].nrOfUses match {
      case 0 ⇒ "Never"
      case 1 ⇒ "Once"
      case 2 ⇒ "Twice"
      case n ⇒ s"$n times"
    })
    columnUsed.getColumn.addSelectionListener { e: SelectionEvent ⇒
      ColumnSorter.doSort(ColumnSorter.Column.NrOfUses)
      viewer.refresh()
    }
    tcl.setColumnData(columnUsed.getColumn, new ColumnWeightData(1, true))

    val columnLastUsed = new TableViewerColumn(viewer, SWT.NONE)
    columnLastUsed.getColumn.setText("Last used")
    columnLastUsed.onLabelUpdate(d ⇒ timeAgo(d.asInstanceOf[Stat].lastUsed))
    columnLastUsed.getColumn.addSelectionListener { e: SelectionEvent ⇒
      ColumnSorter.doSort(ColumnSorter.Column.LastUsed)
      viewer.refresh()
    }
    tcl.setColumnData(columnLastUsed.getColumn, new ColumnWeightData(1, true))

    viewer.setInput(data)

    base
  }

  private def timeAgo(time: Long): String = {
    import scala.concurrent.duration._

    (System.nanoTime - time).nanos match {
      case d if d < 0.nanos   ⇒ "Never"
      case d if d < 2.minutes ⇒ "Moments ago"
      case d if d < 1.hour    ⇒ s"${d.toMinutes} minutes ago"
      case d if d < 1.day     ⇒ s"${d.toHours} hours ago"
      case d                  ⇒ s"${d.toDays} days ago"
    }
  }

  def init(workbench: IWorkbench): Unit = ()

  private object ColumnSorter extends ViewerSorter {

    object Column extends Enumeration {
      type Column = Value
      val Feature, Group, NrOfUses, LastUsed = Value
    }
    object Direction extends Enumeration {
      type Direction = Value
      val Ascending, Descending = Value
    }
    import Column._, Direction._

    var sortCol: Column = Feature
    var sortDir: Direction = Ascending

    override def compare(viewer: Viewer, o1: AnyRef, o2: AnyRef): Int = {
      def str(o: AnyRef) = o match {
        case Stat(feature, nrOfUses, lastUsed) ⇒ sortCol match {
          case Feature  ⇒ feature.description
          case Group    ⇒ feature.group.description
          case NrOfUses ⇒ nrOfUses.toString
          case LastUsed ⇒ lastUsed.toString
        }
      }
      sortDir match {
        case Ascending ⇒ str(o1) compareTo str(o2)
        case Descending ⇒ str(o2) compareTo str(o1)
      }
    }

    def doSort(col: Column) = {
      if (col == sortCol)
        sortDir = if (sortDir == Ascending) Descending else Ascending
      else {
        sortCol = col
        sortDir = Ascending
      }
    }

  }

  private object ContentProvider extends IStructuredContentProvider {
    def dispose(): Unit = ()

    def getElements(input: Any): Array[AnyRef] = {
      input.asInstanceOf[Array[AnyRef]]
    }

    def inputChanged(viewer: Viewer, oldInput: Any, newInput: Any): Unit = ()
  }

}
