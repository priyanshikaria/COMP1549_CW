import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ServerConnectionTest {
    private ServerSocket serverSocketInstance;
    private ExecutorService executorServiceInstance;

    // Test the client connection to the server
    @Test
    public void testClientConnectionToServer() throws IOException {
        int portNumber = 8088;
        String ipAddress = "localhost";
        try (ServerSocket tempServerSocket = new ServerSocket(portNumber);
             Socket clientSocket = new Socket(ipAddress, portNumber)) {
            assertNotNull(clientSocket);
        }
    }

    // Set up the server and executor service before each test
    @BeforeEach
    public void setupServerAndExecutor() throws IOException {
        int portNumber = 8888;
        serverSocketInstance = new ServerSocket(portNumber);
        executorServiceInstance = Executors.newSingleThreadExecutor();
        executorServiceInstance.submit(() -> {
            try {
                while (true) {
                    Socket clientSocket = serverSocketInstance.accept();
                    BufferedReader inputReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter outputWriter = new PrintWriter(clientSocket.getOutputStream(), true);
                    String clientId = inputReader.readLine();
                    if (Server.coordinator == null) {
                        Server.coordinator = clientId;
                        outputWriter.println("You are the new Coordinator!");
                    } else {
                        outputWriter.println("Welcome, " + clientId + "!");
                    }
                }
            } catch (IOException e) {
                 // Print the stack trace for debugging purposes
            }
        });
    }

    // Tear down the server and executor service after each test
    @AfterEach
    public void tearDownServerAndExecutor() throws IOException {
        serverSocketInstance.close();
        executorServiceInstance.shutdown();
    }

    // Test if the first client becomes the coordinator
    @Test
    public void testFirstClientBecomesCoordinator() throws IOException {
        try (Socket client1Socket = new Socket("localhost", 8888);
             BufferedReader client1Reader = new BufferedReader(new InputStreamReader(client1Socket.getInputStream()));
             PrintWriter client1Writer = new PrintWriter(client1Socket.getOutputStream(), true);
             Socket client2Socket = new Socket("localhost", 8888);
             BufferedReader client2Reader = new BufferedReader(new InputStreamReader(client2Socket.getInputStream()));
             PrintWriter client2Writer = new PrintWriter(client2Socket.getOutputStream(), true)) {

            // Send a message from the first client
            client1Writer.println("Client1");

            // Check if the first client becomes the coordinator
            String receivedMessage1 = client1Reader.readLine();
            assertEquals("You are the new Coordinator!", receivedMessage1);

            // Send a message from the second client
            client2Writer.println("Client2");

            // Check if the second client is welcomed as a regular client
            String receivedMessage2 = client2Reader.readLine();
            assertEquals("Welcome, Client2!", receivedMessage2);
        }
    }
}