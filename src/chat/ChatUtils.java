package chat;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

// Utility class with helper methods for the chat system.
// - sys(text): Formats a system message with a timestamp.
// - broadcastSys(): Sends a system message to all given users.
// - log(): Prints a log message with the current date and time.
// Marked as 'final' with a private constructor so it cannot be instantiated.

public final class ChatUtils {
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm");

    private ChatUtils() {}

    public static String sys(String text) {
        return "[" + TS.format(new Date()) + "] * " + text;
    }

    public static void broadcastSys(Collection<UserSession> recipients, String text) {
        String line = sys(text);
        for (UserSession s : recipients) {
            if (s != null && s.out() != null) {
                s.out().println(line);
            }
        }
    }

    public static void log(String msg) {
        System.out.println(new Date() + " " + msg);
    }
}

