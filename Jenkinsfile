#!groovy

// Don't test plugin compatibility - exceeds 1 hour timeout
// Allow failing tests to retry execution
// buildPlugin(failFast: false)

// Test plugin compatbility to latest Jenkins LTS
// Allow failing tests to retry execution
def review = { labels, message ->
  try {
    gerritReview labels: labels, message: message
  } catch (NoSuchMethodError e) {
    println "env.GERRIT_API_URL = '${env.GERRIT_API_URL}'"
    if (env.GERRIT_API_URL != null) throw e;
    // Ignore when running outside the gerrit-code-review plugin
  }
}

try {
  review labels: [:], message: "Build started ${env.BUILD_URL}"
  buildPlugin(jenkinsVersions: [null, '2.60.1'], failFast: false, platforms: ['linux'])
  if (currentBuild.result == 'UNSTABLE') {
    review labels: ['Verified': 0], message: "Build is unstable, there are failed tests ${env.BUILD_URL}"
  } else {
    review labels: ['Verified': +1], message: "Build succeeded ${env.BUILD_URL}"
  }
} catch (e) {
  review labels: ['Verified': -1], message: "Build failed ${env.BUILD_URL}"
  throw e
}


