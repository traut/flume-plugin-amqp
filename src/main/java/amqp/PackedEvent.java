package amqp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import com.cloudera.flume.core.Event;
import com.cloudera.flume.core.EventImpl;

public class PackedEvent extends EventImpl {

	public void addEvents(String key, Iterable<Event> events) throws IOException {
		List<byte[]> serializedEvents = new LinkedList<byte[]>();
		for (Event event : events) {
			serializedEvents.add(serializeObject(event));
		}
		byte[] serializedBatch = serializeObject(serializedEvents);
		this.set(key, serializedBatch);
	}

	public Collection<Event> getEvents(String key) throws IOException, ClassNotFoundException {
		byte[] serializedBatch = this.get(key);
		List<byte[]> encodedBatch = (List<byte[]>) deserializeObject(serializedBatch);
		
		List<Event> events = new LinkedList<Event>();
		
		for (byte[] encodedEvent : encodedBatch) {
			Event event = (Event) deserializeObject(encodedEvent);
			events.add(event);
		}
		return events;
	}
	
	private byte[] serializeObject(Object object) throws IOException {
		ObjectOutput out = null;
		ByteArrayOutputStream bos = null;
		try {
			bos = new ByteArrayOutputStream();
			out = new ObjectOutputStream(bos);
			out.writeObject(object);
			byte[] data = bos.toByteArray();
			return data;
		} finally {
			bos.close();
			out.close();
		}
	}

	private Object deserializeObject(byte[] data) throws IOException, ClassNotFoundException {
		ByteArrayInputStream bis = null;
		ObjectInputStream ois = new ObjectInputStream(bis);
		try {
			bis = new ByteArrayInputStream(data);
			ois = new ObjectInputStream(bis);
			return ois.readObject();
		} finally {
			bis.close();
			ois.close();
		}
	}

}
