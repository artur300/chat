package chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {

    // --- תצורה ---
    private static final int PORT = 7000;
    private static final List<String> ALLOWED = Arrays.asList("BOB","JACK","ALICE","EVA","MIKE","SHIFT_LEAD"); // לדוגמה
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm");

    // --- מצב מערכת ---
    // שם -> סשן (מונע התחברות כפולה)
    private static final ConcurrentMap<String, UserSession> sessionsByName = new ConcurrentHashMap<>();
    // כל הסשנים (לשידורי מערכת, /list)
    private static final CopyOnWriteArrayList<UserSession> allSessions = new CopyOnWriteArrayList<>();
    // צ'אט פעיל: chatId -> ChatRoom
    private static final ConcurrentMap<String, ChatRoom> rooms = new ConcurrentHashMap<>();
    // תורים: יעד -> רשימת מבקשים
    private static final ConcurrentMap<String, Queue<String>> pendingByTarget = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(PORT)) {
            log("* Server up on " + PORT + ". Waiting for clients...");
            while (true) {
                Socket s = server.accept();
                new Thread(() -> handleClient(s)).start();
            }
        }
    }

    private static void handleClient(Socket socket) {
        UserSession us = null;
        try {
            us = new UserSession(new SocketData(socket));
            allSessions.add(us);
            log("* Connection from " + us.addr());

            // משא ומתן על שם משתמש
            negotiateName(us);

            // שידור מערכת
            broadcastSys(us.name()+" joined. Type /help for commands.");
            sendPresenceListTo(us);

            // לולאת הודעות
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

        } catch (Exception e) {
            // אפשר להדפיס ללוג אם רוצים
        } finally {
            if (us != null) {
                cleanup(us);
            }
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    // ---- פקודות ----
    private static void handleCommand(UserSession us, String cmdLine) {
        // cmdLine מגיע בצורת "/help" או "/chat ALICE" וכו'

        // מסירים הסלאש הראשון כדי שה-switch יעבוד על שמות בלי "/"
        if (cmdLine.startsWith("/")) {
            cmdLine = cmdLine.substring(1);
        }

        // פירוק לפקודה + ארגומנט (אם יש)
        String[] parts = cmdLine.split("\\s+", 2);

        String cmd = parts[0].toLowerCase();

        String arg = "";
        if (parts.length > 1) {
            arg = parts[1].trim();
        }

        switch (cmd) {
            case "help": {
                us.out().println(sys(
                        "Available commands:\n" +
                                "  /help            - Show this help message\n" +
                                "  /list            - Show who is online\n" +
                                "  /whoami          - Show your name and status\n" +
                                "  /busy            - Mark yourself as busy\n" +
                                "  /free            - Mark yourself as free\n" +
                                "  /chat <USER>     - Start chat with a user\n" +
                                "  /leave           - Leave the current chat\n" +
                                "  /rooms           - List all active chat rooms\n" +
                                "  /join <CHAT_ID>  - Join a room as supervisor\n" +
                                "  /quit            - Disconnect from server"
                ));
                break;
            }

            case "list": {
                sendPresenceListTo(us);
                break;
            }

            case "whoami": {
                String status;
                if (us.isBusy()) {
                    status = "BUSY";
                } else {
                    status = "FREE";
                }

                String inChat = "";
                if (us.activeRoomId() != null) {
                    inChat = " | in chat " + us.activeRoomId();
                }

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
                if (arg.isEmpty()) {
                    us.out().println(sys("Usage: /chat <USER>"));
                    break;
                }
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
                if (arg.isEmpty()) {
                    us.out().println(sys("Usage: /join <CHAT_ID>"));
                    break;
                }
                joinAsSupervisor(us, arg);
                break;
            }

            case "quit": {
                us.out().println(sys("Goodbye!"));
                break;
            }

            default: {
                us.out().println(sys("Unknown command. Type /help to see available commands."));
                break;
            }
        }
    }


    // ---- לוגיקה ----
    private static void negotiateName(UserSession us) throws IOException {
        while (true) {
            us.out().println(sys("Enter username (allowed: " + ALLOWED + "):"));
            String name = us.in().readLine();
            if (name == null) throw new IOException("Client closed");
            name = name.trim().toUpperCase();

            if (!ALLOWED.contains(name)) {
                us.out().println(sys("✖ Not allowed. Choose from: " + ALLOWED));
                continue;
            }
            if (sessionsByName.containsKey(name)) {
                us.out().println(sys("✖ Already logged in elsewhere."));
                continue;
            }
            us.setName(name);
            sessionsByName.put(name, us);
            us.out().println(sys("Welcome, " + name + "!"));
            break;
        }
    }

    private static void sendPresenceListTo(UserSession us) {
        List<String> names = new ArrayList<>();
        for (UserSession s : allSessions) {
            if (s.name() != null) {
                names.add(s.name() + (s.isBusy()?"(BUSY)":"(FREE)"));
            }
        }
        us.out().println(sys("Online: " + names));
    }

    private static void setBusy(UserSession us, boolean busy) {
        us.setBusy(busy);
        us.out().println(sys("Status set to " + (busy?"BUSY":"FREE")));
        // אם עבר ל-FREE – לבדוק אם יש תור שמחכה לו
        if (!busy) notifyPending(us.name());
        broadcastPresence();
    }

    private static void startChat(UserSession caller, String targetName) {
        if (caller.name().equals(targetName)) {
            caller.out().println(sys("✖ You cannot chat with yourself."));
            return;
        }
        UserSession target = sessionsByName.get(targetName);
        if (target == null) {
            caller.out().println(sys("✖ " + targetName + " is offline. Added to their pending queue."));
            pendingByTarget.computeIfAbsent(targetName, k -> new ConcurrentLinkedQueue<>()).offer(caller.name());
            return;
        }
        if (target.isBusy() || target.activeRoomId()!=null) {
            caller.out().println(sys(targetName + " is busy. Added to their pending queue."));
            pendingByTarget.computeIfAbsent(targetName, k -> new ConcurrentLinkedQueue<>()).offer(caller.name());
            return;
        }

        // פותחים צ'אט
        ChatRoom room = ChatRoom.create(caller, target);
        rooms.put(room.id(), room);
        caller.setActiveRoomId(room.id());
        target.setActiveRoomId(room.id());
        caller.setBusy(true); target.setBusy(true);

        room.system("Chat " + room.id() + " opened between " + caller.name() + " and " + target.name());
        broadcastPresence();
    }

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

        // אם אחרי העזיבה החדר ריק -> סוגרים
        if (room.isEmpty()) {
            rooms.remove(rid);
        }
        us.setActiveRoomId(null);
        us.setBusy(false);
        us.out().println(sys("Left chat " + rid + "."));
        broadcastPresence();

        // כשהמשתמש התפנה – ליידע מבקשים ממתינים
        notifyPending(us.name());
    }

    private static void listRooms(UserSession us) {
        if (rooms.isEmpty()) { us.out().println(sys("No active rooms.")); return; }
        StringBuilder sb = new StringBuilder("Active rooms:\n");
        for (ChatRoom r : rooms.values()) {
            sb.append("- ").append(r.id()).append(" : ").append(r.participantsSummary()).append("\n");
        }
        us.out().println(sys(sb.toString().trim()));
    }

    // מנהל משמרת מצטרף
    private static void joinAsSupervisor(UserSession sup, String roomId) {
        // דוגמה פשוטה: דורשים שם משתמש "SHIFT_LEAD" כדי להצטרף
        if (!"SHIFT_LEAD".equals(sup.name())) {
            sup.out().println(sys("✖ Only SHIFT_LEAD can join rooms."));
            return;
        }
        ChatRoom r = rooms.get(roomId);
        if (r == null) { sup.out().println(sys("✖ No such room.")); return; }

        r.addSupervisor(sup);
        sup.setActiveRoomId(r.id());
        sup.setBusy(true);
        r.system("Supervisor " + sup.name() + " joined room " + r.id());
        broadcastPresence();
    }

    private static void notifyPending(String freedUser) {
        Queue<String> q = pendingByTarget.get(freedUser);
        if (q == null) return;

        String requester;
        while ((requester = q.poll()) != null) {
            UserSession req = sessionsByName.get(requester);
            UserSession tgt = sessionsByName.get(freedUser);
            if (req == null || tgt == null) continue;
            if (tgt.isBusy() || tgt.activeRoomId()!=null) {
                // עדיין עסוק – נחזיר לתור ונפסיק להיום
                q.offer(requester);
                break;
            }
            req.out().println(sys(freedUser + " is now free. Opening chat..."));
            startChat(req, freedUser);
            // פתחנו צ'אט; אין צורך להמשיך לנסות פתיחה נוספת כרגע
            break;
        }
    }

    private static void broadcastPresence() {
        String msg = presenceMessage();
        for (UserSession s : allSessions) {
            if (s.name()!=null) s.out().println(msg);
        }
    }

    private static String presenceMessage() {
        List<String> lst = new ArrayList<>();
        for (UserSession s : allSessions) {
            if (s.name()!=null) {
                String status = "";
                if (s.isBusy()) {
                    status = "(BUSY)";
                } else {
                    status = "(FREE)";
                }

                String roomInfo = "";
                if (s.activeRoomId() != null) {
                    roomInfo = "[in " + s.activeRoomId() + "]";
                }

                lst.add(s.name() + status + roomInfo);
            }
        }
        return sys("Online: " + lst);
    }

    private static void cleanup(UserSession us) {
        try {
            String name = us.name();
            if (name != null) {
                sessionsByName.remove(name);
            }
            allSessions.remove(us);
            // יציאה מצ'אט אם קיים
            if (us.activeRoomId() != null) {
                ChatRoom r = rooms.get(us.activeRoomId());
                if (r != null) {
                    r.system(name + " disconnected.");
                    r.remove(us);
                    if (r.isEmpty()) rooms.remove(r.id());
                }
            }
            broadcastSys((name != null ? name : us.addr()) + " left.");
            broadcastPresence();
        } catch (Exception ignored) {}
    }

    // --- Utilities ---
    private static void broadcastSys(String text) {
        String line = sys(text);
        for (UserSession s : allSessions) s.out().println(line);
    }

    private static String sys(String text) {
        return "["+TS.format(new Date())+"] * " + text;
    }

    private static void log(String x) {
        System.out.println(new Date() + " " + x);
    }
}
