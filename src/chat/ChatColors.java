package chat;

// This class defines ANSI color codes for console output.
// Each constant represents a different text color (red, green, yellow, etc.).
// RESET is used to switch back to the default color after printing.
// These codes allow chat messages to appear in different colors in the terminal.
// Example: ChatColors.RED + "Error!" + ChatColors.RESET

public class ChatColors {
    public static final String RESET  = "\u001B[0m";  // לאפס צבע
    public static final String RED    = "\u001B[31m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE   = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN   = "\u001B[36m";
    public static final String WHITE  = "\u001B[37m";
}