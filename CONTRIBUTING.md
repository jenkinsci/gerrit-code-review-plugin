Contributing to the Gerrit Code Review Plugin
==============================

Plugin source code is hosted on [GitHub](https://github.com/jenkinsci/gerrit-code-review-plugin).
New feature proposals and bug fix proposals should be submitted as
[GitHub pull requests](https://help.github.com/articles/creating-a-pull-request)
or can be submitted directly if you have commit permission to the
git-plugin repository.

Patch submission is done through [GerritHub](https://review.gerrithub.io)  where patches are
voted on by everyone in the community.  A patch usually requires a minimum of one +2 votes
before it will be merged. Your Gerrit patch-sets will be fetched and validated by the
[Gerrit CI](https://gerrit-ci.gerritforge.com) using the Gerrit Code Review plugin for Jenkins,
which is exactly *this project* you are contributing :-).

If you are not familiar with [Gerrit Code Review](https://gerritcodereview.com) and you are
willing to contribute anyway, we do accept pull requests. Fork the repository on GitHub, prepare
your change on your forked copy, and submit a pull request.  Your pull
request will be evaluated by the
[Cloudbees Jenkins job](https://ci.jenkins.io/job/Plugins/job/gerrit-code-review-plugin/).

*NOTE: Whether you chose GitHub or Gerrit Code Review, the discussion and approval will
take place uniquely on GerritHub.*

Before submitting your change, please assure that you've added tests
which verify your change.  There have been many developers involved in
this plugin and there are many, many users who depend on it.
Tests help us assure that we're delivering a reliable plugin,
and that we've communicated our intent to other developers in a way
that they can detect when they run tests.

Code coverage reporting is available as a maven target.  Please try
your best to improve code coverage with tests when you submit.

Before submitting your change, please review the findbugs output to
assure that you haven't introduced new findbugs warnings.
- `mvn findbugs:check` to analyze project using [Findbugs](http://findbugs.sourceforge.net/)
- `mvn findbugs:gui` to check Findbugs report using GUI

## GerritHub Configuration

Log into GerritHub using your GitHub account credentials. Once logged in, in the top
right corner click your user name and select ‘Settings’. You should set up the following:

- Profile: Verify that the information is correct here.
- HTTP Password: Generate a password. You’ll use this password when prompted by git (not your GitHub password!).
- In the My Menu section, add the following entry:

  - Name: Gerrit Plugin Reviews
  - URL: /q/project:jenkinsci%252Fgerrit-code-review-plugin

Now that you’re configured, you can clone the GerritHub repository locally:

```bash
$ git clone https://review.gerrithub.io/jenkinsci/gerrit-code-review
```

Or if you already cloned directly from GitHub, you can change your repository to point at GerritHub by doing:

```bash
$ git remote set-url origin https://review.gerrithub.io/a/jenkinsci/gerrit-code-review
```

When you later push a patch, you’ll be prompted for a password. That password is the one generated in the HTTP
Password section of the GerritHub settings, not your GitHub password. To make it easy, turn on the git credential
helper to store your password for you. You can enable it for the jenkinsci/gerrit-code-review repository with:

```bash
$ git config credential.helper store
```

Finally, you’ll need to install the Gerrit commit-msg hook. This inserts a unique change ID each time you commit
and is required for Gerrit to work.

```bash
$ curl -Lo .git/hooks/commit-msg https://review.gerrithub.io/tools/hooks/commit-msg
$ chmod +x .git/hooks/commit-msg
```

Now open .git/config in a text editor and add these lines: (this will make pushing reviews easier)

```bash
[remote "review"]
  url = https://review.gerrithub.io/spdk/spdk
  push = HEAD:refs/for/master
```

You may also enable the git pre-commit and pre-push hooks to automatically check formatting and run the unit tests:

```bash
$ cp .githooks/* .git/hooks/
```

Now you should be all set!

## Formatting

Code formatting in this plugin follows the Google Java Format guidelines.
Please install the [google-java-format](https://github.com/google/google-java-format) tool
and make sure that your changes keep the overall code formatting style.

## Code Reviews

Every pull request needs to satisfy the following conditions before being merged:

1. Reviewed and approved by at least one contributor
2. Verified by the [Cloudbees Jenkins job](https://ci.jenkins.io/job/Plugins/job/gerrit-code-review-plugin/)

As guideline for the common etiquette and guideline for submitting and reviewing pull-requests,
please refer to [Effective Code Reviews](https://nyu-cds.github.io/effective-code-reviews/) paper by the [NYU](https://www.nyu.edu/).
