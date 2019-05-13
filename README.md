# Gerrit Code Review plugin

[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/gerrit-code-review-plugin/master)](https://ci.jenkins.io/job/Plugins/job/gerrit-code-review-plugin/job/master/)

Gerrit Code Review plugin for Jenkins

* see [Jenkins wiki](https://wiki.jenkins.io/display/JENKINS/Gerrit+Code+Review+Plugin) for detailed feature descriptions
* use [JIRA](https://issues.jenkins-ci.org/issues/?jql=component%20%3D%20gerrit-code-review-plugin%20and%20resolution%20is%20empty) to report issues / feature requests

## Master Branch

The master branch is the primary development branch for the Gerrit Code Review plugin.

## Contributing to the Plugin

Plugin source code is hosted on [GitHub](https://github.com/jenkinsci/gerrit-code-review-plugin).
New feature proposals and bug fix proposals should be submitted as
[Gerrit changes on GerritHub](/CONTRIBUTING.md#gerrithub-configuration) or as
[pull requests](https://help.github.com/articles/creating-a-pull-request) if you are not
familiar with Gerrit Code Review.

Before submitting your contribution, please assure that you've added
a test which verifies your change.

## Building the Plugin

```bash
  $ java -version # Need Java 1.8, earlier versions are unsupported for build
  $ mvn -version # Need a modern maven version; maven 3.2.5 and 3.5.0 are known to work
  $ mvn clean install
```

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

|Key                      |Description                                                     |
|-------------------------|----------------------------------------------------------------|
|GERRIT_API_URL           |Gerrit API URL, onlhy `http` and `https` protocols are supported|
|GERRIT_CHANGE_URL        |Gerrit change URL to parse GERRIT_API_URL out if missing        |
|GERRIT_API_INSECURE_HTTPS|If set to `true` certificate validation will be disabled        |
|GERRIT_CREDENTIALS_ID    |Jenkins credentials object id                                   |
|GERRIT_PROJECT           |Gerrit project name                                             |
|GERRIT_CHANGE_NUMBER     |Gerrit change number                                            |
|GERRIT_PATCHSET_NUMBER   |Gerrit revision                                                 |
|BRANCH_NAME              |Gerrit reference name nn/nnnn/n                                 |

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
        success { gerritReview labels: [Verified: 1] }
        unstable { gerritReview labels: [Verified: 0], message: 'Build is unstable' }
        failure { gerritReview labels: [Verified: -1] }
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
  } catch (e) {
    gerritReview labels: [Verified: -1]
    throw e
  }
}
```
