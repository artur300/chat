package chat;

import java.io.BufferedReader;
import java.io.PrintStream;

public class UserSession {
    private final SocketData sd;
    private String name;          // אחרי negotiate
    private boolean busy;         // FREE/BUSY
    private String activeRoomId;  // מזהה חדר אם קיים

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

