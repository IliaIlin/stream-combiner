package sockets.combiner;

import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Main {

    public static void main(String[] args) {
        List<Pair<String, Integer>> hostsAndPorts = Arrays.stream(((String) readProperties().get("sockets.to.connect")).split(";"))
                .map(it -> {
                    var splitHostAndPort = it.split(":");
                    return Pair.of(splitHostAndPort[0], Integer.valueOf(splitHostAndPort[1]));
                }).toList();
        var openedSockets = new SocketConnector().connect(hostsAndPorts);
        Combiner combiner = new Combiner(openedSockets);
        combiner.readAndCombineMessageStreams();
    }

    private static Properties readProperties() {
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream("application.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            return prop;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
