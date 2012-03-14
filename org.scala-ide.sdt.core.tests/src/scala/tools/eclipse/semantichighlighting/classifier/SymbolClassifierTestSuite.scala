package scala.tools.eclipse.semantichighlighting.classifier

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(value = classOf[Suite])
@Suite.SuiteClasses(value = Array(
  classOf[AnnotationTest],
  classOf[CaseClassTest],
  classOf[CaseObjectTest],
  classOf[ClassTest],
  classOf[LazyLocalValTest],
  classOf[LazyTemplateValTest],
  classOf[LocalValTest],
  classOf[LocalVarTest],
  classOf[MethodParamTest],
  classOf[MethodTest],
  classOf[ObjectTest],
  classOf[PackageTest],
  classOf[TemplateValTest],
  classOf[TemplateVarTest],
  classOf[TraitTest],
  classOf[TypeParameterTest],
  classOf[TypeTest]))
class SymbolClassifierTestSuite