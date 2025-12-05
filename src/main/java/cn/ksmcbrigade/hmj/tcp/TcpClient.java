package cn.ksmcbrigade.hmj.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/12/2
 */
public class TcpClient {
    private String host;
    private int port;

    public TcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean testConnection() {
        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("PING");
            String response = in.readLine();
            return "AGENT_READY".equals(response);

        } catch (IOException e) {
            return false;
        }
    }

    public String sendRetransformRequest(List<String> classes) {
        if (classes == null || classes.isEmpty()) {
            return "ERROR: No classes provided";
        }

        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // 构建消息：RETRANSFORM:class1,class2,class3
            StringBuilder message = new StringBuilder("RETRANSFORM:");
            for (int i = 0; i < classes.size(); i++) {
                if (i > 0) message.append(",");
                message.append(classes.get(i));
            }

            out.println(message.toString());
            return in.readLine();

        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }
}

