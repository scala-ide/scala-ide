package scala.tools.eclipse
package buildmanager
package sbtintegration

import java.io.File

import scala.Option.option2Iterable
import scala.collection.Seq
import scala.tools.eclipse.contribution.weaving.jdt.jcompiler.BuildManagerStore
import scala.tools.eclipse.util.EclipseResource
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.NoPosition
import scala.tools.nsc.MissingRequirementError
import scala.tools.nsc.Settings

import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.launching.JavaRuntime
import org.eclipse.jdt.core.{ JavaCore, IJavaProject }
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.contribution.weaving.jdt.jcompiler.BuildManagerStore
import sbt.CompileOrder.JavaThenScala
import sbt.CompileOrder.Mixed
import sbt.CompileSetup.equivCompileSetup
import sbt.classpath.ClasspathUtilities
import sbt.compiler.CompilerArguments
import sbt.inc.Analysis
import sbt.inc.Incremental
import sbt.inc.IncrementalCompile
import sbt.inc.Locate
import sbt.ClasspathOptions
import sbt.CompileOptions
import sbt.CompileOrder
import sbt.CompileSetup
import sbt.IO
import xsbti.api.Source
import xsbti.AnalysisCallback
import xsbti.Controller
import xsbti.Reporter

class AnalysisCompile (conf: BasicConfiguration, bm: EclipseSbtBuildManager, contr: Controller) extends HasLogger {
    private lazy val store = bm.analysisStore
    
    private def withBootclasspath(args: CompilerArguments, classpath: Seq[File]): Seq[File] =
      args.bootClasspath ++ args.finishClasspath(classpath)
    implicit def toAbstractFile(files: Seq[File]): Set[AbstractFile] =
      files.flatMap(f => EclipseResource.fromString(f.getPath)).toSet

    def removeSbtOutputDirs(args: List[String]) = {
      val outputOpt = "-d"
      val left = args.takeWhile(_ != outputOpt)
      val right = args.dropWhile(_ != outputOpt)
      right match {
        case d::out::rest =>
          (left:::rest).toSeq
        case _ =>
          // something is wrong 
          assert(false, "Incorrect configuration for compiler arguments: " + args)
          args.toSeq
      }
    }
    
    def getJdkPath(jProject: IJavaProject) = {
      val rawClasspath = bm.project.javaProject.getRawClasspath()
      rawClasspath.toSeq.flatMap(cp =>
        cp.getEntryKind match {
          case org.eclipse.jdt.core.IClasspathEntry.CPE_CONTAINER =>
            val path0 = cp.getPath
            if (!path0.isEmpty && path0.segment(0) == JavaRuntime.JRE_CONTAINER) {
              val container = JavaCore.getClasspathContainer(cp.getPath, bm.project.javaProject)
              Some(container.getClasspathEntries.toSeq.map(_.getPath.toFile))
            } else None
          case _ => None
          
        }).flatten
    }

    
    def doCompile(scalac: ScalaSbtCompiler, javac: JavaEclipseCompiler,
              sources: Seq[File],  reporter: Reporter, settings: Settings,
              compOrder: CompileOrder.Value, compOptions: Seq[String] = Nil,
              javaSrcBases: Seq[File] = Nil, javacOptions: Seq[String] = Nil, 
              analysisMap: Map[File, Analysis] = Map.empty, maxErrors: Int = 100)(implicit log: EclipseLogger): Analysis = {
        val currentSetup = new CompileSetup(conf.outputDirectory, new CompileOptions(compOptions, javacOptions),
                                         scalac.scalaInstance.actualVersion, Mixed)
        import currentSetup._

        val getAnalysis = analysisMap.get _
        val getAPI = (f: File) => {
            val extApis = getAnalysis(f) match { case Some(a) => a.apis.external; case None => Map.empty[String, Source] }
            extApis.get _
        }
        val apiOption = (api: Either[Boolean, Source]) => api.right.toOption
        
        // Resolve classpath correctly
        val compArgs = new CompilerArguments(scalac.scalaInstance,
            // do not include autoBoot becuase then bootclasspath takes
            // whatever is set by the env variable and not necessarily what was given
            // in the project definition
            ClasspathOptions(bootLibrary = true, compiler = false, extra = true, autoBoot = false, filterLibrary = true))
        val jrePath = getJdkPath(bm.project.javaProject)
        val classpathWithoutJVM: Set[File] = conf.classpath.toSet -- jrePath
        val searchClasspath = classpathWithoutJVM ++ jrePath
        val entry = Locate.entry(searchClasspath.toSeq, Locate.definesClass) // use default defineClass for now

        
        val ((previousAnalysis, previousSetup), tm) = util.Utils.timed(extract(store.get))
        
        logger.debug("API store loaded in %0,3d ms".format(tm))
        logger.debug("\t" + previousAnalysis)

        val compile0 = (include: Set[File], callback: AnalysisCallback) => {
            conf.outputDirectories.foreach(IO.createDirectory)
            val incSrc = sources.filter(include)
            logger.info("Compiling:\n\t" + incSrc.mkString("\n\t"))
            bm.buildingFiles(toAbstractFile(incSrc))
            val (javaSrcs, scalaSrcs) = incSrc partition javaOnly
            
            var scalaError: Option[xsbti.CompileFailed] = None
            
            def throwLater() {
              scalaError match {
                case Some(err) => throw err
                case _ => ()
              }              
            }
            
            def compileScala() =
              if(!scalaSrcs.isEmpty) {
                val sources0 = if(order == Mixed) incSrc else scalaSrcs
                val argsWithoutOutput = removeSbtOutputDirs(compArgs(sources0, classpathWithoutJVM.toSeq, conf.outputDirectory, options.options).toList)
                
                val bootClasspathArgs: String = CompilerArguments.absString(jrePath) + File.pathSeparator + scalac.scalaInstance.libraryJar.getAbsolutePath
                val arguments = Seq("-bootclasspath", bootClasspathArgs) ++ argsWithoutOutput
                settings.javabootclasspath.tryToSet(List(bootClasspathArgs)) // otherwise scala compiler ignores JDK settings
                try {
                  scalac.compile(arguments, callback, maxErrors, log, contr, settings)
                } catch {
                  case err: xsbti.CompileFailed =>
                    scalaError = Some(err)
                }
              }
            def compileJava() =
              if(!javaSrcs.isEmpty) {
                import sbt.Path._
                val loader = ClasspathUtilities.toLoader(conf.classpath, scalac.scalaInstance.loader)
                def handleError(e: Throwable) {
                  logger.debug("Error running the SBT builder on Java sources:\n " + e)
                  logger.debug("Running a full Java build")
                  javac.build(org.eclipse.core.resources.IncrementalProjectBuilder.FULL_BUILD)
                  log.flush()
                }
                
                try {
                  def readAPI(source: File, classes: Seq[Class[_]]) { callback.api(source, sbt.ClassToAPI(classes)) }

                  BuildManagerStore.INSTANCE.setJavaSourceFilesToCompile(javaSrcs.toArray, conf.project.underlying)
                
                  sbt.classfile.Analyze(conf.outputDirectories, javaSrcs, log)(callback, loader, readAPI) {
                    javac.build(org.eclipse.core.resources.IncrementalProjectBuilder.INCREMENTAL_BUILD)
                    log.flush()
                  }
                  
                  BuildManagerStore.INSTANCE.setJavaSourceFilesToCompile(null, conf.project.underlying)
                } catch {
                  case e: Throwable => 
                    handleError(e)
                }
              }
            
            if(order == JavaThenScala) {
              compileJava(); compileScala()
              throwLater()
            } else {
              compileScala(); compileJava()
              // if we reached here, then it might be that compiling scala files failed but java succeded
              // it might be the case that we just want to proceed with compiling java files when scala succeded
              // this is still something that needs to be settled (in the latter case we won't see errors for java files)
              throwLater()
            }
        }
        
        try {
          import CompileSetup._
          
            val analysis = previousSetup match {
            case Some(previous) if equivCompileSetup.equiv(previous, currentSetup) => previousAnalysis
            case _ => Incremental.prune(sources.toSet, previousAnalysis)
          }
          
          // Seems ok to just provide conf.outputDirectory
          val (modified, result) : (Boolean, Analysis) = 
              IncrementalCompile(sources.toSet, entry, compile0, analysis, getAnalysis, conf.outputDirectory, log)
            
          // Store if necessary
          logger.info("Compilation was successful")
          //logger.info("Modified: " + modified + " Analysis: " + result + " apis " + result.apis)
          if (modified) {
            store.set(result, currentSetup)
          }
          result
        } catch {
          case e: xsbti.CompileFailed =>
            logger.info("Compilation failed")
            null
          case ex @ MissingRequirementError(required) =>
            reporter.log(SbtConverter.convertToSbt(NoPosition), "could not find a required class (incomplete classpath?): " + required, xsbti.Severity.Error)
            null
            
          case ex =>
            eclipseLog.error("Crash in the build compiler.", ex)
            reporter.log(SbtConverter.convertToSbt(NoPosition), "The SBT builder crashed while compiling your project. This is a bug in the Scala compiler or SBT. Check the Erorr Log for details. The error message is: " + ex.getMessage(), xsbti.Severity.Error)
            null
            
        } finally {
          log.flush()
        }
    }
    
    private def extract(previous: Option[(Analysis, CompileSetup)]): (Analysis, Option[CompileSetup]) =
      previous match {
        case Some((an, setup)) =>
//        	logger.debug("restore previous setup")
          (an, Some(setup))
        case None =>
//          logger.debug("previous step")
          (Analysis.Empty, None)
      }
    def javaOnly(f: File) = f.getName.endsWith(".java")
}