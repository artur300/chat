package chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

import static chat.ChatUtils.sys;
import static chat.ChatUtils.broadcastSys;
import static chat.ChatUtils.log;

public class ChatServer {


// This section defines the main system settings and storage:
// - PORT = the port number clients connect to (7000).
// - ALLOWED = list of usernames that are allowed to log in.
// - sessionsByName = keeps track of active users by their name.
// - allSessions = holds all current user sessions for broadcasts.
// - rooms = stores all active chat rooms.
// - pendingByTarget = keeps queues of people waiting to chat with a user.

    private static final int PORT = 7000;
    private static final List<String> ALLOWED = Arrays.asList("BOB","JACK","ALICE","EVA","MIKE","ADMIN");
    private static final ConcurrentMap<String, UserSession> sessionsByName = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<UserSession> allSessions     = new CopyOnWriteArrayList<>();
    private static final ConcurrentMap<String, ChatRoom> rooms             = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Queue<String>> pendingByTarget = new ConcurrentHashMap<>();





// This is the main entry point of the server:
// - Opens a server socket on the given PORT.
// - Prints a message that the server is running.
// - Waits for clients to connect (server.accept()).
// - For every new client, starts a new thread to handle them.
// This allows multiple users to connect at the same time.

    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(PORT)) {
            log("* Server up on " + PORT + ". Waiting for clients...");
            while (true) {
                Socket s = server.accept();
                new Thread(() -> handleClient(s)).start();
            }
        }
    }



// This method handles one connected client:
// - Creates a new UserSession for the client and adds it to the active sessions list.
// - Asks the client to choose a username (negotiateName).
// - Announces to others that the user joined and shows them the current online list.
// - Then enters a loop to read messages from the client.
//   * If the user types "/quit" or "goodbye", they disconnect.
//   * If the message starts with "/", it is treated as a command.
//   * Otherwise, it's sent to the active chat room, if the user is in one.
// - When the client leaves or an error happens, it cleans up and closes the connection.

    private static void handleClient(Socket socket) {
        UserSession us = null;
        try {
            us = new UserSession(new SocketData(socket));
            allSessions.add(us);
            log("* Connection from " + us.addr());

            negotiateName(us);
            broadcastSys(allSessions, us.name()+" joined. Type /menu for commands.");
            sendPresenceListTo(us);

            String line;
            while ((line = us.in().readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equalsIgnoreCase("goodbye") || line.equalsIgnoreCase("/quit")) {
                    us.out().println(sys("Goodbye!"));
                    break;
                }

                if (line.startsWith("/")) {
                    handleCommand(us, line);
                } else {
                    // הודעה לצ'אט פעיל
                    if (us.activeRoomId() == null) {
                        us.out().println(sys("No active chat. Use /chat <USER> first."));
                    } else {
                        ChatRoom room = rooms.get(us.activeRoomId());
                        if (room != null) {
                            room.say(us, line);
                        } else {
                            us.setActiveRoomId(null);
                            us.out().println(sys("Chat ended. Start a new one with /chat <USER>."));
                        }
                    }
                }
            }

        } catch (Exception ignored) {
        } finally {
            if (us != null) cleanup(us);
            try { socket.close(); } catch (Exception ignored) {}
        }
    }



// This method processes commands from a user:
// - Removes the leading "/" from the command (e.g., "/chat" → "chat").
// - Splits the command into the main keyword (like "chat") and an optional argument (like a username).
// - Uses a switch statement to handle different commands:
//   * /menu → shows all available commands.
//   * /list → shows who is online.
//   * /whoami → shows the user’s name, status, and current chat.
//   * /busy /free → changes the user’s availability.
//   * /chat <USER> → starts a chat with another user.
//   * /leave → leaves the current chat room.
//   * /rooms → lists all active chat rooms.
//   * /join <ROOM> → lets a supervisor join an existing room.
//   * /quit → disconnects from the server.
// - If the command is not recognized, it shows an "Unknown command" message.

    private static void handleCommand(UserSession us, String cmdLine) {
        if (cmdLine.startsWith("/")) cmdLine = cmdLine.substring(1);

        String[] parts = cmdLine.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();

        String arg = "";
        if (parts.length > 1) arg = parts[1].trim();

        switch (cmd) {
            case "menu": {
                us.out().println(sys(
                        ChatColors.YELLOW+"Available commands:\n" +
                                "  /menu            - Show this menu message\n" +
                                "  /list            - Show who is online\n" +
                                "  /whoami          - Show your name and status\n" +
                                "  /busy            - Mark yourself as busy\n" +
                                "  /free            - Mark yourself as free\n" +
                                "  /chat <USER>     - Start chat with a user\n" +
                                "  /leave           - Leave the current chat\n" +
                                "  /rooms           - List all active chat rooms\n" +
                                "  /join <ROOM>     - Join a room as supervisor\n" +
                                "  /quit            - Disconnect from server"+ChatColors.RESET
                ));
                break;
            }
            case "list": {
                sendPresenceListTo(us);
                break;
            }
            case "whoami": {
                String status = us.isBusy() ? "BUSY" : "FREE";
                String inChat = (us.activeRoomId() != null) ? " | in chat " + us.activeRoomId() : "";
                us.out().println(sys("You are " + us.name() + " | status: " + status + inChat));
                break;
            }
            case "busy": {
                setBusy(us, true);
                break;
            }
            case "free": {
                setBusy(us, false);
                break;
            }
            case "chat": {
                if (arg.isEmpty()) { us.out().println(sys("Usage: /chat <USER>")); break; }
                startChat(us, arg.toUpperCase());
                break;
            }
            case "leave": {
                leaveChat(us);
                break;
            }
            case "rooms": {
                listRooms(us);
                break;
            }
            case "join": {
                if (arg.isEmpty()) { us.out().println(sys("Usage: /join <CHAT_ID>")); break; }
                String roomKey = arg.trim().toLowerCase().replaceAll("\\s+", " ");
                joinAsSupervisor(us, roomKey);
                break;
            }
            case "quit": {
                us.out().println(sys("Goodbye!"));
                break;
            }
            default: {
                us.out().println(sys("Unknown command. Type /menu to see available commands."));
            }
        }
    }




// This method makes sure each user connects with a valid and unique username:
// 1. Asks the client to enter a username (must be from the ALLOWED list).
// 2. Reads the input, trims spaces, and converts it to uppercase.
// 3. If the username is not in the ALLOWED list → reject and ask again.
// 4. If the username is already taken (someone else logged in with it) → reject and ask again.
// 5. Once valid, assign the name to the user session and store it in the active sessions map.
// 6. Send a welcome message to the user.
// 7. Call notifyPending(name) to check if anyone was waiting to chat with this user.

    private static void negotiateName(UserSession us) throws IOException {
        while (true) {
            us.out().println(sys("Enter username (allowed: " + ALLOWED + "):"));
            String name = us.in().readLine();
            if (name == null) throw new IOException("Client closed");
            name = name.trim().toUpperCase();

            if (!ALLOWED.contains(name)) {
                us.out().println(sys(ChatColors.RED+"✖ Not allowed. Choose from: " + ALLOWED+ChatColors.RESET));
                continue;
            }
            if (sessionsByName.containsKey(name)) {
                us.out().println(sys(ChatColors.RED+"✖ Already logged in elsewhere."+ChatColors.RESET));
                continue;
            }

            us.setName(name);
            sessionsByName.put(name, us);
            us.out().println(sys("Welcome, " + name + "!"));

            notifyPending(name);
            break;
        }
    }



// This method shows the user who is currently online:
// 1. Go through all active sessions in the server.
// 2. For each user, check if they have a name assigned (valid login).
// 3. Add their name plus their status → (BUSY) or (FREE) into a list.
// 4. Send this list back to the requesting user in a formatted message.

    private static void sendPresenceListTo(UserSession us) {
        List<String> names = new ArrayList<>();
        for (UserSession s : allSessions) {
            if (s.name() != null) names.add(s.name() + (s.isBusy()?"(BUSY)":"(FREE)"));
        }
        us.out().println(sys("Online: " + names));
    }



// This method updates a user's busy/free status:
// 1. Change the user's status to BUSY or FREE.
// 2. Notify the user of their new status.
// 3. If the user became FREE, check if anyone was waiting to chat with them (notifyPending).
// 4. Update and broadcast the full presence list so everyone sees the new status.

    private static void setBusy(UserSession us, boolean busy) {
        us.setBusy(busy);
        us.out().println(sys("Status set to " + (busy?"BUSY":"FREE")));
        if (!busy) notifyPending(us.name());
        broadcastPresence();
    }





// This method starts a private chat between two users:
// 1. Prevents a user from opening multiple chats or chatting with themselves.
// 2. Validates that the target user exists in the allowed list.
// 3. If the target is offline or busy, the caller is added to the target's waiting queue.
// 4. If the target is free, a new chat room is created for both users.
// 5. Both users are marked as BUSY and linked to the new chat room.
// 6. A system message announces the chat, and the presence list is updated for everyone.

    private static void startChat(UserSession caller, String targetName) {
        if (caller.activeRoomId() != null) {
            caller.out().println(sys(ChatColors.RED+"✖ You are already in " + caller.activeRoomId() + ". Use /leave first."+ChatColors.RESET));
            return;
        }
        if (caller.name().equals(targetName)) {
            caller.out().println(sys(ChatColors.RED+"✖ You cannot chat with yourself."+ChatColors.RESET));
            return;
        }
        if (!ALLOWED.contains(targetName)) {
            caller.out().println(sys(ChatColors.RED+"✖ No such user: " + targetName + ChatColors.RESET));
            return;
        }

        UserSession target = sessionsByName.get(targetName);
        if (target == null) {
            caller.out().println(sys(ChatColors.RED+"✖ " + targetName + " is offline. Added to their pending queue."+ChatColors.RESET));
            pendingByTarget.computeIfAbsent(targetName, k -> new ConcurrentLinkedQueue<>()).offer(caller.name());
            return;
        }
        if (target.isBusy() || target.activeRoomId()!=null) {
            caller.out().println(sys(targetName + " is busy. Added to their pending queue."));
            pendingByTarget.computeIfAbsent(targetName, k -> new ConcurrentLinkedQueue<>()).offer(caller.name());
            return;
        }

        ChatRoom room = ChatRoom.create(caller, target);
        rooms.put(room.id(), room);
        caller.setActiveRoomId(room.id());
        target.setActiveRoomId(room.id());
        caller.setBusy(true); target.setBusy(true);

        room.system("Chat " + room.id() + " opened between " + caller.name() + " and " + target.name());
        broadcastPresence();
    }



// This method lets a user leave their active chat room:
// 1. If the user is not in any chat, it tells them "No active chat."
// 2. Otherwise, it announces to the room that the user left and removes them.
// 3. If fewer than 2 participants remain, the whole room is closed:
//    - All remaining users and supervisors are released (set FREE).
//    - They are notified the chat closed, and pending requests are checked.
//    - The room is removed from the active list.
// 4. If enough people remain, only the leaving user is released from the chat.
// 5. The presence list is updated for everyone after changes.

    private static void leaveChat(UserSession us) {
        String rid = us.activeRoomId();
        if (rid == null) { us.out().println(sys("No active chat.")); return; }

        ChatRoom room = rooms.get(rid);
        if (room == null) {
            us.setActiveRoomId(null);
            us.setBusy(false);
            us.out().println(sys("Chat ended."));
            broadcastPresence();
            return;
        }

        room.system(us.name() + " left the chat.");
        room.remove(us);

        if (room.participantsCount() < 2) {
            for (UserSession other : room.participantsList()) {
                other.setActiveRoomId(null);
                other.setBusy(false);
                other.out().println(sys("Chat " + rid + " closed."));
                notifyPending(other.name());
            }
            for (UserSession sup : room.supervisorsList()) {
                sup.setActiveRoomId(null);
                sup.setBusy(false);
                sup.out().println(sys("Chat " + rid + " closed."));
            }

            us.setActiveRoomId(null);
            us.setBusy(false);
            us.out().println(sys("Chat " + rid + " closed."));
            notifyPending(us.name());

            rooms.remove(rid);
            broadcastPresence();
            return;
        }

        us.setActiveRoomId(null);
        us.setBusy(false);
        us.out().println(sys("Left chat " + rid + "."));
        broadcastPresence();
        notifyPending(us.name());
    }



// This method shows the user all currently active chat rooms:
// 1. If there are no rooms, it tells the user "No active rooms."
// 2. Otherwise, it builds a list of each room with its ID and participants.
// 3. Finally, it sends this formatted list back to the user.

    private static void listRooms(UserSession us) {
        if (rooms.isEmpty()) { us.out().println(sys("No active rooms.")); return; }
        StringBuilder sb = new StringBuilder("Active rooms:\n");
        for (ChatRoom r : rooms.values()) {
            sb.append("- ").append(r.id()).append(" : ").append(r.participantsSummary()).append("\n");
        }
        us.out().println(sys(sb.toString().trim()));
    }



// This method lets the ADMIN user join a room as a supervisor:
// 1. Only a user named "ADMIN" is allowed to join rooms this way.
// 2. If the admin is already in a room, they must leave it first.
// 3. It checks if the requested room exists; if not, an error is shown.
// 4. If valid, the admin is added as a supervisor, marked busy, and the room is notified.
// 5. Finally, the system updates everyone's view of who is online and in which room.

    private static void joinAsSupervisor(UserSession sup, String roomId) {
        if (!"ADMIN".equals(sup.name())) {
            sup.out().println(sys(ChatColors.RED+"✖ Only ADMIN can join rooms."+ChatColors.RESET));
            return;
        }
        if (sup.activeRoomId() != null) {
            sup.out().println(sys(ChatColors.RED+"✖ You are already in " + sup.activeRoomId() + ". Use /leave first."+ChatColors.RESET));
            return;
        }
        ChatRoom r = rooms.get(roomId);
        if (r == null) { sup.out().println(sys(ChatColors.RED+"✖ No such room."+ChatColors.RESET)); return; }

        r.addSupervisor(sup);
        sup.setActiveRoomId(r.id());
        sup.setBusy(true);
        r.system("Supervisor " + sup.name() + " joined " + r.id());
        broadcastPresence();
    }



// This method connects waiting users when someone becomes free:
// 1. It gets the queue of people waiting to chat with the freed user.
// 2. If the queue is empty, it does nothing.
// 3. It checks the first requester and the freed user are still connected.
// 4. If the freed user is still busy, the requester goes back into the queue.
// 5. If free, the requester is notified, and a new chat is started automatically.
// 6. Only one chat is started per call, then it stops.

    private static void notifyPending(String freedUser) {
        Queue<String> q = pendingByTarget.get(freedUser);
        if (q == null) return;

        String requester;
        while ((requester = q.poll()) != null) {
            UserSession req = sessionsByName.get(requester);
            UserSession tgt = sessionsByName.get(freedUser);
            if (req == null || tgt == null) continue;
            if (tgt.isBusy() || tgt.activeRoomId()!=null) {
                q.offer(requester);
                break;
            }
            req.out().println(sys(freedUser + " is now free. Opening chat..."));
            startChat(req, freedUser);
            break;
        }
    }


// This method updates all connected users with the current online list:
// 1. It builds a presence message using presenceMessage().
// 2. Then it loops through all active sessions.
// 3. For each session that has a username, it sends the message.
// 4. This keeps everyone updated about who is online and their status.

    private static void broadcastPresence() {
        String msg = presenceMessage();
        for (UserSession s : allSessions) {
            if (s.name()!=null) s.out().println(msg);
        }
    }


// This method creates a text summary of all online users:
// 1. Loops through every active session.
// 2. For each user, it adds their name, status (BUSY/FREE), and room info if any.
// 3. Collects all into a list.
// 4. Returns a formatted system message like: "Online: [BOB(BUSY)[in room 1], JACK(FREE)]".

    private static String presenceMessage() {
        List<String> lst = new ArrayList<>();
        for (UserSession s : allSessions) {
            if (s.name()!=null) {
                String status = s.isBusy() ? "(BUSY)" : "(FREE)";
                String roomInfo = (s.activeRoomId() != null) ? "[in " + s.activeRoomId() + "]" : "";
                lst.add(s.name() + status + roomInfo);
            }
        }
        return sys("Online: " + lst);
    }



// This method cleans up when a user disconnects:
// 1. Removes the user from active sessions and maps.
// 2. If they were in a chat room, notifies others and removes them from that room.
// 3. If the room has fewer than 2 participants left, closes it and resets everyone inside.
// 4. Resets the user’s own status (no room, FREE).
// 5. Broadcasts a system message that the user left and updates presence for everyone.

    private static void cleanup(UserSession us) {
        try {
            String name = us.name();
            if (name != null) sessionsByName.remove(name);
            allSessions.remove(us);

            if (us.activeRoomId() != null) {
                ChatRoom r = rooms.get(us.activeRoomId());
                if (r != null) {
                    r.system((name != null ? name : us.addr()) + " disconnected.");
                    r.remove(us);

                    if (r.participantsCount() < 2) {
                        for (UserSession other : r.participantsList()) {
                            other.setActiveRoomId(null);
                            other.setBusy(false);
                            other.out().println(sys("Chat " + r.id() + " closed."));
                            notifyPending(other.name());
                        }
                        for (UserSession sup : r.supervisorsList()) {
                            sup.setActiveRoomId(null);
                            sup.setBusy(false);
                            sup.out().println(sys("Chat " + r.id() + " closed."));
                        }
                        rooms.remove(r.id());
                    }
                }
            }

            us.setActiveRoomId(null);
            us.setBusy(false);

            broadcastSys(allSessions, (name != null ? name : us.addr()) + " left.");
            broadcastPresence();
        } catch (Exception ignored) {}
    }
}
