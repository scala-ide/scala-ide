package org.scalaide.core;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.scalaide.core.buildmanager.ProjectsCleanJobTest;
import org.scalaide.core.classpath.ClasspathTests;
import org.scalaide.core.compiler.settings.CompilerSettingsTest;
import org.scalaide.core.completion.CompletionTests;
import org.scalaide.core.findreferences.FindReferencesTests;
import org.scalaide.core.hyperlink.HyperlinkDetectorTests;
import org.scalaide.core.hyperlink.ScalaWordFinderTest;
import org.scalaide.core.jcompiler.AbstractMethodVerifierTest;
import org.scalaide.core.launching.MainClassVerifierTest;
import org.scalaide.core.launching.RunAsTest;
import org.scalaide.core.lexical.LexicalTestsSuite;
import org.scalaide.core.lexical.ScalaDocumentPartitionerTest;
import org.scalaide.core.occurrences.OccurrencesFinderTest;
import org.scalaide.core.pc.PresentationCompilerRefreshTest;
import org.scalaide.core.pc.PresentationCompilerTest;
import org.scalaide.core.sbtbuilder.DeprecationWarningsTests;
import org.scalaide.core.sbtbuilder.MultipleErrorsTest;
import org.scalaide.core.sbtbuilder.NestedProjectsTest;
import org.scalaide.core.sbtbuilder.OutputFoldersTest;
import org.scalaide.core.sbtbuilder.ProjectDependenciesTest;
import org.scalaide.core.sbtbuilder.SbtBuilderTest;
import org.scalaide.core.sbtbuilder.ScalaCompilerClasspathTest;
import org.scalaide.core.sbtbuilder.ScalaJavaDepTest;
import org.scalaide.core.sbtbuilder.TodoBuilderTest;
import org.scalaide.core.semantic.ImplicitsHighlightingTest;
import org.scalaide.core.semantichighlighting.SemanticHighlightingPositionsTest;
import org.scalaide.core.semantichighlighting.classifier.SymbolClassifierTestSuite;
import org.scalaide.core.structurebuilder.ScalaJavaMapperTest;
import org.scalaide.core.structurebuilder.StructureBuilderTest;
import org.scalaide.core.ui.UITestSuite;
import org.scalaide.core.util.CollectionUtilTest;
import org.scalaide.core.wizards.ImportSupportTest;
import org.scalaide.core.wizards.QualifiedNameSupportTest;


/**
 * To run this class DO NOT FORGET to set the config.ini in the  "configuration" tab.
 * @author ratiu
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
  ProjectsCleanJobTest.class,
  ClasspathTests.class,
  CompilerSettingsTest.class,
  CompletionTests.class,
  RunAsTest.class,
  // ScalaJavaCompletionTests.class, FIXME: Uncomment as soon as this regression is fixed.
  FindReferencesTests.class,
  HyperlinkDetectorTests.class,
  //org.scalaide.core.interpreter.EclipseReplTest.class, // see comments there
  AbstractMethodVerifierTest.class,
  MainClassVerifierTest.class,
  ScalaDocumentPartitionerTest.class,
  LexicalTestsSuite.class,
  PresentationCompilerRefreshTest.class,
  PresentationCompilerTest.class,
  MultipleErrorsTest.class,
  NestedProjectsTest.class,
  OccurrencesFinderTest.class,
  OutputFoldersTest.class,
  ProjectDependenciesTest.class,
  SbtBuilderTest.class,
  DeprecationWarningsTests.class,
  ScalaCompilerClasspathTest.class,
  ScalaJavaDepTest.class, 
  TodoBuilderTest.class,
  ImplicitsHighlightingTest.class,
  SemanticHighlightingPositionsTest.class,
  SymbolClassifierTestSuite.class,
  StructureBuilderTest.class,
  ScalaJavaMapperTest.class,
  UITestSuite.class,
  CollectionUtilTest.class,
  ImportSupportTest.class,
  QualifiedNameSupportTest.class,
  ScalaWordFinderTest.class
})
class TestsSuite { }
