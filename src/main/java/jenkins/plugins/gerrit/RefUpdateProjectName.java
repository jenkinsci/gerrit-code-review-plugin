package jenkins.plugins.gerrit;

public class RefUpdateProjectName {
  String project;

  public RefUpdateProjectName(String project) {
    this.project = project;
  }

  @Override
  public String toString() {
    return project;
  }
}
