#!/bin/sh

export MAVEN=/opt/apache-maven-3.0-beta-1/bin/mvn
export MAVEN_REPO_LOCAL=./m2repo
export SCALA_TOOLCHAIN_VERSION=2.8.0-RC6
export SCALA_VERSION=2.8.0.RC6

${MAVEN} \
-U \
-Dmaven.repo.local=${MAVEN_REPO_LOCAL} \
-Dscala.toolchain.version=${SCALA_TOOLCHAIN_VERSION} \
-Dscala.version=${SCALA_VERSION} \
-f toolchain-pom.xml clean install

${MAVEN} \
-U \
-Dmaven.repo.local=${MAVEN_REPO_LOCAL} \
-Dscala.toolchain.version=${SCALA_TOOLCHAIN_VERSION} \
-Dscala.version=${SCALA_VERSION} \
clean package
