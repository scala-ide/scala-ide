Scala IDE for Eclipse
==============

This is the new home of the Scala IDE for Eclipse. Previously, source code was hosted on Assembla.

Tickets and documentation are still hosted by Assembla:

* https://www.assembla.com/spaces/scala-ide/tickets
* https://www.assembla.com/spaces/scala-ide/wiki


Build
-------

The Scala IDE for Eclipse can be built on the command line using Maven, as is done, for example, during the Jenkins-driven [nightly builds](http://jenkins.scala-ide.org) on jenkins.scala-ide.org. Using a Unix-like OS, the process is as follows ...

* Requirements,
    * The Git command line tools (this will be available as a standard package for Linux distributions)
    * A recent JDK [current Oracle JDK](the (http://www.oracle.com/technetwork/java/javase/downloads/index.html) is recommended)
    * [Maven 3](http://maven.apache.org/download.html)
* (Inital setup) Retrieve the source from its git repository, and make sure to checkout the `master` branch

```bash
# Clone and checkout master branch
$ git clone git://github.com/scala-ide/scala-ide.git
$ cd scala-ide
```

* (Subsequent updates) Update the source from the repository,

```bash
$ cd scala-ide
$ git pull
```

* Navigate to the build directory,

```bash
$ cd org.scala-ide.build
```

* Build using scripts provided,

```bash
# For builds relative to Scala 2.8.0.final
$ ./build-ide-2.8.3-SNAPSHOT
#
# For builds relative to Scala 2.9.0.final
$ ./build-ide-2.9.2-SNAPSHOT
#
# For builds relative to Scala trunk
$ ./build-ide-trunk.sh
```

Assuming your build is successful you should find an Eclipse update site has been built in `org.scala-ide.sdt.update-site/target/site` and a zipped version of the same at `org.scala-ide.sdt.update-site/target/site_assembly.zip`. You can install directly into Eclipse from this update site by adding it as a local update site via the Eclipse "Install New Software ..." action. Alternatively, if you make the unpacked site available via a web server, then the http URL of its root directory is acceptable as an ordinary Eclipse update site URL.

Working with local version of the Scala Presentation Compiler
==========================

  * Make your local changes in the scala compiler 
  * Build the scala compiler, package into maven format and deploy locally,

```bash
# You are in the main scala directory
$ ant distpack-opt
$ (cd dists/maven/latest; ant -Dlocal.snapshot.repository=${LOCAL_REPO} -Dlocal.release.repository=${LOCAL_REPO} deploy.snapshot.local)
# If you use the standard .m2 location for maven, then the last command reduces to
$ (cd dists/maven/latest; ant deploy.snapshot.local)
```

  * Go to directory where you cloned your scala-ide and run the necessary scripts that use your local trunk version

```bash
$ (cd org.scala-ide.build-toolchain; ./build-toolchain-local-trunk.sh)
$ (cd org.scala-ide.build; ./build-ide-local-trunk.sh)
```

If the build was successful you will end up with a right update-site - it contains the local changes you made in the scala compiler. It is possible to start the Eclipse instance with that plugin within your original workspace. You just have to have the following projects open `org.scala-ide.{sdt.core, scala.compiler, scala.library}`. Build `sdt.core` project within Eclipse and run the configuration using the weaving launcher.