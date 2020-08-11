package net.devtech.potatounit.api.util;

import java.util.Enumeration;

import net.minecraft.server.MinecraftServer;

public class PUnit {
	/**
	 * waits for a specific amount of ticks
	 */
	@SuppressWarnings ("StatementWithEmptyBody")
	public static void wait(MinecraftServer server, int ticks) {
		if (ticks == 0) {
			return;
		}
		int waitFor = server.getTicks() + ticks;
		while (server.getTicks() < waitFor) {
			synchronized (Enumeration.class) {
				Enumeration.class.notifyAll();
			}
		}
		synchronized (Enumeration.class) {
			try {
				Enumeration.class.wait();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static void init() {}
}
