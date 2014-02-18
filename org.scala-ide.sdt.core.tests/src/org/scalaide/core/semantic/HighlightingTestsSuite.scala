/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.core.semantic

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Junit test suite for the semantic annotations.
 */
@RunWith(classOf[Suite])
@Suite.SuiteClasses(
  Array(
    classOf[ImplicitsHighlightingTest],
    classOf[CustomMethodHighlightingTest],
    classOf[CustomClassHighlightingTest],
    classOf[CustomAnnotationHighlightingTest]))
class HighlightingTestsSuite
