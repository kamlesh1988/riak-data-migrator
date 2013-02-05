package com.basho.proserv.datamigrator.io;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.basho.proserv.datamigrator.util.NamedThreadFactory;
import com.basho.riak.client.IRiakObject;
import com.basho.riak.client.raw.pbc.ConversionUtilWrapper;
import com.basho.riak.pbc.RiakObject;
import com.google.protobuf.ByteString;

public class ThreadedRiakObjectWriter implements IRiakObjectWriter {
	@SuppressWarnings("unused")
	private final Logger log = LoggerFactory.getLogger(ThreadedRiakObjectReader.class);
	private static final int DEFAULT_QUEUE_SIZE = 10000;
	private static final String  STOP_STRING = "STOPSTOPSTOPSTOPSTOP";
	private static final ByteString STOP_FLAG = ByteString.copyFromUtf8(STOP_STRING);
	private static final IRiakObject STOP_OBJECT = ConversionUtilWrapper.convertConcreteToInterface(new RiakObject(STOP_FLAG, STOP_FLAG, STOP_FLAG, STOP_FLAG));
	
	private final LinkedBlockingQueue<IRiakObject> queue;
	private final NamedThreadFactory threadFactory = new NamedThreadFactory();
	private final ExecutorService executor = Executors.newCachedThreadPool(threadFactory);
	
	private static int threadId = 0;
	private long count = 0;
	
	public ThreadedRiakObjectWriter(File file) {
		this.queue = new LinkedBlockingQueue<IRiakObject>(DEFAULT_QUEUE_SIZE);
		this.threadFactory.setNextThreadName(String.format("ThreadedRiakObjectWriter-%d", threadId++));
		executor.submit(new RiakObjectWriterThread(file, queue));
	}
	
	@Override
	public boolean writeRiakObject(IRiakObject riakObject) {
		try {
			this.queue.put(riakObject);
		} catch (InterruptedException e) {
			return false;
		}
		return true;
	}

	@Override
	public void close() {
		try {
			this.queue.put(STOP_OBJECT);
		} catch (InterruptedException e) {
			// no-op
		}
		executor.shutdown();
	}
	
	private class RiakObjectWriterThread extends RiakObjectWriter implements Runnable {
		private final LinkedBlockingQueue<IRiakObject> queue;
		
		public RiakObjectWriterThread(File file, 
					LinkedBlockingQueue<IRiakObject> queue) {
			super(file);
			
			this.queue = queue;
			
		}

		@Override
		public void run() {
			try {
				while (!Thread.currentThread().isInterrupted()) {
					IRiakObject riakObject = queue.poll();
					if (riakObject == null) {
						Thread.sleep(10);
						continue;
					}
					if (riakObject.getBucket().compareTo(STOP_STRING) == 0) {
						break;
					}
					boolean success = super.writeRiakObject(riakObject);
					++count;
				}
			} catch (InterruptedException e) {
				// no-op, allow to exit
			}
			
			super.close();
			
		}
		
	}

}
