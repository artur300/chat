package chat;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;


public class ChatRoom {
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm");

    // צבעים קבועים — נוח לשחק בהם במקום אחד
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

    public static ChatRoom create(UserSession a, UserSession b) {
        String id = "room " + SEQ.getAndIncrement();
        ChatRoom r = new ChatRoom(id);
        r.participants.add(a);
        r.participants.add(b);
        return r;
    }

    public String id() { return id; }

    // הודעות מערכת בצבע קבוע (כחול-טורקיז)
    public void system(String text) {
        String line = "[" + TS.format(new Date()) + "] "
                + SYSTEM_COLOR + "* " + text + ChatColors.RESET;
        sendToAll(line);
    }

    // צביעה שונה לשולח/מקבל: בניית שורה מותאמת לכל נמען
    public void say(UserSession from, String msg) {
        String ts = "[" + TS.format(new Date()) + "] ";

        // למשתתפים
        for (UserSession u : participants) {
            boolean isSenderView = (u == from);

            String namePart = (isSenderView ? SENDER_NAME_COLOR : RECV_NAME_COLOR)
                    + from.name() + ChatColors.RESET;

            String msgPart  = (isSenderView ? SENDER_MSG_COLOR  : RECV_MSG_COLOR)
                    + msg + ChatColors.RESET;

            u.out().println(ts + namePart + ": " + msgPart);
        }

        // למנהלי משמרת — מוצג כמו אצל מקבל (אפשר לשנות כרצונך)
        for (UserSession sup : supervisors) {
            String namePart = ChatColors.BLUE + from.name() + ChatColors.RESET;
            String msgPart  = ChatColors.BLUE  + msg        + ChatColors.RESET;
            sup.out().println(ts + namePart + ": " + msgPart);
        }
    }

    public void addSupervisor(UserSession sup) {
        supervisors.add(sup);
    }

    public void remove(UserSession u) {
        participants.remove(u);
        supervisors.remove(u);
    }

    public boolean isEmpty() {
        return participants.isEmpty() && supervisors.isEmpty();
    }

    public String participantsSummary() {
        List<String> p = new ArrayList<>();
        for (UserSession u : participants) p.add(u.name());
        for (UserSession s : supervisors) p.add("SUP:" + s.name());
        return String.join(", ", p);
    }

    private void sendToAll(String line) {
        for (UserSession u : participants) u.out().println(line);
        for (UserSession s : supervisors) s.out().println(line);
    }


    public int participantsCount() {
        return participants.size();
    }
    public List<UserSession> participantsList() {
        return new ArrayList<>(participants);
    }
    public List<UserSession> supervisorsList() {
        return new ArrayList<>(supervisors);
    }

}


