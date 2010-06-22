#!/bin/sh

source $(dirname $0)/env.sh

${MAVEN} \
-N versions:update-child-modules
