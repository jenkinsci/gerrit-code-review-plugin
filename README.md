# Gerrit Code Review plugin

Gerrit Code Review plugin for Jenkins

* see [Jenkins wiki](https://wiki.jenkins.io/display/JENKINS/Gerrit+Code+Review+Plugin) for detailed feature descriptions
* use [JIRA](https://issues.jenkins-ci.org) to report issues / feature requests

## Master Branch

The master branch is the primary development branch for the Gerrit Code Review plugin.

## Contributing to the Plugin

Plugin source code is hosted on [GitHub](https://github.com/jenkinsci/gerrit-plugin).
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

