package org.example.socketTCP;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

public class WebServer {
    /**
     * Inicia um servidor socket para monitorar as requisições do cliente
     * e então as envia para o HttpWorker
     * 
     * @throws IOException
     */
    public static void main(String args[]) {
        // Comprimento máximo da fila para novas conexões
        int queue_len;
        int port;

        // Representa o cliente socket
        Socket socket;

        try {
            // Recebe parâmetros de config.properties
            String basePath = new File("").getAbsolutePath();
            String configFilePath = basePath + "/config.properties";

            FileInputStream propsInput = new FileInputStream(configFilePath);

            Properties prop = new Properties();
            prop.load(propsInput);

            queue_len = Integer.parseInt(prop.getProperty("QUEUE_LEN"));
            port = Integer.parseInt(prop.getProperty("PORT"));

            // Setup do server socket
            ServerSocket servsocket = new ServerSocket(port, queue_len);
            System.out.println("Servidor web está iniciando, escutando na porta " + port + ".");
            System.out.println("Pode acessar em http://localhost:2540.");

            while (true) {
                socket = servsocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String req = "";
                String clientRequest = "";
                while ((clientRequest = reader.readLine()) != null) {
                    if (req.equals("")) {
                        req = clientRequest;
                    }
                    if (clientRequest.equals("")) { // parar se a requisição chegar ao fim
                        break;
                    }
                }

                if (req != null && !req.equals("")) {
                    new HttpWorker(req, socket).start();
                }
            }
        } catch (IOException ex) {
            // Handle the exception
            System.out.println(ex);
        } finally {
            System.out.println("Servidor foi fechado!");
        }
    }
}