# Contributing to Scala IDE

## General Workflow

This is the process for committing code into master. There are of course exceptions to these rules, for example minor changes to comments and documentation, fixing a broken build etc.

1. Make sure you have signed the [Typesafe CLA](http://www.typesafe.com/contribute/cla), if not, sign it online.
2. Before starting to work on a feature or a fix, you have to make sure that there is a ticket for your work in the project's issue tracker. If not, create it first.
3. You should always perform your work in a Git branch. The branch should be given a descriptive name that explains its intent and the ticket number should be suffixed, e.g., scala-java-interop-1000508.
4. When the feature or fix is completed you should open a [Pull Request](https://help.github.com/articles/using-pull-requests) on GitHub.
5. The Pull Request should be reviewed by other maintainers (as many as feasible/practical). Note that the maintainers can consist of outside contributors, both within and outside the [Scala IDE Team](http://scala-ide.org/team). Outside contributors are encouraged to participate in the review process, it is not a closed process.
6. After the review you should fix the issues as needed, **pushing the changes as additional commits**, iterating until the reviewers give their thumbs up.
    - If after the review no action is taken from the contributor for more than 2 weeks, the Pull Request will be closed. Of course, you are free to reopen it as soon as you have addressed the reviewers’ comments.
7. Once the code has passed review, it’s ok to amend commits as it makes sense (see the ‘Creating Commits And Writing Commit Messages’ section below).
8. The Pull Request can be merged into the master branch.
9. If the code change needs to be applied to other branches as well, create pull requests against those branches which contain the change after rebasing it onto the respective branch and await successful verification by the continuous integration infrastructure; then merge those pull requests.
10. Once everything is said and done, associate the ticket with the “earliest” release branch (i.e. if back-ported so that it will be in release x.y.z, find the relevant milestone for that release) and close it.

## Pull Request Requirements

For a Pull Request to be considered at all it has to meet these requirements:

1. Live up to the current code standard:
   - Not violate [DRY](http://programmer.97things.oreilly.com/wiki/index.php/Don%27t_Repeat_Yourself).
   - [Boy Scout Rule](http://programmer.97things.oreilly.com/wiki/index.php/The_Boy_Scout_Rule) needs to have been applied.
2. Regardless if the code introduces new features or fixes bugs or regressions, it must have comprehensive tests. The only exception is UI code, because our infrastructure allows to run test only in a headless environment.
3. The code must be well documented.
4. User documentation should be provided for all new features. This can be done by opening a Pull Request in our [Documentation](https://github.com/scala-ide/docs) repository.
5. Rebase you branch on the latest master if it can’t be cleanly merged.
6. The Pull Request validator successfully builds. What the pull request validator does is making sure that the current master branch would still compile fine after the currently in-review Pull Request is merged.
    - The Pull Request validator will start within 1 hour from the moment you opened the Pull Request.
    - If you want to force the Pull Request validator to run again, you can do so by adding a new comment in the Pull Request with the following text: ``PLS REBUILD ALL``. Again, the Pull Request validator will kick-in within 1 hour.


If these requirements are not met then the code should **not** be merged into master, or even reviewed - regardless of how good or important it is. No exceptions. For any question, please drop us a message in the [scala-ide-dev](http://groups.google.com/group/scala-ide-dev) mailing list

## Creating Commits And Writing Commit Messages

Follow these guidelines when creating public commits and writing commit messages.

1. If your work spans multiple local commits (for example; if you do safe point commits while working in a feature branch or work in a branch for long time doing merges/rebases etc.) then please do not commit it all but rewrite the history by squashing the commits into as few as it makes sense.Every commit should be able to be used in isolation, cherry picked etc.
2. First line should be a descriptive sentence what the commit is doing. It should be possible to fully understand what the commit does by just reading this single line. It is **not ok** to only list the ticket number, type "minor fix" or similar. If the commit is a small fix, then go to 4. Otherwise, keep reading.
3. Following the single line description should be a blank line followed by a detailed description of the problem the commit solves and justify your solution . For more info, read this article: [Writing good commit messages](https://github.com/erlang/otp/wiki/Writing-good-commit-messages).
4. Add keywords for your commit (depending on the degree of automation we reach, the list may change over time):
    * ``Review by @gituser`` - if you want to notify someone on the team. The others can, and are encouraged to participate.
    * ``backport to _branch name_`` - if the fix needs to be cherry-picked to another branch (like /release/scala-ide-2.0.x, etc)
    * ``Fix #ticket`` - if the commit fixes a ticket (or``Fix #ticket1``, ..., ``Fix #ticketN``, if it fixes several tickets).
    * ``Re #ticket`` - if a commit is related to a ticket, without fixing it.

Example:

    Corrected semantic highlighting for methods

    Details 1

    Details 2

    Details 3

    Fix #2731, Fix #2732, Re #2733

## Resources

* [Website](http://scala-ide.org/)
* [Contributor License Agreement](http://www.typesafe.com/contribute/cla)
* [Issue Tracker](http://scala-ide-portfolio.assembla.com/spaces/scala-ide/support/tickets)
* [Developer Documentation](http://scala-ide.org/docs/dev/index.html)
* [scala-ide-dev mailing list](https://groups.google.com/group/scala-ide-dev)
