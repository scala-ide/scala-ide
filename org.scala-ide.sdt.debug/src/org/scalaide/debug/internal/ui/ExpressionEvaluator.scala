/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.ui

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout.RowData
import org.eclipse.swt.layout.RowLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.forms.widgets.FormToolkit
import org.eclipse.ui.part.ViewPart
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.layout.FillLayout;
import org.scalaide.debug.internal.expression.ExpressionManager

/**
 * UI component for debug expression evaluation.
 */
class ExpressionEvaluator extends ViewPart {
  private final val formToolkit: FormToolkit = new FormToolkit(Display.getDefault)
  private var inputText: Text = null
  private var resultText: Text = null

  /**
   * Create contents of the view part.
   */

  def createPartControl(parent: Composite) {
    parent.setLayout(new FillLayout(SWT.HORIZONTAL))

    val scrolledComposite: ScrolledComposite = new ScrolledComposite(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
    formToolkit.adapt(scrolledComposite)
    formToolkit.paintBordersFor(scrolledComposite)
    scrolledComposite.setExpandHorizontal(true)
    scrolledComposite.setExpandVertical(true)

    val composite_1: Composite = new Composite(scrolledComposite, SWT.NONE)
    formToolkit.adapt(composite_1)
    formToolkit.paintBordersFor(composite_1)
    composite_1.setLayout(new RowLayout(SWT.VERTICAL))

    val lblTypeYourExpression: Label = new Label(composite_1, SWT.NONE)
    formToolkit.adapt(lblTypeYourExpression, true, true)
    lblTypeYourExpression.setText("Type your expression here:")

    val composite: Composite = new Composite(composite_1, SWT.BORDER)
    composite.setLayoutData(new RowData(560, 130))
    formToolkit.adapt(composite)
    formToolkit.paintBordersFor(composite)
    composite.setLayout(null)
    inputText = new Text(composite, SWT.BORDER | SWT.MULTI)
    inputText.setBounds(10, 10, 483, 110)
    inputText.setToolTipText("")
    formToolkit.adapt(inputText, true, true)

    val btnEvaluate: Button = new Button(composite, SWT.NONE)
    btnEvaluate.setBounds(499, 8, 56, 25)

    val resultCallback =
      (result: String) => {
        resultText.setForeground(Display.getDefault.getSystemColor(SWT.COLOR_BLACK))
        resultText.setText(result)
      }

    val errorCallback = (result: String) => {
      resultText.setForeground(Display.getDefault.getSystemColor(SWT.COLOR_RED))
      resultText.setText(result)
    }

    btnEvaluate.addSelectionListener(new SelectionAdapter {
      override def widgetSelected(e: SelectionEvent) {
        ExpressionManager.compute(inputText.getText, resultCallback, errorCallback)
      }
    })

    formToolkit.adapt(btnEvaluate, true, true)
    btnEvaluate.setText("Evaluate")

    val btnClean: Button = new Button(composite, SWT.NONE)
    btnClean.addSelectionListener(new SelectionAdapter {
      override def widgetSelected(e: SelectionEvent) {
        inputText.setText("")
        resultText.setText("")
      }
    })
    btnClean.setBounds(499, 39, 56, 25)
    formToolkit.adapt(btnClean, true, true)
    btnClean.setText("Clean")

    val resultComposite: Composite = new Composite(composite_1, SWT.BORDER)
    resultComposite.setLayoutData(new RowData(560, 156))
    formToolkit.adapt(resultComposite)
    formToolkit.paintBordersFor(resultComposite)

    val resultLabel: Label = new Label(resultComposite, SWT.NONE)
    resultLabel.setBounds(10, 10, 104, 15)
    formToolkit.adapt(resultLabel, true, true)
    resultLabel.setText("Result")
    resultText = new Text(resultComposite, SWT.BORDER | SWT.MULTI)
    resultText.setEditable(false)
    resultText.setBounds(10, 31, 540, 115)
    formToolkit.adapt(resultText, true, true)
    scrolledComposite.setContent(composite_1)
    scrolledComposite.setMinSize(composite_1.computeSize(SWT.DEFAULT, SWT.DEFAULT))
  }

  override def dispose {
  }

  def setFocus {
  }

}
