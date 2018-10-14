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
[pull requests](https://help.github.com/articles/creating-a-pull-request).
Fork the repository, prepare your change on your forked
copy, and submit a pull request.

Before submitting your pull request, please assure that you've added
a test which verifies your change.

## Building the Plugin

```bash
  $ java -version # Need Java 1.8, earlier versions are unsupported for build
  $ mvn -version # Need a modern maven version; maven 3.2.5 and 3.5.0 are known to work
  $ mvn clean install
```

## Jenkinsfile Steps

Gerrit Code Review plugin provides one extra step for allowing to post the
review feedback back to the originating change.

### ```gerritReview``` step

Parameters:

- ```label```
  The review label to add to the Gerrit change.
  Default: 'Verified'.

- ```score```
  The score value (positive, negative or zero) associated to the review.
  Default: 0.

- ```message```
  Additional message provided as explanation of the the review feedback.
  Default: ''

### Declarative pipeline example

```groovy

pipeline {
    agent any
    stages {
        stage('Example') {
            steps {
                echo 'Hello World'
            }
        }
    }
    post {
        success { gerritReview score:1 }
        unstable { gerritReview message:'Build is unstable' }
        failure { gerritReview score:-1 }
    }
}
```

### Scripted pipeline example

```groovy
node {
  checkout scm
  try {
    stage('Hello') {
      echo 'Hello World'
    }
    gerritReview score:1
  } catch (e) {
    gerritReview score:-1
    throw e
  }
}
```
