package scala.tools.eclipse;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import scala.tools.eclipse.buildmanager.ProjectsCleanJobTest;
import scala.tools.eclipse.classpath.ClasspathTests;
import scala.tools.eclipse.compiler.settings.CompilerSettingsTest;
import scala.tools.eclipse.completion.CompletionTests;
import scala.tools.eclipse.findreferences.FindReferencesTests;
import scala.tools.eclipse.hyperlink.HyperlinkDetectorTests;
import scala.tools.eclipse.hyperlink.ScalaWordFinderTest;
import scala.tools.eclipse.jcompiler.AbstractMethodVerifierTest;
import scala.tools.eclipse.launching.RunAsTest;
import scala.tools.eclipse.launching.MainClassVerifierTest;
import scala.tools.eclipse.lexical.LexicalTestsSuite;
import scala.tools.eclipse.lexical.ScalaDocumentPartitionerTest;
import scala.tools.eclipse.occurrences.OccurrencesFinderTest;
import scala.tools.eclipse.pc.PresentationCompilerRefreshTest;
import scala.tools.eclipse.pc.PresentationCompilerTest;
import scala.tools.eclipse.sbtbuilder.DeprecationWarningsTests;
import scala.tools.eclipse.sbtbuilder.MultipleErrorsTest;
import scala.tools.eclipse.sbtbuilder.NestedProjectsTest;
import scala.tools.eclipse.sbtbuilder.OutputFoldersTest;
import scala.tools.eclipse.sbtbuilder.ProjectDependenciesTest;
import scala.tools.eclipse.sbtbuilder.SbtBuilderTest;
import scala.tools.eclipse.sbtbuilder.ScalaCompilerClasspathTest;
import scala.tools.eclipse.sbtbuilder.ScalaJavaDepTest;
import scala.tools.eclipse.sbtbuilder.TodoBuilderTest;
import scala.tools.eclipse.semantic.ImplicitsHighlightingTest;
import scala.tools.eclipse.semantichighlighting.SemanticHighlightingPositionsTest;
import scala.tools.eclipse.semantichighlighting.classifier.SymbolClassifierTestSuite;
import scala.tools.eclipse.structurebuilder.ScalaJavaMapperTest;
import scala.tools.eclipse.structurebuilder.StructureBuilderTest;
import scala.tools.eclipse.ui.UITestSuite;
import scala.tools.eclipse.util.CollectionUtilTest;
import scala.tools.eclipse.wizards.ImportSupportTest;
import scala.tools.eclipse.wizards.QualifiedNameSupportTest;


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
  //scala.tools.eclipse.interpreter.EclipseReplTest.class, // see comments there
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
