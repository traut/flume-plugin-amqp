package amqp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.SynchronousQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudera.flume.core.Event;
import com.cloudera.flume.core.EventImpl;

public class ImpatientBatcher {

	private static final Logger LOG = LoggerFactory.getLogger(ImpatientBatcher.class);

	private final Queue<Event> events;

	private int batchSize;

	private long timeout;

	private long batchStarted;

	private LinkedList<Long> tags;
	
	class CantCreateBatchException extends Exception {
	}

	public ImpatientBatcher(int batchSize, long maxTimeout) {
		events = new SynchronousQueue<Event>();
		tags = new LinkedList<Long>();
		
		this.batchSize = batchSize;
		this.timeout = maxTimeout;
		
		this.batchStarted = System.currentTimeMillis();
	}

	public synchronized void addEvent(Event event, long deliveryTag) {
		events.add(new SerializableEvent(event));
		tags.add(deliveryTag);
	}
	
	public synchronized Event getEventFromBatch() {
		return events.poll();
	}
	
	public synchronized int size() {
		return events.size();
	}
	
	public synchronized List<Long> pollAllTags() {
		LinkedList<Long> toReturn = new LinkedList<Long>(this.tags);
		this.tags.clear();
		return toReturn;
	}

	public synchronized Event getBatchAsEvent() throws CantCreateBatchException {

		if (events.size() < batchSize && (System.currentTimeMillis() - this.batchStarted) < timeout) {
			return null;
		} else {
			if (events.size() == 0) {
				return null;
			}
			
			PackedEvent packedEvent = new PackedEvent();
			try {
				packedEvent.addEvents("batch", events);
			} catch (IllegalArgumentException e) {
				LOG.error("Can't create a batch", e);
				throw new CantCreateBatchException();
			} catch (IOException e) {
				LOG.error("Can't create a batch", e);
				throw new CantCreateBatchException();
			} 
			events.clear();
			this.batchStarted = System.currentTimeMillis();
			
			return packedEvent;
		}
	}

}

