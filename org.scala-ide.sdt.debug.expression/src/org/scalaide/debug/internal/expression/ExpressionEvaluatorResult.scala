/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import org.scalaide.debug.internal.model.ScalaValue

/**
 * Result types returned by ExpressionManager
 */
sealed trait ExpressionEvaluatorResult

case class SuccessWithValue(scalaValue: ScalaValue, outputText: String) extends ExpressionEvaluatorResult

/**
 * There's no underlying object e.g. for Unit so that we cannot create ScalaValue for it
 */
case class SuccessWithoutValue(outputText: String) extends ExpressionEvaluatorResult

case class EvaluationFailure(errorMessage: String) extends ExpressionEvaluatorResult
