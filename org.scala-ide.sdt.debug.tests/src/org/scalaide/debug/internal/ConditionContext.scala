/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal

/**
 * @param condition condition to evaluate at breakpoint
 * @param shouldSuspend expected result of condition evaluation
 */
case class ConditionContext(condition: String, shouldSuspend: Boolean)
