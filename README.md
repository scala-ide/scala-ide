SBT support in Eclipse
======================

The purpose of this project is to provide good support for SBT projects using a multi step compilation task, like Play projects.

The initial goals are to enable importing vanilly SBT projects, driving the build process from Eclipse and provide an output console.

## Building:

This template uses [plugin-profiles](https://github.com/scala-ide/plugin-profiles) to manage the build. Check its documentation for detailed information. A script with good default parameters is provided:

    ./build.sh

# Nightly build:

A nightly build for Juno/Kepler on Scala 2.10.x is available at: http://download.scala-ide.org/plugins/sbt-integration/nightly/e38/2.10.x/site/
