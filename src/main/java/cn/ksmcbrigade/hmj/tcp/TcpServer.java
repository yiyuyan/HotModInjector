package cn.ksmcbrigade.hmj.tcp;

import cn.ksmcbrigade.hmj.HotMixinAgent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * &#064;Author: KSmc_brigade
 * &#064;Date: 2025/12/2
 */
public class TcpServer extends Thread{
    private final ServerSocket serverSocket;
    private volatile boolean running = true;

    public TcpServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
    }

    @Override
    public void run() {
        HotMixinAgent.log("[TCP SERVER] TCP Server listening on port 65534");

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                HotMixinAgent.log("[TCP SERVER] Client connected: " + clientSocket.getInetAddress());

                // 处理客户端连接
                new ClientHandler(clientSocket).start();
            } catch (IOException e) {
                if (running) {
                    HotMixinAgent.log("[TCP SERVER] Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    public void shutdown() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            // Ignore
        }
    }

    private static class ClientHandler extends Thread {
        private Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                String message;
                while ((message = in.readLine()) != null) {
                    HotMixinAgent.log("[TCP SERVER] Received message: " + message);
                    String response = MessageHandler.handleMessage(message);
                    out.println(response);
                }
            } catch (IOException e) {
                HotMixinAgent.log("[TCP SERVER] Error handling client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
}
