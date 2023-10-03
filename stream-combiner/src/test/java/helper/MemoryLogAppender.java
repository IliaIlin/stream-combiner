package helper;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.Optional;

public class MemoryLogAppender extends ListAppender<ILoggingEvent> {

    public boolean isMessageSubstringPresentInLogger(String loggerName, String message) {
        return list.stream()
                .anyMatch(it -> it.getLoggerName().equals(loggerName) && it.getMessage().contains(message));
    }
}
