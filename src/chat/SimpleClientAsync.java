package chat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

// The SimpleClientAsync class is the chat client program.
// - Connects to the server on port 7000 using a socket.
// - Opens input/output streams to communicate with the server and the user’s console.
// - Starts a background thread that constantly reads and prints messages from the server.
// - Lets the user type messages or commands, which are sent to the server.
// - The client closes when the user types "/quit" or "goodbye".
// In short, this is the program the user runs to join and chat with the server.

public class SimpleClientAsync {

    public static void main(String[] args) {
        try (Socket s = new Socket("localhost", 7000);
             BufferedReader fromSrv = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintStream toSrv = new PrintStream(s.getOutputStream(), true);
             BufferedReader fromUser = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to " + s.getRemoteSocketAddress());

            Thread reader = new Thread(() -> {
                try {
                    String srvMsg;
                    while ((srvMsg = fromSrv.readLine()) != null) {
                        System.out.println(srvMsg);
                    }
                } catch (Exception ignored) {}
            });
            reader.setDaemon(true);
            reader.start();

            System.out.println(ChatColors.YELLOW+"----- Type: /menu -----"+ChatColors.RESET);
            String line;
            while ((line = fromUser.readLine()) != null) {
                toSrv.println(line);
                if ("/quit".equalsIgnoreCase(line) || "goodbye".equalsIgnoreCase(line)) break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

