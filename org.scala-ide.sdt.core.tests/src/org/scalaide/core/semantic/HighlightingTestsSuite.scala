/*
 * Copyright (c) 2014 Contributor. All rights reserved.
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
