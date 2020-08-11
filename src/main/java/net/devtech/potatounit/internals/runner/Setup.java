package net.devtech.potatounit.internals.runner;

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import net.devtech.potatounit.internals.Main;

import net.fabricmc.loader.launch.knot.Knot;

public class Setup {
	private static final Logger LOGGER = Logger.getLogger("Setup");
	static ClassLoader init() {
		File file = new File("");
		if (file.getAbsolutePath()
		        .endsWith("testRun")) {
			System.setProperty("fabric.dli.config",
			                   file.getAbsoluteFile()
			                       .getParentFile()
			                       .getAbsolutePath() + "/.gradle/loom-cache/launch.cfg".replace('/', File.separatorChar));
			System.setProperty("potato.unt.test", "true");
			// todo https://gaming.stackexchange.com/questions/32288/how-do-i-create-an-empty-minecraft-world
			AtomicReference<Knot> knotRef = new AtomicReference<>();
			new Thread(() -> {
				try {
					LOGGER.info("Server starting!");
					Main.main(new String[] {"nogui"}, knotRef::set);
				} catch (Throwable throwable) {
					throw new RuntimeException(throwable);
				}
			}).start();

			// hacky way of cross-classloader locks
			synchronized (ConcurrentHashMap.class) {
				try {
					ConcurrentHashMap.class.wait();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}

			LOGGER.info("Server started!");
			return knotRef.get().getTargetClassLoader();
		} else {
			throw new IllegalStateException("set working directory to 'testRun' to run unit tests!");
		}
	}
}
