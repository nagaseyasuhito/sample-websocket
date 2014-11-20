package com.github.nagaseyasuhito.sample;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import lombok.SneakyThrows;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.nagaseyasuhito.sample.websocket.EchoEndpoint;
import com.google.common.base.Stopwatch;

@RunWith(Arquillian.class)
@RunAsClient
public class EchoEndpointIT {
	private static Logger log = Logger.getLogger(EchoEndpointIT.class.getCanonicalName());

	private static final int NUMBER_OF_CLIENTS = 100;
	private static final int NUMBER_OF_LOOPS = 1000;

	private static AtomicInteger connecting = new AtomicInteger();
	private static AtomicInteger connected = new AtomicInteger();
	private static AtomicInteger succeeded = new AtomicInteger();
	private static AtomicInteger failed = new AtomicInteger();
	private static AtomicInteger processed = new AtomicInteger();
	private static List<Long> elapsedTime = Collections.synchronizedList(new ArrayList<>(NUMBER_OF_CLIENTS));
	private static CountDownLatch clientLatch;

	@ClientEndpoint
	public static class Client {
		private int currentNumber;
		private Stopwatch stopwatch;

		@OnOpen
		@SneakyThrows
		public void onOpen(Session session) {
			this.stopwatch = Stopwatch.createStarted();
			connected.incrementAndGet();

			session.getBasicRemote().sendText(Integer.toString(this.currentNumber));
		}

		@OnMessage
		@SneakyThrows
		public void onMessage(Session session, String message) {
			int count = Integer.parseInt(message);

			assertThat(count, is(this.currentNumber));

			if (this.currentNumber == NUMBER_OF_LOOPS) {
				session.close();
				succeeded.incrementAndGet();
				clientLatch.countDown();
				return;
			}

			processed.incrementAndGet();

			this.currentNumber++;

			session.getBasicRemote().sendText(Integer.toString(this.currentNumber));
		}

		@OnClose
		public void onClose() {
			elapsedTime.add(this.stopwatch.elapsed(TimeUnit.MILLISECONDS));
		}

		@OnError
		public void onError(Throwable e) {
			fail(e.getMessage());
			failed.incrementAndGet();
		}
	}

	@ArquillianResource
	private URL url;

	@Deployment
	public static Archive<?> createDeployment() {
		return ShrinkWrap.create(WebArchive.class).addClass(EchoEndpoint.class);
	}

	@Test
	public void stressTest() throws Exception {
		URI uri = new URI(this.url.toString().replaceAll("http://", "ws://") + "/echo");
		log.info("Endpoint URI: " + uri);

		WebSocketContainer container = ContainerProvider.getWebSocketContainer();
		ForkJoinPool forkJoinPool = new ForkJoinPool(NUMBER_OF_CLIENTS);
		clientLatch = new CountDownLatch(NUMBER_OF_CLIENTS);

		ExecutorService monitor = Executors.newFixedThreadPool(1);
		monitor.submit(() -> {
			while (clientLatch.getCount() != 0) {
				log.info(String.format("Connecting: %04d Connected: %04d Succeeded: %04d Failed: %04d Current: %04d Processed: %d Average: %.3f", connecting.get(), connected.get(), succeeded.get(), failed.get(), connected.get() - succeeded.get()
						- failed.get(), processed.get(), elapsedTime.stream().collect(Collectors.averagingDouble((d) -> d))));
				try {
					Thread.sleep(1000L);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});

		Stopwatch stopwatch = Stopwatch.createStarted();

		forkJoinPool.submit(() -> {
			IntStream.range(0, NUMBER_OF_CLIENTS).parallel().forEach((i) -> {
				try {
					container.connectToServer(Client.class, uri);
					connecting.incrementAndGet();
				} catch (Exception e) {
					fail(e.getMessage());
				}
			});
		}).get();
		clientLatch.await();
		monitor.shutdown();

		EchoEndpointIT.log.info(stopwatch.toString());
		log.info(String.format("Connecting: %04d Connected: %04d Succeeded: %04d Failed: %04d Current: %04d Processed: %d Average: %.3f", connecting.get(), connected.get(), succeeded.get(), failed.get(),
				connected.get() - succeeded.get() - failed.get(), processed.get(), elapsedTime.stream().collect(Collectors.averagingDouble((d) -> d))));
	}
}
