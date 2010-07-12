package scala.tools.eclipse.contribution.weaving.jdt.spellingengineprovider;

import org.eclipse.jdt.internal.ui.text.spelling.DefaultSpellingEngine;

@SuppressWarnings("restriction")
public privileged aspect SpellingEngineProviderAspect {

	pointcut defaultSpellingEngineCreations(DefaultSpellingEngine instance) : 
    execution(public DefaultSpellingEngine.new()) && this(instance);

	after(DefaultSpellingEngine instance) returning: defaultSpellingEngineCreations(instance) {
		for (ISpellingEngineProvider spellingEngineProvider : SpellingEngineProviderRegistry
				.getInstance().getProviders()) {
			instance.fEngines.put(
					DefaultSpellingEngine.JAVA_CONTENT_TYPE,
					spellingEngineProvider.getScalaSpellingEngine());
		}
	}
}