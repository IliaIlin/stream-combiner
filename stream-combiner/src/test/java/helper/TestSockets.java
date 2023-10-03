package helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import sockets.model.Message;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.StructuredTaskScope;

public class TestSockets {

    private final ConcurrentMap<Integer, PrintWriter> portToServerWriter = new ConcurrentHashMap<>();
    private final XmlMapper mapper = new XmlMapper();

    private final int timeoutMillis = 5000;
    private final String hostname = "127.0.0.1";

    public List<Socket> openSockets(List<Integer> ports) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            ports.stream().forEach(port -> scope.fork(() -> {
                var serverSocket = new ServerSocket(port);
                Socket accept = serverSocket.accept();
                portToServerWriter.put(port, new PrintWriter(accept.getOutputStream(), true));
                return null;
            }));

            var openedSocketTasks = ports.stream().map(port -> scope.fork(() -> {
                var socket = new Socket();
                socket.setSoTimeout(timeoutMillis);
                socket.connect(new InetSocketAddress(hostname, port));
                return socket;
            })).toList();

            scope.join();

            return openedSocketTasks.stream().map(StructuredTaskScope.Subtask::get).toList();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendMessage(int port, Message message) throws JsonProcessingException {
        portToServerWriter.get(port)
                .println(mapper.writeValueAsString(message));
    }
}
