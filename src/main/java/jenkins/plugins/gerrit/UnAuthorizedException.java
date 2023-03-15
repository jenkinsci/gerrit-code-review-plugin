package jenkins.plugins.gerrit;

public class UnAuthorizedException extends RuntimeException{
    public UnAuthorizedException(String s) {
        super(s);
    }

}
