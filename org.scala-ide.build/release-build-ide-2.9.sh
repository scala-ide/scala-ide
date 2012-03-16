#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.9.2-RC1

PROFILE="-P release-ide-29"

build $*
