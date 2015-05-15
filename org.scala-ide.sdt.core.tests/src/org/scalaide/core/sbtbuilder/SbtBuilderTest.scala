package org.scalaide.core
package sbtbuilder

import org.junit.Test
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.core.resources.IResource
import org.junit.Assert
import org.eclipse.core.resources.IMarker
import testsetup._
import org.eclipse.core.resources.IFile
import org.junit.Ignore
import org.junit.Before
import org.mockito.Matchers.any
import org.eclipse.jdt.core.IProblemRequestor
import org.eclipse.jdt.core.WorkingCopyOwner
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import scala.util.matching.Regex
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.JavaCore
import scala.tools.nsc.Settings
import org.scalaide.core.IScalaProject
import org.scalaide.core.internal.project.ScalaClasspath
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.util.eclipse.EclipseUtils

object SbtBuilderTest extends TestProjectSetup("builder") with CustomAssertion
object depProject extends TestProjectSetup("builder-sub")
object closedProject extends TestProjectSetup("closed-project-test") {

  def closeProject(): Unit = {
    project.underlying.close(null)
  }
}

class SbtBuilderTest {

  import SbtBuilderTest._

  @Before
  def setupWorkspace(): Unit = {
    SDTTestUtils.enableAutoBuild(true)
  }

  @Test def testSimpleBuild(): Unit = {
    println("building " + project)
    project.clean(new NullProgressMonitor())
    depProject // initialize
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    val units = compilationUnits("test/ja/JClassA.java", "test/sc/ClassA.scala")
    val noErrors = units.forall { unit =>
      val problems = unit.getUnderlyingResource().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
      println("problems: %s: %s".format(unit, problems.toList))
      problems.isEmpty
    }

    Assert.assertTrue("Build errors found", noErrors)
  }

  /**
   * See #1002070
   */
  @Test def testBuildErrorRemove(): Unit = {
     import SDTTestUtils._
     SDTTestUtils.enableAutoBuild(false)

     val Seq(prj) = createProjects("A")
     try {

       val pack = createSourcePackage("test")(prj)
       val originalA = "package test;\n object A { def callMe() = ??? }\n"
       val originalB = "package test;\n class B { A.callMe() } \n"

       // C depends directly on A, through an exported dependency of B
       val unitA = pack.createCompilationUnit("A.scala", originalA, true, null)
       val unitB = pack.createCompilationUnit("B.scala", originalB, true, null)

       // no errors
       prj.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
       val errors = getErrorMessages(unitA, unitB)
       Assert.assertEquals("Build errors found", List(), errors)

       val changedErrors = SDTTestUtils.buildWith(unitA.getResource, "package test;\n object A { def callMe1() = ??? }\n", Seq(unitA, unitB))
       Assert.assertEquals("Build problems " + changedErrors, 1, changedErrors.size)

       prj.presentationCompiler(pc => pc.discardCompilationUnit(unitA.asInstanceOf[ScalaCompilationUnit]))
       val deletedErrors = SDTTestUtils.buildWith(unitB.getResource, originalB, Seq(unitB))
       Assert.assertEquals("Build problems " + deletedErrors, 1, deletedErrors.size)

       // the error goes away
       val newUnitA = pack.createCompilationUnit("A.scala", originalA, true, null)
       val errorMessages = SDTTestUtils.buildWith(newUnitA.getResource, originalA, Seq(newUnitA, unitB))
       Assert.assertEquals("No build problems: " + errorMessages, 0, errorMessages.size)
     } finally
       deleteProjects(prj)
   }

  @Test def testSimpleBuildWithResources(): Unit = {
    println("building " + depProject)
    depProject.project.clean(new NullProgressMonitor())
    depProject.project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    val targetResource = depProject.project.javaProject.getOutputLocation().append(new Path("resource.txt"))
    val file = EclipseUtils.workspaceRoot.findMember(targetResource)
    Assert.assertNotNull("Resource has been copied to the output directory", file ne null)
    Assert.assertTrue("Resource has been copied to the output directory and exists", file.exists())
  }

  @Test def dependent_projects_are_rebuilt_and_PC_notified(): Unit = {

    def rebuild(prj: IScalaProject): List[IMarker] = {
      println("building " + prj)
      prj.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)

      getProblemMarkers()
    }

    depProject.project // force initialization of this project
    depProject.project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)

    println("=== Dependency Test === ")
    project.clean(new NullProgressMonitor())

    rebuild(depProject.project)
    val problemsOrig = rebuild(project)
    Assert.assertTrue("Should succeed compilation " + problemsOrig, problemsOrig.isEmpty)

    val fooCU = depProject.compilationUnit("subpack/Foo.scala")
    println("IFile: " + fooCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile])
    SDTTestUtils.changeContentOfFile(fooCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changedFooScala)

    val fooClientCU = scalaCompilationUnit("test/dependency/FooClient.scala")

    println("=== Rebuilding workspace === ")
    SDTTestUtils.workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)

    val problems = getProblemMarkers()

    val errorMessages: List[String] = for (p <- problems) yield p.getAttribute(IMarker.MESSAGE).toString

    Assert.assertEquals("Build problems", 2, problems.size)
    Assert.assertEquals("Build Problem should be in FooClient.scala", problems(0).getResource(), fooClientCU.getResource())
    Assert.assertEquals("Number of error messages differ", expectedMessages.size, errorMessages.size)
    for (error <- errorMessages) {
      Assert.assertTrue("Build error messages differ. Expected: %s, Actual: %s".format(expectedMessages, errorMessages), expectedMessages.exists(similarErrorMessage(error)))
    }

    // error markers have non-zero length
    for (p <- problems)
      Assert.assertTrue("Error marker length is zero", (p.getAttribute(IMarker.CHAR_END, 0) - p.getAttribute(IMarker.CHAR_START, 0) > 0))

    val sf = fooClientCU.lastSourceMap().sourceFile
    fooClientCU.scalaProject.presentationCompiler { comp =>
      comp.askReload(fooClientCU, sf).get // synchronize with the good compiler
    }

    val pcProblems = fooClientCU.asInstanceOf[ScalaSourceFile].getProblems()
    Assert.assertEquals("Presentation compiler errors.", 2, pcProblems.size)
  }

  /** Where we look for errors. */
  val unitsToWatch = compilationUnits("test/ja/JClassA.java", "test/sc/ClassA.scala", "test/dependency/FooClient.scala").toList

  private def getProblemMarkers(): List[IMarker] = {
    unitsToWatch.flatMap(SDTTestUtils.findProblemMarkers)
  }

  @Test def dependentProject_should_restart_PC_after_build(): Unit = {
    val fooCU = depProject.compilationUnit("subpack/Foo.scala")
    val changedErrors = SDTTestUtils.buildWith(fooCU.getResource, changedFooScala, unitsToWatch)

    Assert.assertEquals("Build problems " + changedErrors, 2, changedErrors.size)

    val errorMessages = SDTTestUtils.buildWith(fooCU.getResource, originalFooScala, unitsToWatch)
    Assert.assertEquals("No build problems: " + errorMessages, 0, errorMessages.size)

    val fooClientCU = scalaCompilationUnit("test/dependency/FooClient.scala")

    reload(fooClientCU)

    assertNoErrors(fooClientCU)
  }

  @Test def scalaLibrary_in_dependent_project_shouldBe_on_BootClasspath(): Unit = {
    import SDTTestUtils._

    val Seq(prjClient, prjLib) = createProjects("client", "library")
    try {
      val packLib = createSourcePackage("scala")(prjLib)
      val baseRawClasspath = prjClient.javaProject.getRawClasspath()

      /* The classpath, with the eclipse scala container removed. */
      def cleanRawClasspath = baseRawClasspath.filterNot(_.getPath().toPortableString() == "org.scala-ide.sdt.launching.SCALA_CONTAINER")

      prjClient.javaProject.setRawClasspath(cleanRawClasspath, null)
      addToClasspath(prjClient, JavaCore.newProjectEntry(prjLib.underlying.getFullPath, true))

      packLib.createCompilationUnit("Predef.scala", "package scala; class Predef", true, null)
      prjLib.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

      Assert.assertTrue("Found Scala library", prjClient.scalaClasspath.scalaLibrary.isDefined)

      val expectedLib = EclipseUtils.workspaceRoot.findMember("/library/bin").getLocation
      Assert.assertEquals("Unexpected Scala lib", expectedLib, prjClient.scalaClasspath.scalaLibrary.get)
    } finally {
      deleteProjects(prjClient, prjLib)
    }
  }

  /**
   * Test that the JDK and Scala library end up in the bootclasspaths arguments for
   *  scalac.
   *
   *  - We test that the `-javabootclasspath` and `-bootclasspath` are correctly set
   *  by `ScalaProject.scalacArguments`.
   *  - we test that a build *fails* with a non-existent Scala library (Sbt adds its
   *  own processing of arguments that might add a working default)
   *  - we do *not* test that a different JDK is honored by Sbt (couldn't find a way to
   *  fake a JDK install), but we do test that the JDK is put in `-javabootclasspath`.
   */
  @Test def bootLibrariesAreOnClasspath(): Unit = {
    import SDTTestUtils._

    val Seq(prjClient, prjLib) = createProjects("client", "library")
    try {
      val packLib = createSourcePackage("scala")(prjLib)
      val baseRawClasspath = prjClient.javaProject.getRawClasspath()

      // The classpath, with the eclipse scala container removed
      def cleanRawClasspath = baseRawClasspath.filterNot(_.getPath().toPortableString() == "org.scala-ide.sdt.launching.SCALA_CONTAINER")

      // add a fake Scala library
      prjClient.javaProject.setRawClasspath(cleanRawClasspath, null)
      addToClasspath(prjClient, JavaCore.newProjectEntry(prjLib.underlying.getFullPath, true))

      // add a source file
      val packA = createSourcePackage("test")(prjClient)
      packA.createCompilationUnit("A.scala", """class A { println("hello") }""", true, null)

      // build the fake Scala library
      packLib.createCompilationUnit("Predef.scala", "package scala; class Predef", true, null)
      prjLib.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

      Assert.assertTrue("Found Scala library", prjClient.scalaClasspath.scalaLibrary.isDefined)

      val ScalaClasspath(jdkPaths, scalaLib, _, _) = prjClient.scalaClasspath
      val args = prjClient.scalacArguments

      // parsing back these arguments should give back the same libraries
      val settings = new Settings()
      settings.processArguments(args.toList, true)

      def unify(s: String) = s.replace('\\', '/')

      Assert.assertEquals("Java bootclasspath is correct",
          unify(settings.javabootclasspath.value),
          unify(jdkPaths.mkString(java.io.File.pathSeparator)))
      Assert.assertEquals("Scala bootclasspath is correct",
          unify(settings.bootclasspath.value),
          unify(scalaLib.get.toString))

      // now test that the build fails with the fake Scala library
      prjClient.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
      prjClient.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
      val markers = prjClient.underlying.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
      Assert.assertTrue("Errors expected, but none found", markers.nonEmpty)
    } finally {
      deleteProjects(prjClient, prjLib)
    }
  }

   @Test def bootLibrariesCanBeUnorderedOnClasspath(): Unit = {
    import SDTTestUtils._

    val Seq(prjClient) = createProjects("client")
    try {
      val baseRawClasspath = prjClient.javaProject.getRawClasspath()

      // The classpath, with the eclipse scala container removed
      val (scalaContainerPath, cleanRawClasspath) = baseRawClasspath.partition(_.getPath().toPortableString() == "org.scala-ide.sdt.launching.SCALA_CONTAINER")

      // add the scala classpath at the end
      prjClient.javaProject.setRawClasspath(cleanRawClasspath ++ scalaContainerPath, null)

      // add a source file
      val packA = createSourcePackage("test")(prjClient)
      packA.createCompilationUnit("A.scala", """class A { println("hello") }""", true, null)
      Assert.assertTrue("Found Scala library", prjClient.scalaClasspath.scalaLibrary.isDefined)

      // now test that the build succeeds with the out-of-order Scala library
      prjClient.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
      prjClient.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
      val markers = prjClient.underlying.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
      Assert.assertTrue("No errors expected", markers.isEmpty)
    } finally {
      deleteProjects(prjClient)
    }
  }

  @Test def checkClosedProject(): Unit = {
    closedProject.closeProject()
    Assert.assertEquals("exportedDependencies", Nil, closedProject.project.exportedDependencies)
    Assert.assertEquals("sourceFolders", Nil, closedProject.project.sourceFolders)
    Assert.assertTrue("sourceOutputFolders", closedProject.project.sourceFolders.isEmpty)
  }

  /** Returns true if the expected regular expression matches the given error message. */
  private def similarErrorMessage(msg: String)(expected: String): Boolean = {
    msg.matches(expected)
  }

  lazy val changedFooScala = """
    package subpack

class Foo1
"""

  lazy val originalFooScala = """
    package subpack

class Foo
"""

  /** Each error message is a regular expression. This allows some variation between compiler versions. */
  lazy val expectedMessages = List(
    "(object )?Foo is not a member of (package )?subpack",
    "not found: type Foo")
}
