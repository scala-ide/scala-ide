# Goals

... Currently :

* to provide target platform definition usable into eclipse
  * to compile any bundle with the latest version of other bundles (scalariform, refactoring,...)
    * without needed to be installed in current eclipse
    * without required to have every bundle open
  * to run bundle of scala-ide with an other version of eclipse (ex: code under helios (3.6) launch under galileo (3.5.2)
* to share definition between developers 

... Future :

* to be used by itest (integration tests)
* to be used by ui test (swtbot ??)
* to be used by tycho to build


# Usage :

## e35_scala281.target

(I'm a newbee is eclipse PDE) So to have a workable platform you need to :
1. build toolchain
2. build local-2.8.1.sh (because the target currenlty used the generated update-site to provide the other plugins of scala-ide
3. import `platform` project into eclipse
4. select e35_scala281 as current target
  * via Preferences
  * via target editor (link upper + right)  