package katamari.ex;

public class CommandNotFoundException extends Exception {
    public CommandNotFoundException(String message) {
        super(message);
    }

    public CommandNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

