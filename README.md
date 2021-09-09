# Gerrit Code Review plugin for Jenkins

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/gerrit-code-review.svg)](https://plugins.jenkins.io/gerrit-code-review)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/gerrit-code-review.svg?color=blue)](https://plugins.jenkins.io/gerrit-code-review)
[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/gerrit-code-review-plugin/master)](https://ci.jenkins.io/job/Plugins/job/gerrit-code-review-plugin/job/master/)

This plugin allows any Jenkins multi-branch pipeline to be seamlessly
integrated with [Gerrit Code Review](https://gerritcodereview.com) for branches and changes validation.

## Why yet another plugin?

I wanted to use the Gerrit CI validation workflow with potentially any
project, including Gerrit plugins or anybody else wanting to adopt it.
I could just "copy & paste" our Groovy workflow, however, that does not
seem a sensible and long-term approach.
I wanted to have "something more" than a pure triggering mechanism: I
wanted to extend the power of Jenkisfile with the Gerrit review workflow
verbs.

## What about the Gerrit Trigger Plugin?

The **new plugin is not going to replace the current Gerrit Trigger
Plugin**, but would rather represent an alternative to simpler scenarios
when you just require a standard Jenkinsfile Gerrit validation workflow.
For all the current users of the Gerrit Trigger Plugin things wouldn't
change, unless they need a more Jenkisfile-integrated experience.

## Why not?

Why should I write yet another Gerrit/Jenkins plugin? Isn't Gerrit
Trigger Plugin
(<https://wiki.jenkins.io/display/JENKINS/Gerrit+Trigger>) enough?
We couldn't use it against
[gerrit-review.googlesource.com](http://gerrit-review.googlesource.com)
because stream events are just not accessible.

There are unresolved issues about:

-   **Stability**
    stream-events are based on SSH, which isn't scalable or reliable
    against downtime, doesn't allow smart Git routing
-   **Complex configuration**
    requires a node-level and project-level configuration, which is
    orthogonal to the Jenkinsfile pipeline
-   **Integration**
    using it inside a Jenkinsfile isn't that straightforward and
    multi-branch projects aren't supported either

## Why a new name?

The new name "Gerrit Code Review Plugin" indicates Gerrit as the
first-class citizen of the Jenkins ecosystem, and not just as an "extra
triggering or validation" logic of a Jenkins job.

One key aspect will be: stateless, configuration-less apart the standard
SCM configuration settings.
That means that multiple Jobs, multiple branches of the same Job, can
have their own Gerrit integration defined and working out-of-the-box.

No more people asking "how do I configure the Gerrit integration"? it
will just work.

## Jenkins Setup

### Using Multibranch Pipeline

Create a new `Multibranch Pipeline` item.

Select `Branch Source` of type `Gerrit`.

Specify project repository URL, only `http` or `https` based protocol is
supported at this point, copy the URL from project settings at Gerrit.

Trigger `Scan Multibranch Pipeline now` either manually or by external
trigger.

Notice the `Changes` tab at the job information, per each review an entry will
be created.

#### Remote Trigger

Remote trigger is possible using webhook, URL is
`https://jenkins/prefix/gerrit-webhook/`.

Content:

```json
{
  "project": {
    "name": "project1"
  }
}
```

Example:

```sh
$ curl -X POST -d '{"project":{"name":"project1"}}' 'https://jenkins/prefix/gerrit-webhook/'
```

### Using Gerrit Trigger Plugin

Configure Gerrit Trigger Plugin normally.

At the job that being triggered, add a parameter with the name of
`GERRIT_CREDENTIALS_ID` with default value of a credentials id that can access
Gerrit using RestAPI.

### Using Environment Variables

| Key                            | Description                                                      |
| ------------------------------ | ---------------------------------------------------------------- |
| GERRIT_API_URL                 | Gerrit API URL, onlhy `http` and `https` protocols are supported |
| GERRIT_CHANGE_URL              | Gerrit change URL to parse GERRIT_API_URL out if missing         |
| GERRIT_API_INSECURE_HTTPS      | If set to `true` certificate validation will be disabled         |
| GERRIT_CREDENTIALS_ID          | Jenkins credentials object id                                    |
| GERRIT_PROJECT                 | Gerrit project name                                              |
| BRANCH_NAME                    | Gerrit reference name nn/nnnn/n                                  |
| GERRIT_CHANGE_NUMBER           | Gerrit change number                                             |
| GERRIT_PATCHSET_NUMBER         | Gerrit revision                                                  |
| GERRIT_CHANGE_PRIVATE_STATE    | true if the Gerrit change is private                             |
| GERRIT_CHANGE_WIP_STATE        | true if the Gerrit change is WIP                                 |
| GERRIT_CHANGE_SUBJECT          | Gerrit change headline                                           |
| GERRIT_BRANCH                  | target branch of the Gerrit change                               |
| GERRIT_TOPIC                   | topic name (if any) of the Gerrit change                         |
| GERRIT_CHANGE_ID               | Gerrit change id                                                 |
| GERRIT_CHANGE_URL              | Gerrit change URL                                                |
| GERRIT_PATCHSET_REVISION       | SHA1 of the Gerrit patch-set                                     |
| GERRIT_CHANGE_OWNER            | Owner (name <email>) of the Gerrit change                        |
| GERRIT_CHANGE_OWNER_NAME       | Owner name of the Gerrit change                                  |
| GERRIT_CHANGE_OWNER_EMAIL      | Owner e-mail of the Gerrit change                                |
| GERRIT_PATCHSET_UPLOADER       | Uploader (name <email>) of the Gerrit patch-set                  |
| GERRIT_PATCHSET_UPLOADER_NAME  | Uploader name of the Gerrit patch-set                            |
| GERRIT_PATCHSET_UPLOADER_EMAIL | Uploader e-mail of the Gerrit patch-set                          |
| GERRIT_REFNAME                 | Git ref name of the change/patch-set                             |
| GERRIT_REFSPEC                 | Git ref name of the change/patch-set (*)                         |

(*) Added for compatibility with the [gerrit-trigger-plugin](https://plugins.jenkins.io/gerrit-trigger/)

When the Jenkinsfile is discovered through a multi-branch pipeline, the above environment
variables related to Gerrit and the associated change/patch-set would be automatically
discovered and made available to the pipeline steps.

For the pipeline projects (non-multibranch) the variables would need to be set through
an external triggering job (e.g. Gerrit Trigger Plugin).

### Integrating with the Gerrit Checks plugin

The [Gerrit Checks plugin](https://gerrit-review.googlesource.com/Documentation/config-plugins.html#checks)
provides a different approach to integrate CI systems with Gerrit. The
GerritCodeReview-plugin supports the usage of the checks plugin, making it even
more convenient to integrate automated verification into the code review.

To build changes with pending checks, create a new `Multibranch Pipeline` item
and select `Branch Source` of type `Gerrit` as described above. Then add the
`Filter by Pending Checks`-behaviour to the `Gerrit Branch Source`. To select
checkers, which should be checked for whether their checks are pending, under
`Query Operator` either select to query pending checks by checker scheme to
select a whole group of checkers or by a specific checker UUID, to only query
by a specific checker. In the `Query String`-field enter the scheme name or checker
UUID respectively.

Jenkins will then only start builds for changes that have pending checks handled
by the configured checkers and will set the status of the check to `SCHEDULED`.

## Jenkinsfile Steps

Gerrit Code Review plugin provides steps for allowing to post the
review feedback back to the originating change and adding extra comments
to the code that has been built.

### ```gerritReview```

Add a review label to the change/patchset that has triggered the
multi-branch pipeline job execution.

Parameters:

- ```labels``` The labels associated to the review, a dictionary, key is label
  name and value is the score.

- ```message```
  Additional message provided as explanation of the the review feedback.
  Default: ''

### ```gerritComment```

Add a review comment to the entire file or a single line.
All the comments added during the pipeline execution are going to be
published upon the execution of the ```gerritReview``` step.

Parameters:

- ```path```
  Relative path of the file to comment. Mandatory.

- ```line```
  Line number of where the comment should be added. When not specified
  the comment apply to the entire file.

- ```message```
  Comment message body. Mandatory.

### Declarative pipeline example

> Note: the gerrit DSL helper was removed in 0.3, please use the following.

```groovy
pipeline {
    agent any
    stages {
        stage('Example') {
            steps {
                gerritReview labels: [Verified: 0]
                echo 'Hello World'
                gerritComment path:'path/to/file', line: 10, message: 'invalid syntax'
            }
        }
    }
    post {
        success {
            gerritReview labels: [Verified: 1]
            gerritCheck checks: ['example:checker': 'SUCCESSFUL']
        }
        unstable { gerritReview labels: [Verified: 0], message: 'Build is unstable' }
        failure {
            gerritReview labels: [Verified: -1]
            gerritCheck checks: ['example:checker': 'FAILED'], message: 'invalid syntax'
        }
    }
}
```

### Scripted pipeline example

```groovy
node {
  checkout scm
  try {
    gerritReview labels: [Verified: 0]
    stage('Hello') {
      echo 'Hello World'
      gerritComment path:'path/to/file', line: 10, message: 'invalid syntax'
    }
    gerritReview labels: [Verified: 1]
    gerritCheck checks: ['example:checker': 'SUCCESSFUL']
  } catch (e) {
    gerritReview labels: [Verified: -1]
    gerritCheck checks: ['example:checker': 'FAILED']
    throw e
  }
}
```

## Issues

The issues are tracked on the [Jenkins Issues portal](https://issues.jenkins-ci.org/issues/?jql=project%20%3D%20JENKINS%20AND%20status%20in%20(Open%2C%20%22In%20Progress%22%2C%20Reopened)%20AND%20component%20%3D%20gerrit-code-review-plugin)
under the `gerrit-code-review-plugin` component.

## Plugin Releases

I have presented the first prototype of this new plugin at the Jenkins
World Conference in San Francisco back in 2017 inside my "Data-Driven
Pipeline workshop."
(<https://jenkinsworld20162017.sched.com/event/APTd/data-driven-pipeline-workshop-free>)

The first version was published and available in the Jenkins Plugin
Registry since April 2018 and has been used so far by hundreds of
developers around the world that provided already very useful feedback
and started contributing with pull-requests.

### v0.4.7 - Released - 6 Sep 2021

#### Fixes

- [I732c14](https://review.gerrithub.io/q/I732c14) Avoid updating Gerrit Checks to SCHEDULED status

### v0.4.6 - Released - 5 Sep 2021

#### Fixes

- [c50c5ea](https://review.gerrithub.io/q/c50c5ea) Fix timestamp parsing for Checks JSON payload

#### Known issues

Requires to allow the serialization of the following classes through Jenkins's remoting:
- com.google.gerrit.extensions.common.AvatarInfo
- com.google.gerrit.extensions.common.ReviewerUpdateInfo
- com.google.gerrit.extensions.common.TrackingIdInfo

Add the following extra JVM options when starting Jenkins:
```
export JAVA_OPTS='-Dhudson.remoting.ClassFilter=com.google.gerrit.extensions.common.AvatarInfo,com.google.gerrit.extensions.common.ReviewerUpdateInfo,com.google.gerrit.extensions.common.TrackingIdInfo'
```

### v0.4.5 - Released - 3 Sep 2021

#### New features

- [d0f1c20](https://review.gerrithub.io/q/d0f1c20) Support Gerrit < 2.15
- [d73aa8b](https://review.gerrithub.io/q/d73aa8b) Expose GERRIT_REFSPEC variable, like gerrit-trigger

#### Fixes

- [JENKINS-61469](https://issues.jenkins-ci.org/browse/JENKINS-61469) Persist change rev over rebuilds of child jobs
- [JENKINS-63437](https://issues.jenkins-ci.org/browse/JENKINS-63437) Resolve improper timestamp format
- [JENKINS-64899](https://issues.jenkins-ci.org/browse/JENKINS-64899) Add exemptions to ClassFilter
- [JENKINS-61107](https://issues.jenkins-ci.org/browse/JENKINS-61107) Avoid excessive query for change-info
- [05ca16d](https://review.gerrithub.io/q/05ca16d) Fix prefix extract when project contain dots
- [b8368cf](https://review.gerrithub.io/q/b8368cf) Add more diagnostic data when update Checks API fails

### v0.4.4 - Released - 12 June 2020

#### Fixes

- [JENKINS-62648](https://issues.jenkins-ci.org/browse/JENKINS-62648)
  Fix regression in v0.4.3 where changes are no longer detected because of a `null` query string.

### v0.4.3 - Released - 4 June 2020

#### New features

- [JENKINS-55037](https://issues.jenkins-ci.org/browse/JENKINS-55037)
  Add an extra query filter for discovering changes (e.g. `is:wip` or `file:componenta/src`)
- [JENKINS-62551](https://issues.jenkins-ci.org/browse/JENKINS-62551)
  Add plugin name and version to the HTTP user agent
- Bump gerrit-rest-java-client to 0.9.2

### v0.4.2 - Released - 2 June 2020

#### Fixes

- [JENKINS-62539](https://issues.jenkins-ci.org/browse/JENKINS-62539) Always use API when interacting with changes on Gerrit.

### v0.4.1 - Released - 5 January 2020

#### Fixes

- [JENKINS-60383](https://issues.jenkins-ci.org/browse/JENKINS-60383) Preserve additional behaviour of the SCM source.

### v0.4.0 - Released - 31 December 2019

#### Highlights

The very first integration of the [Gerrit Checks plugin](https://gerrit.googlesource.com/plugins/checks)
with Jenkins is now available, allowing a 1st-class integration of the build validation with the
Gerrit UI. Allows also the ability to have the build duration, live status update and quick links
in the Gerrit UI to view the logs and re-run the build.

The adoption of the Gerrit checks plugin allows to reduce significantly the pipeline branch scanning
by getting the exact list of changes that need verification, instead of fetching all the open changes
that may or may not need any verification at all. For large repositories with a lot of changes, that
means reducing the branch scanning from minutes to a few seconds.

Because of the huge contribution of the Gerrit checks integration, Thomas Dräbing is nominated
the 3rd maintainer of the Gerrit Code Review plugin for Jenkins.

Also, fixes the compatibilities issues with Gerrit v2.14 or earlier releases.

#### New features

- Add `gerritCheck` pipeline step to update check status in Gerrit UI
- Add support for selecting changes with pending checks
- Add REST API client for the checks plugin
- Add `GERRIT_CHANGE_URL` env variable pointing to the Gerrit change screen URL

#### Fixes

- [JENKINS-60364](https://issues.jenkins-ci.org/browse/JENKINS-60364) Downgrade Gerrit API to v0.8.15 for fixing compatibility
  with Gerrit v2.14 or earlier.

### v0.3.7 - Released - 3 December 2019

#### Highlights

Significant performance improvment on the branch indexing and checkout
of changes. On the validation of [Gerrit Code Review CI](https://gerrit-ci.gerritforge.com) the
branch indexing time was reduced from **40 minutes down to 2 minutes** (20x performance improvement).

Also, introduces the ability to reduce the number of refs being fetched by defining a custom
query condition.

#### New features

- Provide GERRIT_REFNAME environment variable
- Improve performance by fetching only the individual change ref instead of the full refs/changes/*
- Avoid fetching patch-sets multiple times when not strictly needed
- Allow configuration of an extra filter for open changes (default to -age:24w)

#### Fixes

- [JENKINS-59958](https://issues.jenkins-ci.org/browse/JENKINS-59958) Do not cache open changes
- [JENKINS-60259](https://issues.jenkins-ci.org/browse/JENKINS-60259) Restrict git fetch to open changes refsSpecs
- [JENKINS-60126](https://issues.jenkins-ci.org/browse/JENKINS-60126) Update gerrit-rest-java-client to 0.8.17

### v0.3.5 - Released - 24 October 2019

#### Fixes

-   [JENKINS-59745](https://issues.jenkins-ci.org/browse/JENKINS-59745) Fix exception navigating the SCMs of a workflowJob

### v0.3.4 - Released - 10 October 2019

#### New features

-   40a5144 Set automatically change details to build environment
    variables, align with the Gerrit Trigger Plugin variables names
-   JENKINS-54722 Generate the standard change hyperlink to the original
    Gerrit change that triggered a build

#### Fixes

-   e6c56b7 Running web hook as System ACL

### v0.3.3 - Released - 8 March 2019

#### Fixes

-   [JENKINS-5479](https://issues.jenkins-ci.org/browse/JENKINS-56479)
    Fix exception thrown when trying to parse unhandled events 
-   7d31723 Allow for remote URLs containing .git when matching webhook
    project events

-   4ee2b3f Log user account who posted the webhook

-   520c932 Fix processing WebHooks POST with unknown body length

### v0.3.2 - Released - 3 November 2018

Fixes

-   [JENKINS-54432](https://issues.jenkins-ci.org/browse/JENKINS-54432) Restore
    normal behaviour when using Gerrit as anonymous SCM source  

### v0.3.1 - Released - 29 October 2018

#### Highlights

Integration of the support for Gerrit in conjunction with the Gerrit
Trigger Plugin and single branch pipelines. Improvements on the support
for Gerrit API with the introduction of multiple labels in a single
review.

#### Features

-   Support for multiple labels in a single \`gerritReview\` step invocation
-   Support for integration with the Gerrit Trigger Plugin
-   Remove the support for deprecated \`GerritDSL\` style invocation
    (e.g. \`gerrit.review\` step)

#### Fixes

-   [JENKINS-54224](https://issues.jenkins-ci.org/browse/JENKINS-54224) restapi:
    cleanup restapi usage

### v0.2.1 - Released - 27 October 2018

#### Highlights

Bug-fix release from feedback and contributions of the adopters of the
v0.2 release.

#### Fixes

-   [3359ba3](https://github.com/jenkinsci/gerrit-code-review-plugin/commit/3359ba3) -
    Amendements to the documentation to explain the first-time setup

-   [e320d18](https://github.com/jenkinsci/gerrit-code-review-plugin/commit/e320d18) -
    Allow feedback to an open change without changing current score /
    label

-   [fc0bc91](https://github.com/jenkinsci/gerrit-code-review-plugin/commit/fc0bc91) -
    Restapi: allow the use of remote Gerrit server with non-root prefix

-   [JENKINS-54214](https://issues.jenkins-ci.org/browse/JENKINS-54214) - Avoid
    NPE when credentials are not provided

-   [JENKINS-54212](https://issues.jenkins-ci.org/browse/JENKINS-54212) - Fix
    detection of project name with refupdate

### v0.2 - Released - 23 October 2018

#### Highlights

Many bug fixes thanks to the initial adoption in real life use-cases.
Improvement of the compliance of the plugin with the rest of the
ecosystem of Jenkinsfile steps.

One major addition is the support for Jenkins Declarative Pipelines and
the deprecation of the old DSL-based integration, which was not
compatible with the way plugins should be written and integrated with
the rest of the CPS scripts and pipelines.

#### Features

-   [JENKINS-54070](https://issues.jenkins-ci.org/browse/JENKINS-54070) - Support
    for Jenkinsfile declarative pipelines
-   [JENKINS-50783](https://issues.jenkins-ci.org/browse/JENKINS-50783) -
    Support for file-based comments from Jenkinsfile steps

#### Fixes

-   [JENKINS-49695](https://issues.jenkins-ci.org/browse/JENKINS-49695) - Could
    not fetch branches from source while using Discover open changes
-   [JENKINS-50930](https://issues.jenkins-ci.org/browse/JENKINS-50930) - Multibranch
    fails if gerrit lives at a subdirectory
-   [JENKINS-49983](https://issues.jenkins-ci.org/browse/JENKINS-49983) - NullPointerException
    from gerrit plugin due to lack of configuration
-   [JENKINS-49985](https://issues.jenkins-ci.org/browse/JENKINS-49985) - hudson.remoting.ProxyException:
    org.codehaus.groovy.runtime.typehandling.GroovyCastException
-   [JENKINS-49713](https://issues.jenkins-ci.org/browse/JENKINS-49713) - gerrit-plugin
    exposes change-requests as normal branches instead of pull-requests
-   [JENKINS-49692](https://issues.jenkins-ci.org/browse/JENKINS-49692) - Could
    not update folder level actions from source

### v0.1.1 - Released - 4 April 2018

Initial version of the Gerrit Code Review plugin for Jenkins CI, with
support for the scripted pipeline with DSL.

# Future RoadMap

See below the roadmap I have envisaged for this plugin. However, I do
follow a fully Agile approach where it is really the user that drives
the development of the product, rather than a project plan.

The roadmap below can then change based on what are your needs and the
adoption by the community.


### v1.0 - Planned

##### Highlights

First fully functional release of the Gerrit Code Review integration
that can be functionally equivalent to the historical Gerrit Trigger
Plugin but with a focus on Jenkinsfile pipelines.

### v0.8 - Planned

##### Highlights

Support for internal networks where any calls outside the Jenkins node
can be made via Proxy servers.

### v0.7 - Planned

##### Highlights

Support for builds against Gerrit replicas, where API for review
feedback can be different from the one used for fetching the changes.

The challenge of this release is how to automatically discover the
upstream Gerrit node purely based on the fetch URL.

### v0.6 - Planned

##### Highlights

Introduction of the Git/SSH protocol support and use for review
feedback.

### v0.5 - Planned

##### Highlights

Support for BlueOcean, including change description, hyperlink and owner
visible from Jenkins UI.
