#!/bin/sh

. $(dirname $0)/env.sh

# Scala compiler version to be used as a dependency
SCALA_VERSION=2.9.2-SNAPSHOT

# Scala library version
SCALA_LIBRARY_VERSION=2.9.1

#THIS NEEDS TO BE UPDATED ONCE WE HAVE SNAPSHOTS

# The precompiled compiler-interface.jar that SBT uses to communicate with scalac
# This is usually the same as Scala version, and it is not enough to be just binary 
# compatible (compiler interface depends on scalac, not just std library)
SCALA_PRECOMPILED=2_9_1

# The scala version that was used to compile SBT dependencies (it is part of the artifact name)
# It is usually enough to have a binary compatible version of SCALA_VERSION
# For instance, here we can use 2.9.0, for a 2.9.1 compiler
SBT_SCALA_VERSION=2.9.1

SBINARY_VERSION=0.4.0

set_version ${SCALA_VERSION}

PROFILE="-P sbt-2.9,default"

build $*
