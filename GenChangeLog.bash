#!/usr/bin/env bash

##############################################################################
# This scripts output a changelog for all changes from <since> to <until> It #
# makes many assumptions on how github works, mainly that all changes are    #
# made through pull requests.                                                #
#                                                                            #
# It adopts something akin to the standard SVN changelog format. The merge  #
# commit log (the pull request message) is used as a content string, and the #
# logs for the branch are grepped to find a mention of something looking     #
# like "fixes #1001234", indicating this commit fixes an Assembla ticket.    #
##############################################################################

DEBUG=""

function usage() {
    echo "Usage : $0 [-p] <since> <until>"
    echo "    -p : use GNU-style printing"
    echo "    -d : print debug output    "
}

function is_git_merge() {
    # merges have 2 parents, so 3 outputs to rev-list
    # non-merges have only one, so 1 output
    local nmerges=$(git rev-list -n 1 --parents $1 | awk '{print NF}')
    if [ $nmerges -gt 2 ]; then echo 1; fi
}

function grep_for_fixes {
    # This needs to be VERY dumb for the old BSD grep of OSX to
    # understand. Also, no -i (case-insensitive) mode.
    echo $(echo $@ | grep -oEe "[Ff][Ii][Xx][[:alpha:]]*?[[:space:]]*?#?100[0-9]{4}"|awk '{print $NF}')
}

# look for the command line options
# again, single-letter options only because OSX's getopt is limited
set -- $(getopt dp $*)
while [ $# -gt 0 ]
do
    case "$1" in
    (-d) DEBUG=yes;;
    (-p) PRETTY=yes;;
    (--) shift; break;;
    (-*) echo "$0: error - unrecognized option $1" 1>&2; usage; exit 1;;
    esac
    shift
done

# beginning and end of Changelog
start=$(git rev-parse $1)
end=$(git rev-parse $2)

if [ $DEBUG ]
then
    echo "I'm going to start from $start"
    echo "I'm going to end at $end"
    echo "GETOPTS:" $PRETTY
fi

commits=$(git rev-list --first-parent $end ^$start)

for m in $commits
do
    echo ""
    if [ $DEBUG ]; then
        echo $m
    fi
    if [ $(is_git_merge "$m" ) ]; then
        if [ $PRETTY ]; then
           # we want the author of the merge branch, not the one of the merge
           # commit
            realAuthorName=$(git log -1 --format='%an' $m^2)
            mergeDate=$(git log -1 --format='%aD' $m)
            logline=$(git log -1 --format='  * %b' $m)
            changeLogMsg=$(printf "%s - %s\n%s" "$mergeDate" "$realAuthorName" "$logline")
        else
            changeLogMsg=$(git log -1 --format=' - %b' $m)
        fi
        # $(git rev-list --parents -n 1 $m) is the set of direct parents of
        # the merge commits (plus the merge commit itself)
        PARENTSET=$(git rev-list --parents -n 1 $m)

        # $(git merge-base --octopus $PARENTSET) is the root of the merge
        # --octopus here just in case
        # FIXME: how does this behave if the PR is NOT rebased
        # before merge ? (we'd have to disambiguate with cherry)
        BRANCH_ORIGIN=$(git merge-base --octopus $PARENTSET)

        # $m^2 is the head of the merged branch, right before merge
        FINAL_COMMIT="$m^2"
        # $(git log <opts> $BRANCH_ORIGIN..$FINAL_COMMIT) is the log for the branch
        logtext=$(git log --format='%B' $BRANCH_ORIGIN..$FINAL_COMMIT)
    else
        if [ $PRETTY ]; then
            changeLogMsg=$(git log -1 --format='%aD - %an%n  * %s' $m)
        else
            changeLogMsg=$(git log -1 --format='- %s' $m)
        fi
        logtext=$(git log -1 --format=%B $m)
    fi
    fixes=$(grep_for_fixes $logtext)

    fixesString=""
    # fixesList=${fixeslist%*,}
    if [ $DEBUG ]; then
        printf "%s\n" ${fixes[@]} | paste -sd ',' -
    fi

    if [[ $PRETTY && ! -z $fixes ]]
    then
        fixesList=$(printf "%s\n" ${fixes[@]} | paste -sd ',' -)
        fixesString=$(echo -n "\n  * FIXES: $fixesList")
    else
        if [[ ! -z $fixes ]] ; then
        fixURLs=$(printf "%s\n" ${fixes[@]}| sed 's/#\(100[0-9][0-9][0-9][0-9]\)/:ticket:`\1`/g'|paste -sd ',' -)
        fixesString="($fixURLs)"
        fi
    fi

    echo -en "$changeLogMsg"
    echo -en " $fixesString"
done








