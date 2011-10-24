package scala.tools.eclipse.jcompiler

import scala.tools.eclipse.contribution.weaving.jdt.jcompiler.IMethodVerifierProvider
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import scala.tools.eclipse.{ ScalaPlugin, ScalaPresentationCompiler }
import org.eclipse.core.runtime.Path
import org.eclipse.core.resources.ResourcesPlugin
import scala.tools.eclipse.ScalaProject
import org.eclipse.core.resources.IProject
import scala.tools.eclipse.util.HasLogger
import scala.tools.eclipse.util.Utils

/**
 * <p>
 * This class is instantiated by a custom extension point created while weaving on
 * `MethodVerifier.checkAbstractMethod(MethodBinding)`, which is a JDT internal class
 * that can't access and extend.
 * </p>
 * <p>
 * We need to hook into the abstract method's check because JDT method's verifier
 * assumes Java code. Specifically, incorrect errors are reported by the verifier
 * for the following scenario:
 *   - Assume a trait declaring only non-deferred (i.e., concrete) members, let's call it trait `A`
 *   - An abstract Scala class that inherits from trait `A`. Let's call this class `B`
 *   - A (concrete) Java class that inherits from the abstract Scala class `B`. Let's call this class `C`
 *
 * The issue is that traits are exposed as interfaces to JDT. Therefore, the method verifiers
 * (correctly) expects that all members declared in an interface should have an implementation
 * in the concrete Java class `C`. Hence, we need to hook into the abstract method's check
 * to avoid JDT from reporting missing implementation errors for non-deferred members that
 * are inherited from a Scala trait.
 * </p>
 * <p>
 * @see scala.tools.eclipse.contribution.weaving.jdt.jcompiler.MethodVerifierAspect
 * in `org.scala-ide.sdt.aspects` project for checking how the extension point is created.
 * </p>
 */
class ScalaMethodVerifierProvider extends IMethodVerifierProvider with HasLogger {
  import ScalaMethodVerifierProvider.JDTMethodVerifierCarryOnMsg

  /**
   * Checks that `abstractMethod` is a non-deferred member of a Scala Trait.
   */
  def isConcreteTraitMethod(abstractMethod: MethodBinding): Boolean = {
    val project = findProjectOf(abstractMethod)
    
    ScalaPlugin.plugin.asScalaProject(project) match {
      case Some(scalaProject) =>
        Utils.tryExecute {
          isConcreteTraitMethod(abstractMethod, scalaProject)
        }(orElse = false)
      case None =>
        logger.debug("`%s` is not a Scala Project. %s".format(project.getName(), JDTMethodVerifierCarryOnMsg))
        false
    }

  }

  private def findProjectOf(abstractMethod: MethodBinding): IProject = {
    // File name containing the abstractMethod definition. 
    // Note that the returned path contains includes the project's folder where the file resides. 
    val qualifiedFileName = abstractMethod.declaringClass.getFileName().mkString
    // File containing the `abstractMethod` definition. From a file we can find the project the file belongs to.
    val file = ResourcesPlugin.getWorkspace().getRoot().getFile(Path.fromPortableString(qualifiedFileName))

    val project = file.getProject()

    logger.debug("Found definition for `%s` in file `%s` of project `%s`".format(abstractMethod, qualifiedFileName, project.getName()))

    project
  }

  private def isConcreteTraitMethod(abstractMethod: MethodBinding, project: ScalaProject): Boolean = {
    project.withPresentationCompiler { pc =>
      pc.askOption { () =>
        import pc._
        /** Find the method's symbol for the given `abstractMethod` definition. */
        def findMethodSymbol(methodOwner: Symbol, abstractMethod: MethodBinding): Option[Symbol] = {
          // first find (if it exists) the scala's compiler symbol associated to the `abstractMethod`
          methodOwner.info.members.find { m =>

            def haveSameTpeParams(abstractMethod: MethodBinding, method: Symbol) = {
              val fps = m.paramss.flatten
              
              val javaSig = javaSigOf(method)
              
              // mapping Scala params' types to be Java conform, so that comparison
              // with `abstractMethod` is meaningful
              val paramsTypeSigs =
                if(javaSig.isDefined) javaSig.paramsType.map(_.mkString)
                else fps.map(s => s.info.typeSymbol.fullName).toArray

              if (abstractMethod.parameters.length == paramsTypeSigs.size) {
                val pairedParams = paramsTypeSigs.zip(abstractMethod.parameters.map(_.readableName().mkString))
                pairedParams forall { case (jdtTpe, scalacTpe) => jdtTpe == scalacTpe }
              } else
                false
            }

            // overloading on the return type is not allowed neither on Java nor in Scala, 
            // which implies that we don't need to compare the method's return type.
            m.encodedName == abstractMethod.selector.mkString && haveSameTpeParams(abstractMethod, m)
          }
        }

        /** find the method owner scalac symbol for the provided JDT `abstractMethod`.*/
        def findMethodOwnerSymbol(abstractMethod: MethodBinding) = {
          val packageName = abstractMethod.declaringClass.getPackage().readableName().mkString
          val typeName = abstractMethod.declaringClass.qualifiedSourceName().mkString
          logger.debug("Looking for class symbol in package `%s` for name `%s`" format (packageName, typeName))

          // When looking for the typename, the strategy is different when the type is defined in the
          // the empty package.
          if (packageName.isEmpty())
            pc.definitions.EmptyPackage.info.member(typeName.toTypeName)
          else {
            try {
              pc.definitions.getModule(packageName.toTermName).info.member(typeName.toTypeName)
            } catch {
              case _ =>
                logger.info("Failed to retrieve class symbol for `%s`".format(packageName + "." + typeName))
                NoSymbol
            }
          }
        }
        
        def isConcreteMethod(methodOwner: Symbol, abstractMethod: MethodBinding) = {
          // Checks if `methodOwner`'s contain a non-deferred (i.e. concrete) member that matches `abstractMethod` definition
          val methodSymbol = findMethodSymbol(methodOwner, abstractMethod)
          val isConcreteMethod = methodSymbol.nonEmpty && {
            val isDeferredMethod = methodSymbol.exists(_.isDeferred)
            logger.debug("found %s method symbol: %s" format (abstractMethod.selector.mkString, methodSymbol))
            !isDeferredMethod
          }
          isConcreteMethod
        }

        val methodOwner = findMethodOwnerSymbol(abstractMethod)
        // makes sure the symbol has been fully initialized. This is needed for example after a project's clean to ensure 
        // the symbol's flags are correctly set.
        methodOwner.initialize

        methodOwner.isTrait && isConcreteMethod(methodOwner, abstractMethod)
      }.getOrElse {
        logger.info("`askOption` failed. Check the Presentation Compiler log for more information. %s".format(JDTMethodVerifierCarryOnMsg))
        false
      }
    } {
      logger.info("Failed to instantiate Presentation Compiler. %s".format(JDTMethodVerifierCarryOnMsg))
      false
    }
  }
}

object ScalaMethodVerifierProvider {
  private final val JDTMethodVerifierCarryOnMsg = "Let JDT method's verifier carry on the check"
}