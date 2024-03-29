import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;



public class Server {
    private static final int PORT = 8080;
    private static final String IP_ADDRESS = "127.0.0.1";
    protected static final ScheduledExecutorService COORDINATOR_TIMER = Executors.newSingleThreadScheduledExecutor();

    protected static final ArrayList<ClientThread> CLIENT_THREADS = new ArrayList<>();
    protected static final ArrayList<String> USER_NAMES = new ArrayList<>();
    protected static String coordinator = null;
    protected static boolean coordinatorStatus;
    private static ServerSocket serverSocket;
    public static void main(String[] args) {
        StartServer();
    }

    public static void StartServer(){
        try {
            InetAddress inetAddress = InetAddress.getByName(IP_ADDRESS);
            ServerSocket serverSocket = new ServerSocket(PORT, 50, inetAddress);
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket);

                ClientThread clientThread = new ClientThread(socket);
                CLIENT_THREADS.add(clientThread);
                clientThread.start();
            }
        } catch (IOException ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }



    public static void broadcast(String message, ClientThread sender) {
        for (ClientThread clientThread : CLIENT_THREADS) {
            if (clientThread != sender) {
                clientThread.sendMessage(message);
            } else {
                if (message.equals("Disconnecting Inactive Users")) {
                    clientThread.sendMessage("You have been kicked out.");
                    try {
                        clientThread.getSocket().close();
                        broadcast(clientThread.getUsername() + " has been disconnected from the chat due to inactivity", clientThread);

                        if (coordinator.equals(clientThread.getUsername())) {
                            coordinator = USER_NAMES.get(1);
                            ClientThread newCoordinator = CLIENT_THREADS.get(0);
                            coordinator = USER_NAMES.get(1);
                            sendPrivateMessage("You are the new Coordinator!\nThere is an active" +
                                            " check timer for the Coordinator, if you fail to respond within" +
                                            " 60 seconds you will be disconnected due to inactivity.",
                                    coordinator, sender);
                            startCoordinatorTimer(newCoordinator);
                            broadcast("Server: " + coordinator + " is the new coordinator", null);
                            broadcast("Server: The coordinator changed from " + sender.getUsername() + " to " + coordinator,null);
                        }
                        USER_NAMES.remove(clientThread.getUsername());
                        CLIENT_THREADS.remove(clientThread);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static void startCoordinatorTimer(ClientThread sender) {
        COORDINATOR_TIMER.schedule(() -> {
            if (!coordinatorStatus) {
                broadcast("Disconnecting Inactive Users", sender);
            } else {
                coordinatorStatus = false;
                startCoordinatorTimer(sender);
            }
        }, 60, TimeUnit.SECONDS);
    }

    public static void sendPrivateMessage(String message, String recipientName, ClientThread sender) {
        for (ClientThread clientThread : CLIENT_THREADS) {
            if (clientThread.getUsername().equals(recipientName)) {
                clientThread.sendMessage(sender.getUsername() + " (private): " + message);
                sender.sendMessage("To " + recipientName + " (private): " + message);
                return;
            }
        }
        sender.sendMessage(recipientName + " is not currently online.");
    }


    public static void removeClientThread(ClientThread clientThread) {
        CLIENT_THREADS.remove(clientThread);
    }

    public static void removeClientUsername(String user) {
        USER_NAMES.remove(user);
    }

    public static void printActiveClients(ClientThread sender) {
        StringBuilder sb = new StringBuilder();
        sb.append("Active clients:");
        for (ClientThread clientThread : CLIENT_THREADS) {
            Socket socket = clientThread.getSocket();
            String ipAddress = socket.getInetAddress().getHostAddress();
            int port = socket.getPort();
            String username = clientThread.getUsername();
            if (username.equals(coordinator)) {
                sb.append("\n- ").append(username).append(" (IP Address: ").append(ipAddress).append(" :: " +
                        "Port: ").append(port).append(") (Coordinator)");
            } else {
                sb.append("\n- ").append(username).append(" (IP Address: ").append(ipAddress).append(" :: " +
                        "Port: ").append(port).append(")");
            }
        }
        System.out.println(sb);
        sender.sendMessage(sb.toString());
    }
}

class ClientThread extends Thread {
    private final Socket socket;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final ScheduledExecutorService disconnectTimer;
    private final AtomicBoolean responded;
    protected String username;

    public ClientThread(Socket socket) throws IOException {
        this.socket = socket;
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(socket.getOutputStream(), true);
        disconnectTimer = Executors.newSingleThreadScheduledExecutor();
        responded = new AtomicBoolean(true);

        boolean check = false;
        while (true) {
            if (check) {
                writer.println("The username you chose is already in use. Please enter a valid username:");
            } else {
                writer.println("Enter a unique valid username:");
                check = true;
            }
            username = reader.readLine();
            if (username == null) {
                return;
            }
            synchronized (Server.USER_NAMES) {
                if (!username.isEmpty() && !Server.USER_NAMES.contains(username)) {
                    Server.USER_NAMES.add(username);
                    break;
                }
            }
        }
        if (Server.coordinator == null) {
            Server.coordinator = username;
            writer.println("Welcome, " + username + "! You are the Coordinator.\n" +
                    "There is an active check timer for the Coordinator, if you fail to respond within" +
                    " 60 seconds you will be disconnected due to inactivity.");
            Server.coordinatorStatus = false;
            Server.startCoordinatorTimer(this);
            System.out.println("The new client is the coordinator and named themselves: " + username);
        } else {
            writer.println("Welcome, " + username + "!\nYou can type '/users' command to check the members.");
            Server.broadcast(username + " has joined the chat!", this);
            System.out.println("The new client named themselves: " + username);
        }
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = reader.readLine()) != null) {
                if (username.equals(Server.coordinator)) {
                    Server.coordinatorStatus = true;
                }
                if (message.equalsIgnoreCase("/yes")) {

                    responded.set(true);
                    Server.broadcast("Server: " + username + " is active", null);
                } else if (message.startsWith("@")) {
                    int spaceIndex = message.indexOf(' ');
                    if (spaceIndex != -1) {
                        String recipientName = message.substring(1, spaceIndex);
                        String privateMessage = message.substring(spaceIndex + 1);
                        Server.sendPrivateMessage(privateMessage, recipientName, this);
                    }
                } else if (message.equals("/users")) {
                    Server.printActiveClients(this);
                } else if (message.equals("/active") && Server.coordinator.equals(username)) {
                    //System.out.println("(You) Coordinator "+ username + " is asking for the active users.");
                    Server.broadcast("The Coordinator is asking for the active users.\n" +
                            "Please respond with '/yes' within 20 seconds!", this);
                    for (ClientThread clientThread : Server.CLIENT_THREADS) {
                        if (clientThread != this) {
                            clientThread.startDisconnectTimer();
                        }
                    }
                } else if (message.equals("/quit")) {
                    try {
                        reader.close();
                        writer.close();
                        socket.close();

                    } catch (IOException ex) {
                        System.err.println("Error: " + ex.getMessage());
                    }
                    Server.removeClientThread(this);
                    Server.broadcast(username + " has left the chat.", null);
                    System.out.println(username + " has left the chat.");
                    if (Server.coordinator.equals(username)) {
                        if (Server.USER_NAMES.size() > 1) {

                            Server.coordinator = Server.USER_NAMES.get(1);
                            Server.broadcast(Server.coordinator + " is the new Coordinator!",
                                    null);
                        }
                    }
                    Server.removeClientUsername(username);
                    return;
                } else {
                    if (message.startsWith("/")) {
                        System.out.println(username + " entered invalid Command request!");
                    } else {
                        Server.broadcast(username + ": " + message, this);
                    }
                }
            }
        } catch (IOException ex) {
            System.out.println(username + " : " + ex.getMessage());
        } finally {
            try {
                reader.close();
                writer.close();
                socket.close();
            } catch (IOException ex) {
                System.err.println("Error: " + ex.getMessage());
            }
            Server.removeClientThread(this);
            Server.broadcast(username + " has left the chat.", null);

            if (Server.coordinator.equals(username)) {
                if (Server.USER_NAMES.size() > 1) {
                    Server.coordinator = Server.USER_NAMES.get(1);
                    Server.sendPrivateMessage("You are the new Coordinator!",
                            Server.coordinator, this);
                    Server.broadcast("Server: " + Server.coordinator + " is the new coordinator", null);
                    System.out.println("The coordinator changed from " + username + " to " + Server.coordinator);
                }else{
                    Server.coordinator = null;
                    System.out.println("Server has no coordinator");
                    System.out.println("Server is Empty!");
                }

            }
            Server.removeClientUsername(username);
        }
    }


    public void sendMessage(String message) {
        writer.println(message);
    }

    public String getUsername() {
        return username;
    }

    public Socket getSocket() {
        return socket;
    }

    public void startDisconnectTimer() {
        responded.set(false);
        disconnectTimer.schedule(() -> {
            if (!responded.get()) {
                Server.broadcast("Disconnecting Inactive Users", this);
            }
        }, 20, TimeUnit.SECONDS);
    }
}
