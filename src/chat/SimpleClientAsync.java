package chat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

public class SimpleClientAsync {

    public static void main(String[] args) {
        try (Socket s = new Socket("localhost", 7000);
             BufferedReader fromSrv = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintStream toSrv = new PrintStream(s.getOutputStream(), true);
             BufferedReader fromUser = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to " + s.getRemoteSocketAddress());

            // Thread לקריאת הודעות מהשרת
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

            // אם שולחים שם כארגומנט, נכניס אותו ישר
            if (args.length > 0 && args[0] != null && !args[0].isBlank()) {
                toSrv.println(args[0].trim());
            }

            System.out.println("Type /help");
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

