import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

// This test will check if clients are having unique IDs
public class UniqueClientIdTest {
    private ServerSocket serverSocketInstance;
    private ExecutorService executorServiceInstance;
    private Set<String> connectedClientIds;

    @Before
    public void setupTestEnvironment() throws IOException {
        int serverPort = 8888;
        serverSocketInstance = new ServerSocket(serverPort);
        executorServiceInstance = Executors.newSingleThreadExecutor();
        connectedClientIds = new HashSet<>();

        executorServiceInstance.submit(() -> {
            try {
                while (true) {
                    Socket clientSocket = serverSocketInstance.accept();
                    BufferedReader inputReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter outputWriter = new PrintWriter(clientSocket.getOutputStream(), true);

                    String clientId = inputReader.readLine();
                    if (connectedClientIds.contains(clientId)) {
                        outputWriter.println("The client ID you provided is already in use. Please enter a valid ID:");
                    } else {
                        connectedClientIds.add(clientId);
                        outputWriter.println("Welcome, client " + clientId + "!");
                    }
                }
            } catch (IOException e) {
            }
        });
    }

    @After
    public void tearDownTestEnvironment() throws IOException {
        serverSocketInstance.close();
        executorServiceInstance.shutdown();
    }

    @Test
    public void testUniqueClientId() throws IOException {
        // Connect two clients with the same ID
        Socket client1Socket = new Socket("localhost", 8888);
        BufferedReader client1Reader = new BufferedReader(new InputStreamReader(client1Socket.getInputStream()));
        PrintWriter client1Writer = new PrintWriter(client1Socket.getOutputStream(), true);
        client1Writer.println("client1");
        String client1Response = client1Reader.readLine();

        Socket client2Socket = new Socket("localhost", 8888);
        BufferedReader client2Reader = new BufferedReader(new InputStreamReader(client2Socket.getInputStream()));
        PrintWriter client2Writer = new PrintWriter(client2Socket.getOutputStream(), true);
        client2Writer.println("client1");
        String client2Response = client2Reader.readLine();

        // Check if the second client was rejected and the server returned the correct response
        assertEquals("The client ID you provided is already in use. Please enter a valid ID:", client2Response);

        // Close the client sockets
        client1Socket.close();
        client2Socket.close();
    }
}