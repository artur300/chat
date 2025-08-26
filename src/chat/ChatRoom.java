package chat;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;


public class ChatRoom {

// These are settings and variables for the chat room system.
// TS formats timestamps as "HH:mm" (e.g., 14:05).
// Colors define how names/messages appear for senders, receivers, and system messages.
// Each chat room has a unique ID, a set of participants, and supervisors.
// SEQ is a counter that auto-increments to give each new room a unique ID (room 1, room 2, etc.).

    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm");
    private static final String SENDER_NAME_COLOR = ChatColors.PURPLE;
    private static final String SENDER_MSG_COLOR  = ChatColors.PURPLE;
    private static final String RECV_NAME_COLOR   = ChatColors.WHITE;
    private static final String RECV_MSG_COLOR    = ChatColors.CYAN;
    private static final String SYSTEM_COLOR      = ChatColors.CYAN;
    private final String id;
    private final Set<UserSession> participants = new CopyOnWriteArraySet<>();
    private final Set<UserSession> supervisors = new CopyOnWriteArraySet<>();
    private ChatRoom(String id) { this.id = id; }
    private static final AtomicInteger SEQ = new AtomicInteger(1);

// Creates a new chat room with a unique ID (room 1, room 2, …).
// Adds the two users (a and b) as participants in this room.
// Returns the newly created ChatRoom object.

    public static ChatRoom create(UserSession a, UserSession b) {
        String id = "room " + SEQ.getAndIncrement();
        ChatRoom r = new ChatRoom(id);
        r.participants.add(a);
        r.participants.add(b);
        return r;
    }

// Returns the unique ID of this chat room (e.g., "room 1").

    public String id() { return id; }

// Sends a system message (with timestamp and color) to all users in the room.

    public void system(String text) {
        String line = "[" + TS.format(new Date()) + "] "
                + SYSTEM_COLOR + "* " + text + ChatColors.RESET;
        sendToAll(line);
    }

// Sends a chat message to all room members.
// If the user is the sender, their message is shown in one color;
// for others, it is shown in a different color.
// Supervisors see all messages in blue.

    public void say(UserSession from, String msg) {
        String ts = "[" + TS.format(new Date()) + "] ";

        for (UserSession u : participants) {
            boolean isSenderView = (u == from);

            String namePart = (isSenderView ? SENDER_NAME_COLOR : RECV_NAME_COLOR)
                    + from.name() + ChatColors.RESET;

            String msgPart  = (isSenderView ? SENDER_MSG_COLOR  : RECV_MSG_COLOR)
                    + msg + ChatColors.RESET;

            u.out().println(ts + namePart + ": " + msgPart);
        }

        for (UserSession sup : supervisors) {
            String namePart = ChatColors.BLUE + from.name() + ChatColors.RESET;
            String msgPart  = ChatColors.BLUE  + msg        + ChatColors.RESET;
            sup.out().println(ts + namePart + ": " + msgPart);
        }
    }


// Adds a supervisor (like an admin) to the chat room.
// Supervisors can monitor the conversation without being regular participants.

    public void addSupervisor(UserSession sup) {
        supervisors.add(sup);
    }

// Removes a user from the room.
// The user is taken out of both participants and supervisors lists,
// ensuring they no longer belong to this chat room.

    public void remove(UserSession u) {
        participants.remove(u);
        supervisors.remove(u);
    }


// Creates a summary of all users in the room.
// Regular participants are listed by their name,
// while supervisors are marked with "SUP:".
// Returns the full list as a single comma-separated string.

    public String participantsSummary() {
        List<String> p = new ArrayList<>();
        for (UserSession u : participants) p.add(u.name());
        for (UserSession s : supervisors) p.add("SUP:" + s.name());
        return String.join(", ", p);
    }


// Sends a given message line to everyone in the chat room.
// It loops through all participants and supervisors
// and prints the message to each user's output stream.

    private void sendToAll(String line) {
        for (UserSession u : participants) u.out().println(line);
        for (UserSession s : supervisors) s.out().println(line);
    }

// Returns the number of participants currently in the chat room.
// (Does not include supervisors, only regular participants.)

    public int participantsCount() {
        return participants.size();
    }

// Returns a new list of all current participants in the chat room.
// This allows other parts of the program to safely access the participants
// without directly modifying the original set.

    public List<UserSession> participantsList() {
        return new ArrayList<>(participants);
    }

// Returns a new list of all supervisors in the chat room.
// This way, other parts of the program can see who the supervisors are
// without changing the original set directly.

    public List<UserSession> supervisorsList() {
        return new ArrayList<>(supervisors);
    }

}


