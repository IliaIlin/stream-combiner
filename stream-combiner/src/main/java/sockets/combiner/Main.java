package sockets.combiner;

import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

public class Main {

    public static void main(String[] args) {
        var hostsAndPorts = Arrays.stream(((String) readProperties().get("sockets.to.connect")).split(";"))
                .map(it -> {
                    var splitHostAndPort = it.split(":");
                    return Pair.of(splitHostAndPort[0], Integer.valueOf(splitHostAndPort[1]));
                }).toList();
        new Processor().process(hostsAndPorts);
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
