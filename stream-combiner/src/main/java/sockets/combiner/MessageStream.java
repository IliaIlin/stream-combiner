package sockets.combiner;

import sockets.model.Message;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

record MessageStream(AtomicBoolean isActive, ConcurrentLinkedQueue<Message> messageQueue) {

}
