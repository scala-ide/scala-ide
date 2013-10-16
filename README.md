scala-ide-plugin.g8
===================

Giger8 template for Eclipse plugins based on the Scala IDE.

This template produces 5 Eclipse plugins:

* the plugin itself
* the `plugin.tests` fragment
* an Eclipse feature
* an Eclipse source feature
* an Eclipse update-site

The projects can readily be imported inside Eclipse. Additionally, you have maven `pom` files
based on Tycho, enabling command line builds.

## Building:

This template uses [plugin-profiles](https://github.com/scala-ide/plugin-profiles) to manage the build. Check its documentation for detailed information. The command to use looks like this:

    mvn -Pscala-2.10.x,eclipse-indigo,scala-ide-stable clean install

