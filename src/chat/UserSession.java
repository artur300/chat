package chat;

import java.io.BufferedReader;
import java.io.PrintStream;

// The UserSession class represents a single connected user in the chat system.
// - It stores the user’s name, busy status, and the ID of the room they are in.
// - It uses SocketData to handle input (reading messages) and output (sending messages).
// - Provides methods to get and update the user’s name, status, and active room.
// - Also allows access to the client’s address, input stream, and output stream.
// In short, this is the "profile" of each connected user while they are online.

public class UserSession {
    private final SocketData sd;
    private String name;
    private boolean busy;
    private String activeRoomId;
    public UserSession(SocketData sd) {
        this.sd = sd;
    }
    public String name() { return name; }
    public void setName(String n) { this.name = n; }
    public boolean isBusy() { return busy; }
    public void setBusy(boolean b) { this.busy = b; }
    public String activeRoomId() { return activeRoomId; }
    public void setActiveRoomId(String id) { this.activeRoomId = id; }
    public String addr() { return sd.getClientAddress(); }
    public BufferedReader in() { return sd.getReader(); }
    public PrintStream out() { return sd.getOutputStream(); }
}

