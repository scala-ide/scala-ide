the goal of the plugin is to provide a custom nsc.interactive (mainly by backporting code from scala trunk)
to ease migration to scala-2.9 and merge with branch wip_experiment

usefull command to update the code

svn co http://lampsvn.epfl.ch/svn-repos/scala/scala/trunk/src/compiler/scala/tools/nsc/interactive
meld interactive ~/work/oss/scala-eclipse/scala-ide-wip_exp_backport/org.scala-ide.sdt.compiler.ext/src/scala/tools/nsc/interactive 
meld interactive ~/work/oss/scala-eclipse/scala-ide-wip_exp_backport/org.scala-ide.sdt.compiler.ext/src_2.8.1/scala/tools/nsc/interactiv
