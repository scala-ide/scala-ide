SBT support in Eclipse
======================

The purpose of this project is to provide good support for SBT projects using a multi step compilation task, like Play projects.

The initial goals are to enable importing vanilly SBT projects, driving the build process from Eclipse and provide an output console.

## Using:

sbt-remote-control and plugin-profiles need to be deployed locally.

To deploy sbt-remote-control locally, clone the `https://github.com/sbt/sbt-remote-control` project, check out the right commit (the hash is part of the version number in the top `pom.xml`) and run `sbt publishM2`.

To deploy plugin-profiles locally, clone the `https://github.com/sschaef/plugin-profiles` project, check out branch `update-to-luna` and run `mvn clean install`.

## Building:

This template uses [plugin-profiles](https://github.com/scala-ide/plugin-profiles) to manage the build. Check its documentation for detailed information. A script with good default parameters is provided:

    ./build.sh

# Nightly build:

A nightly build for Juno/Kepler on Scala 2.10.x is available at: http://download.scala-ide.org/plugins/sbt-integration/nightly/e38/2.10.x/site/
