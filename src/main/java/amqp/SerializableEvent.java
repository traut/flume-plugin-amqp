package amqp;

import java.io.Serializable;

import com.cloudera.flume.core.Event;
import com.cloudera.flume.core.EventImpl;

public class SerializableEvent extends EventImpl implements Serializable {
	
	private static final long serialVersionUID = 1L;

	public SerializableEvent(Event event) {
		super(event);
	}
}
