the goal of the plugin is to provide a custom nsc.interactive (mainly by backporting code from scala trunk)
to ease migration to scala-2.9 and merge with branch wip_experiment

usefull command to update the code

svn co http://lampsvn.epfl.ch/svn-repos/scala/scala/trunk/src/compiler/scala/tools/nsc/interactive src_2.9.0-SNAPSHOT/scala/tools/nsc/interactive
svn update src_2.9.0-SNAPSHOT/scala/tools/nsc/interactive
meld src_2.8.1 src_2.9.0-SNAPSHOT
