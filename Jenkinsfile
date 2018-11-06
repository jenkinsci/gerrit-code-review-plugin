#!groovy

def jenkinsVersions = [null, '2.60.1']
def jenkinsPlatforms = ['linux']

// Don't test plugin compatibility - exceeds 1 hour timeout
// Allow failing tests to retry execution
// buildPlugin(failFast: false)

// Test plugin compatbility to latest Jenkins LTS
// Allow failing tests to retry execution
if (env.GERRIT_API_URL == null) {
    buildPlugin(jenkinsVersions: jenkinsVersions, failFast: false, platforms: jenkinsPlatforms)
} else {
  try {
    gerritReview labels: [:], message: "Build started ${env.BUILD_URL}"
    buildPlugin(jenkinsVersions: jenkinsVersions, failFast: false, platforms: jenkinsPlatforms)
    if (currentBuild.result == 'UNSTABLE') {
      gerritReview labels: [Verified: 0], message: "Build is unstable, there are failed tests ${env.BUILD_URL}"
    } else {
      gerritReview labels: [Verified: +1], message: "Build succeeded ${env.BUILD_URL}"
    }
  } catch (e) {
    gerritReview labels: [Verified: -1], message: "Build failed ${env.BUILD_URL}"
    throw e
  }
}
