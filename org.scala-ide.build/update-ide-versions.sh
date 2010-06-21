#!/bin/sh

export MAVEN=/opt/apache-maven-3.0-beta-1/bin/mvn
export MAVEN_REPO_LOCAL=./m2repo

${MAVEN} \
-Dmaven.repo.local=${MAVEN_REPO_LOCAL} \
-N versions:update-child-modules
