package chat;

import java.io.*;
import java.net.Socket;

// The SocketData class is a helper wrapper around a client socket.
// - It prepares a BufferedReader for reading incoming messages from the socket.
// - It prepares a PrintStream for sending messages back to the client.
// - It also stores the client’s address and port as a string for easy logging.
// - Provides simple getter methods so other parts of the program can access
//   the reader, writer, and client address without dealing directly with the socket.

public class SocketData {
    private final BufferedReader reader;
    private final PrintStream outputStream;
    private final String clientAddress;

    public SocketData(Socket socket) throws IOException {
        this.reader  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.outputStream = new PrintStream(socket.getOutputStream(), true);
        this.clientAddress = socket.getInetAddress() + ":" + socket.getPort();
    }

    public BufferedReader getReader() { return reader; }
    public PrintStream getOutputStream() { return outputStream; }
    public String getClientAddress() { return clientAddress; }
}

