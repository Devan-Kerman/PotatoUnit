package net.devtech.potatounit.test;

import net.devtech.potatounit.api.util.PUnit;
import net.devtech.potatounit.internals.runner.PotatoTestRunner;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

@RunWith(PotatoTestRunner.class)
public class Registries {
	public static MinecraftServer server;

	@AfterClass
	public static void thisLoader() {
		if (server != null) {
			server.stop(true);
		} else {
			System.out.println("Error: server not found!");
		}
	}

	@Test
	public void testRegistries() {
		Assert.assertEquals(Registry.ITEM.get(new Identifier("minecraft", "air")), Items.AIR);
	}

	@Test
	public void waitOneSecond() {
		int start = server.getTicks();
		PUnit.wait(server, 20);
		Assert.assertEquals(server.getTicks() - start, 20);
	}
}
