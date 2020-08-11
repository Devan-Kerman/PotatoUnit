package net.devtech.potatounit.internals.mixin;

import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

import net.devtech.potatounit.api.util.PUnit;
import net.devtech.potatounit.internals.runner.PotatoTestRunner;
import net.devtech.potatounit.internals.runner.Setup;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

@Mixin (MinecraftServer.class)
public class MinecraftServerMixin {
	@Shadow @Final private static Logger LOGGER;
	private static final boolean POTATO_UNIT_ENABLED = "true".equals(System.getProperty("potato.unt.test"));

	@Inject (method = "runServer",
			at = @At (value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;setFavicon(Lnet/minecraft/server/ServerMetadata;)V"))
	private void runServer(CallbackInfo ci) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
		// unlock start lock
		LOGGER.info("PotatoUnit: " + POTATO_UNIT_ENABLED);
		if (POTATO_UNIT_ENABLED) {
			Class.forName("net.devtech.potatounit.test.Registries").getField("server").set(null, this);
			// hacky way of cross-classloader locks
			synchronized (ConcurrentHashMap.class) {
				ConcurrentHashMap.class.notifyAll();
			}
			LOGGER.info("PotatoUnit ready!");
		}
	}

	@Inject (method = "runServer", at = @At (value = "FIELD", target = "Lnet/minecraft/server/MinecraftServer;loading:Z"))
	private void loading(CallbackInfo ci) {
		if (POTATO_UNIT_ENABLED) {
			synchronized (Enumeration.class) {
				Enumeration.class.notifyAll();
			}

			synchronized (Enumeration.class) {
				try {
					Enumeration.class.wait(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}
}
