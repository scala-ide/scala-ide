/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import org.scalaide.debug.internal.expression.ExpressionEvaluator
import org.scalaide.debug.internal.expression.NullProgressMonitor

trait HasEvaluator {
  protected object Evaluator extends ExpressionEvaluator(getClass.getClassLoader, NullProgressMonitor)
}