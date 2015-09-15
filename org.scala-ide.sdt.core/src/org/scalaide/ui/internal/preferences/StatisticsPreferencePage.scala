package org.scalaide.ui.internal.preferences

import java.text.DateFormat
import java.util.Calendar

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
import org.scalaide.core.internal.statistics.FeatureData
import org.scalaide.core.internal.statistics.Features.CharactersSaved
import org.scalaide.core.internal.statistics.Features.NotSpecified
import org.scalaide.util.eclipse.SWTUtils._

class StatisticsPreferencePage extends PreferencePage with IWorkbenchPreferencePage {

  /*
   * Some features should not be displayed in the list of used features. These
   * are part of `filteredData`, the displayed ones are `displayedData`.
   */
  private val (displayedData, filteredData) = {
    val d = ScalaPlugin().statistics.data.toArray
    d.partition(fd ⇒ !Set(CharactersSaved, NotSpecified)(fd.feature))
  }

  override def createContents(parent: Composite): Control = {
    val base = new Composite(parent, SWT.NONE)
    base.setLayout(new GridLayout(1, true))

    val charsSaved = filteredData.find(_.feature == CharactersSaved).map(_.nrOfUses).getOrElse(0)
    mkLabel(base, s"Statistics tracking started at $startOfStats.")
    mkLabel(base, s"Code completion has saved you from typing approx. $charsSaved characters")

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
    columnFeature.onLabelUpdate(_.asInstanceOf[FeatureData].feature.description)
    columnFeature.getColumn.addSelectionListener { e: SelectionEvent ⇒
      ColumnSorter.doSort(ColumnSorter.Column.Feature)
      viewer.refresh()
    }
    tcl.setColumnData(columnFeature.getColumn, new ColumnWeightData(3, true))

    val columnGroup = new TableViewerColumn(viewer, SWT.NONE)
    columnGroup.getColumn.setText("Group")
    columnGroup.onLabelUpdate(_.asInstanceOf[FeatureData].feature.group.description)
    columnGroup.getColumn.addSelectionListener { e: SelectionEvent ⇒
      ColumnSorter.doSort(ColumnSorter.Column.Group)
      viewer.refresh()
    }
    tcl.setColumnData(columnGroup.getColumn, new ColumnWeightData(2, true))

    val columnUsed = new TableViewerColumn(viewer, SWT.NONE)
    columnUsed.getColumn.setText("Used")
    columnUsed.onLabelUpdate(_.asInstanceOf[FeatureData].nrOfUses match {
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
    columnLastUsed.onLabelUpdate(d ⇒ timeAgo(d.asInstanceOf[FeatureData].lastUsed))
    columnLastUsed.getColumn.addSelectionListener { e: SelectionEvent ⇒
      ColumnSorter.doSort(ColumnSorter.Column.LastUsed)
      viewer.refresh()
    }
    tcl.setColumnData(columnLastUsed.getColumn, new ColumnWeightData(1, true))

    viewer.setInput(displayedData)

    base
  }

  private def startOfStats: String = {
    val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    val c = Calendar.getInstance
    c.setTimeInMillis(ScalaPlugin().statistics.startOfStats)
    df.format(c.getTime)
  }

  private def timeAgo(time: Long): String = {
    import scala.concurrent.duration._

    (System.currentTimeMillis - time).millis match {
      case d if d < 0.nanos   ⇒ "Never"
      case d if d < 2.minutes ⇒ "Moments ago"
      case d if d < 1.hour    ⇒ s"${d.toMinutes} minutes ago"
      case d if d < 1.day     ⇒ s"${d.toHours} hours ago"
      case d                  ⇒ s"${d.toDays} days ago"
    }
  }

  override def init(workbench: IWorkbench): Unit = ()

  private object ColumnSorter extends ViewerSorter {

    object Column extends Enumeration {
      type Column = Value
      val Feature, Group, NrOfUses, LastUsed = Value
    }
    object Direction extends Enumeration {
      type Direction = Value
      val Ascending, Descending = Value
    }
    import Column._
    import Direction._

    var sortCol: Column = NrOfUses
    var sortDir: Direction = Descending

    override def compare(viewer: Viewer, o1: AnyRef, o2: AnyRef): Int = {
      def cmp = (o1, o2) match {
        case (o1: FeatureData, o2: FeatureData) ⇒ sortCol match {
          case Feature  ⇒ o1.feature.description compareTo o2.feature.description
          case Group    ⇒ o1.feature.group.description compareTo o2.feature.group.description
          case NrOfUses ⇒ (o1.nrOfUses-o2.nrOfUses).toInt
          case LastUsed ⇒ (o1.lastUsed-o2.lastUsed).toInt
        }
      }
      if (sortDir == Ascending) cmp else -cmp
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
    override def dispose(): Unit = ()

    override def getElements(input: Any): Array[AnyRef] = {
      input.asInstanceOf[Array[AnyRef]]
    }

    override def inputChanged(viewer: Viewer, oldInput: Any, newInput: Any): Unit = ()
  }

}
