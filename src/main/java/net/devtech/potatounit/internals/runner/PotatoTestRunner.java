package net.devtech.potatounit.internals.runner;

import java.lang.reflect.Method;
import java.util.Enumeration;

import net.devtech.potatounit.api.util.PUnit;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

public class PotatoTestRunner extends BlockJUnit4ClassRunner {
	private static ClassLoader knot = Setup.init();
	public PotatoTestRunner(Class<?> klass) throws InitializationError {
		super(loadFromCustomClassloader(klass));
	}

	private static Class<?> loadFromCustomClassloader(Class<?> clazz) {
		try {
			return Class.forName(clazz.getName(), true, knot);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void run(RunNotifier notifier) {
		try {
			Thread thread = new Thread(() -> {
				loadFromCustomClassloader(PUnit.class);
				super.run(notifier);
			});
			thread.setContextClassLoader(knot);
			thread.start();
			thread.join();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected Statement methodInvoker(FrameworkMethod method, Object test) {
		return new PotatoInvoker(method.getMethod(), test);
	}

	private static class PotatoInvoker extends Statement {
		private final Method method;
		private final Object object;

		private PotatoInvoker(Method method, Object object) {
			this.method = method;
			this.object = object;
		}

		@Override
		public void evaluate() throws Throwable {
			synchronized (Enumeration.class) {
				try {
					Enumeration.class.wait();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			this.method.invoke(this.object);
			synchronized (Enumeration.class) {
				Enumeration.class.notifyAll();
			}
		}
	}
}
