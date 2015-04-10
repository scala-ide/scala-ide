/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal

/**
 * @param condition condition to evaluate at breakpoint
 * @param shouldSuspend expected result of condition evaluation
 */
case class ConditionContext(condition: String, shouldSuspend: Boolean)
