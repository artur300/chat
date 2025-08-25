package chat;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public class ChatRoom {
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm");

    private final String id;
    private final Set<UserSession> participants = new CopyOnWriteArraySet<>();
    private final Set<UserSession> supervisors = new CopyOnWriteArraySet<>();

    private ChatRoom(String id) { this.id = id; }

    public static ChatRoom create(UserSession a, UserSession b) {
        String id = "R" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ChatRoom r = new ChatRoom(id);
        r.participants.add(a);
        r.participants.add(b);
        return r;
    }

    public String id() { return id; }

    public void system(String text) {
        String line = "["+TS.format(new Date())+"] * " + text;
        sendToAll(line);
    }

    public void say(UserSession from, String msg) {
        String line = "["+TS.format(new Date())+"] " + from.name() + ": " + msg;
        sendToAll(line);
    }

    public void addSupervisor(UserSession sup) {
        supervisors.add(sup);
        // מודיעים גם למשתתפים
        system("Supervisor " + sup.name() + " joined.");
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
        for (UserSession s : supervisors) p.add("SUP:"+s.name());
        return String.join(", ", p);
    }

    private void sendToAll(String line) {
        for (UserSession u : participants) u.out().println(line);
        for (UserSession s : supervisors) s.out().println(line);
    }
}

