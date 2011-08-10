package scala.tools.eclipse.buildmanager.sbtintegration

import sbt.{Logger, IO, CompileSetup, CompileOptions,
            ClasspathOptions, CompileOrder}
import sbt.inc.{AnalysisFormats, AnalysisStore, Analysis,
	              FileBasedStore, Locate, IncrementalCompile,
	              Stamps, Stamp, ReadStamps, Incremental}
import sbt.compiler.{JavaCompiler, CompilerArguments}
import sbt.classpath.ClasspathUtilities

import xsbti.{AnalysisCallback, Reporter, Controller}
import xsbti.api.{Source}
import xsbt.{InterfaceCompileFailed}

import CompileOrder.{JavaThenScala, Mixed, ScalaThenJava}

import sbinary.DefaultProtocol.{ immutableMapFormat, immutableSetFormat, StringFormat }

import scala.collection.Seq
import scala.tools.nsc.io.AbstractFile
import scala.tools.eclipse.util.EclipseResource

import java.io.File

class CompilerArgsConstr {
    def apply(sources: Seq[File], out: File, classpath: Seq[File]): Seq[String] = {
      param("-d", abs(out).toString) ++ debugSbt ++ param("-classpath", classpath.map(_.toString).mkString(":")) ++ abs(sources)
    }
    
    private def debugSbt: Seq[String] = Seq("-Dxsbt.inc.debug=true")
    private def param(name: String, value: String): Seq[String] = Seq(name, value)
        
    private def abs(files: Seq[File]) = files.map(_.getAbsolutePath).sortWith(_ < _)
    private def abs(file: File) = file.getAbsolutePath
}

class AnalysisCompile (conf: BasicConfiguration, bm: EclipseSbtBuildManager, contr: Controller) {
    import AnalysisFormats._
    private lazy val store = AnalysisStore.sync(AnalysisStore.cached(FileBasedStore(conf.cacheDirectory)))
    
    private def withBootclasspath(args: CompilerArguments, classpath: Seq[File]): Seq[File] =
		  args.bootClasspath ++ args.finishClasspath(classpath)
		  
		implicit def toAbstractFile(files: Seq[File]): Set[AbstractFile] =
		  files.flatMap(f => EclipseResource.fromString(f.getPath)).toSet
    
    def doCompile(scalac: ScalaSbtCompiler, javac: JavaCompiler,
              sources: Seq[File],  reporter: Reporter,
              compOptions: Seq[String] = Nil, javaSrcBases: Seq[File] = Nil,
              javacOptions: Seq[String] = Nil, compOrder: CompileOrder.Value = Mixed,
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
        val compArgs = new CompilerArguments(scalac.scalaInstance, scalac.cp)
        val searchClasspath = withBootclasspath(compArgs, conf.classpath)
        val entry = Locate.entry(searchClasspath, Locate.definesClass) // use default defineClass for now
        
        val (previousAnalysis, previousSetup) = extract(store.get)
        	
        val compile0 = (include: Set[File], callback: AnalysisCallback) => {
            IO.createDirectory(conf.outputDirectory)
            val incSrc = sources.filter(include)
            println("Compiling:\n\t" + incSrc.mkString("\n\t"))
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
				      	val arguments = compArgs(sources0, conf.classpath, conf.outputDirectory, options.options)
				      	try {
				      	  scalac.compile(arguments, callback, maxErrors, log, contr)
				      	} catch {
				      	  case err: xsbti.CompileFailed =>
				      	    scalaError = Some(err)
				      	}
				      }
            def compileJava() =
              if(!javaSrcs.isEmpty) {
                import sbt.Path._
                val loader = ClasspathUtilities.toLoader(conf.classpath, scalac.scalaInstance.loader)
                def readAPI(source: File, classes: Seq[Class[_]]) { callback.api(source, sbt.ClassToAPI(classes)) }
	            	
                sbt.classfile.Analyze(conf.outputDirectory, javaSrcs, log)(callback, loader, readAPI) {
                  javac(javaSrcs, conf.classpath, conf.outputDirectory, options.javacOptions)
                  log.flush()
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
          
          val (modified, result) : (Boolean, Analysis) = 
              IncrementalCompile(sources.toSet, entry, compile0, analysis, getAnalysis, conf.outputDirectory, log)
            
          // Store if necessary
          //println("Modified: " + modified + " Analysis: " + result + " apis " + result.apis)
          if (modified) {
            store.set(result, currentSetup)
          }
          result
        } catch {
        	case e: xsbti.CompileFailed => 
        	  null
        } finally {
          log.flush()
        }
    }
    
    private def extract(previous: Option[(Analysis, CompileSetup)]): (Analysis, Option[CompileSetup]) =
      previous match {
        case Some((an, setup)) =>
//        	println("restore previous setup")
          (an, Some(setup))
        case None =>
//          println("previous step")
          (Analysis.Empty, None)
      }
    def javaOnly(f: File) = f.getName.endsWith(".java")
}