package org.scalaide

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.scalaide.core.jcompiler.AbstractMethodVerifierTest
import org.scalaide.core.sbtbuilder.ScalaCompilerClasspathTest
import org.scalaide.core.wizards.ImportSupportTest
import org.scalaide.core.hyperlink.HyperlinkDetectorTests
import org.scalaide.core.sbtbuilder.ScalaJavaDepTest
import org.scalaide.core.lexical.LexicalTestsSuite
import org.scalaide.core.wizards.QualifiedNameSupportTest
import org.scalaide.core.classpath.ClasspathTests
import org.scalaide.core.classpath.ClasspathContainersTests
import org.scalaide.core.structurebuilder.ScalaJavaMapperTest
import org.scalaide.core.completion.CompletionTests
import org.scalaide.core.launching.MainClassVerifierTest
import org.scalaide.core.occurrences.OccurrencesFinderTest
import org.scalaide.core.sbtbuilder.OutputFoldersTest
import org.scalaide.core.sbtbuilder.ProjectDependenciesTest
import org.scalaide.core.pc.PresentationCompilerTest
import org.scalaide.core.structurebuilder.StructureBuilderTest
import org.scalaide.core.ui.UITestSuite
import org.scalaide.core.semantic.HighlightingTestsSuite
import org.scalaide.core.buildmanager.ProjectsCleanJobTest
import org.scalaide.core.semantichighlighting.classifier.SymbolClassifierTestSuite
import org.scalaide.core.pc.PresentationCompilerRefreshTest
import org.scalaide.core.sbtbuilder.SbtBuilderTest
import org.scalaide.core.semantichighlighting.SemanticHighlightingPositionsTest
import org.scalaide.core.hyperlink.ScalaWordFinderTest
import org.scalaide.core.sbtbuilder.MultipleErrorsTest
import org.scalaide.core.findreferences.FindReferencesTests
import org.scalaide.core.lexical.ScalaDocumentPartitionerTest
import org.scalaide.core.compiler.settings.CompilerSettingsTest
import org.scalaide.core.launching.RunAsTest
import org.scalaide.core.sbtbuilder.NestedProjectsTest
import org.scalaide.core.sbtbuilder.TodoBuilderTest
import org.scalaide.core.sbtbuilder.DeprecationWarningsTests
import org.scalaide.core.project.ScalaInstallationTest
import org.scalaide.core.sbtbuilder.MultiScalaVersionTest
import org.scalaide.core.ui.completion.CompletionTestSuite
import org.scalaide.core.project.DirectoryScalaInstallationTest
import org.scalaide.core.classpath.DesiredScalaInstallationTests
import org.scalaide.ui.wizards.WizardTests

@RunWith(classOf[Suite])
@Suite.SuiteClasses(
  Array(
    classOf[ProjectsCleanJobTest],
    classOf[ClasspathTests],
    classOf[ClasspathContainersTests],
    classOf[CompilerSettingsTest],
    classOf[CompletionTests],
    classOf[DesiredScalaInstallationTests],
    classOf[RunAsTest],
    // classOf[ScalaJavaCompletionTests], FIXME: Uncomment as soon as this regression is fixed.
    classOf[FindReferencesTests],
    classOf[HyperlinkDetectorTests],
    // classOf[scala.tools.eclipse.interpreter.EclipseReplTest], // see comments there
    classOf[AbstractMethodVerifierTest],
    classOf[MainClassVerifierTest],
    classOf[ScalaDocumentPartitionerTest],
    classOf[LexicalTestsSuite],
    classOf[PresentationCompilerRefreshTest],
    classOf[PresentationCompilerTest],
    classOf[MultipleErrorsTest],
    classOf[NestedProjectsTest],
    classOf[OccurrencesFinderTest],
    classOf[OutputFoldersTest],
    classOf[ProjectDependenciesTest],
    classOf[SbtBuilderTest],
    classOf[DeprecationWarningsTests],
    classOf[ScalaCompilerClasspathTest],
    classOf[ScalaJavaDepTest],
    classOf[TodoBuilderTest],
    classOf[HighlightingTestsSuite],
    classOf[SemanticHighlightingPositionsTest],
    classOf[SymbolClassifierTestSuite],
    classOf[StructureBuilderTest],
    classOf[ScalaJavaMapperTest],
    classOf[UITestSuite],
    classOf[ImportSupportTest],
    classOf[QualifiedNameSupportTest],
    classOf[ScalaWordFinderTest],
    classOf[ScalaInstallationTest],
    classOf[MultiScalaVersionTest],
    classOf[DirectoryScalaInstallationTest],
    classOf[CompletionTestSuite],
    classOf[WizardTests]
))
class TestsSuite
