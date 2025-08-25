package chat;

import java.io.*;
import java.net.Socket;

public class SocketData {
    private final Socket socket;
    private final BufferedReader reader;
    private final PrintStream outputStream;
    private final String clientAddress;

    public SocketData(Socket socket) throws IOException {
        this.socket = socket;
        this.reader  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.outputStream = new PrintStream(socket.getOutputStream(), true);
        this.clientAddress = socket.getInetAddress() + ":" + socket.getPort();
    }

    public Socket getSocket() { return socket; }
    public BufferedReader getReader() { return reader; }
    public PrintStream getOutputStream() { return outputStream; }
    public String getClientAddress() { return clientAddress; }
}

