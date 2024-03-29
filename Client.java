import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Client class to connect to the server and communicate with it
public class Client {
    private static final String DEFAULT_SERVER_IP = "127.0.0.1";
    private static final int DEFAULT_SERVER_PORT = 8080;
    public String username;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Welcome to the Chat Client!");
        System.out.println("1. Connect automatically to default server");
        System.out.println("2. Connect manually");

        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        String ipAddress;
        int serverPort;

        switch (choice) {
            case 1:
                ipAddress = DEFAULT_SERVER_IP;
                serverPort = DEFAULT_SERVER_PORT;
                break;
            case 2:
                System.out.println("Enter the server IP Address:");
                ipAddress = scanner.nextLine();
                System.out.println("Enter the server Port:");
                serverPort = scanner.nextInt();
                scanner.nextLine(); // Consume newline
                break;
            default:
                System.out.println("Invalid choice. Exiting...");
                return;
        }

        connectToServer(ipAddress, serverPort);

        scanner.close();
    }

    public static void connectToServer(String ipAddress, int port) {
        try (Socket socket = new Socket(ipAddress, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {

            // Get username from user input and send it to the server
            String username = null;
            while (username == null) {
                System.out.println(reader.readLine());
                String input = consoleReader.readLine();
                writer.println(input);
                username = input;
            }

            // Start a new thread to listen for incoming messages from the server
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.execute(new ServerListener(socket));

            // Read user input from the console and send it to the server
            while (true) {
                String input = consoleReader.readLine();
                if (input.startsWith("@")) {
                    int spaceIndex = input.indexOf(' ');
                    if (spaceIndex != -1) {
                        String recipientName = input.substring(1, spaceIndex);
                        String message = input.substring(spaceIndex + 1);
                        writer.println("@" + recipientName + " " + message);
                    } else {
                        System.out.println("Invalid private message format. Usage: @recipient message");
                    }
                } else if (input.equalsIgnoreCase("/active")) {
                    // Handle active status if needed
                    writer.println(input);

                } else if (input.equalsIgnoreCase("/quit")) {
                    writer.println(input);
                    System.err.println("You have left the chat.");
                    System.exit(0);

                } else if (input.equalsIgnoreCase("/yes")){

                    writer.println(input);
                    System.out.println("Your Status: Active");

                } else {
                    writer.println(input);
                    System.out.println("You: " + input);
                }
            }

        } catch (IOException ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }
}

// ServerListener class to handle incoming messages from the server
class ServerListener implements Runnable {
    private final Socket socket;
    private volatile boolean isRunning = true;

    // Constructor to initialize the ServerListener with a socket
    public ServerListener(Socket socket) {
        this.socket = socket;
    }

    // Run method to read messages from the server and display them
    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String message;
            while (isRunning && (message = reader.readLine()) != null) {
                System.out.println(message);
            }
        } catch (IOException ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }
}
