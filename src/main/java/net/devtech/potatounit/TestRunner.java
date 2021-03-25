package net.devtech.potatounit;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.launch.knot.Knot;

public abstract class TestRunner extends BlockJUnit4ClassRunner {
	protected final ClassLoader loader;

	public TestRunner(Class<?> testClass, EnvType type) throws InitializationError, ReflectiveOperationException {
		super(Class.forName(testClass.getName(), true, loader(type)));
		this.loader = this.getTestClass().getJavaClass().getClassLoader();
	}

	protected static ClassLoader loader(EnvType type) throws ReflectiveOperationException {
		System.setProperty("fabric.dli.env", type.name().toLowerCase(Locale.ROOT));
		return main(new String[]{"nogui"});
	}

	@Override
	public void run(RunNotifier notifier) {
		try {
			Thread thread = new Thread(() -> super.run(notifier));
			thread.setContextClassLoader(this.loader);
			thread.start();
			thread.join();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public static final class Server extends TestRunner {
		public Server(Class<?> testClass) throws InitializationError, ReflectiveOperationException {
			super(testClass, EnvType.SERVER);
		}
	}

	public static final class Client extends TestRunner {
		public Client(Class<?> testClass) throws InitializationError, ReflectiveOperationException {
			super(testClass, EnvType.CLIENT);
		}
	}

	public static ClassLoader main(String[] args) throws ReflectiveOperationException {
		String env = System.clearProperty("fabric.dli.env"); // desired environment, for config section selection
		String main = System.clearProperty("fabric.dli.main"); // main class to invoke afterwards
		String config = System.clearProperty("fabric.dli.config"); // config file location
		Path configFile;

		if (main == null) {
			System.err.println("error: missing fabric.dli.main property, can't launch (copy your run args into the test run args)");
			System.exit(1);
		} else if (env == null || config == null) {
			warnNoop("missing fabric.dli.env or fabric.dli.config properties  (copy your run args into the test run args)");
		} else if (!Files.isRegularFile(configFile = Paths.get(decodeEscaped(config)))
		           || !Files.isReadable(configFile)) {
			warnNoop("missing or unreadable config file ("+configFile+")  (copy your run args into the test run args)");
		} else {
			List<String> extraArgs = new ArrayList<>();
			Map<String, String> extraProperties = new HashMap<>();

			try {
				parseConfig(configFile, env, extraArgs, extraProperties);

				// apply args
				String[] newArgs = extraArgs.toArray(new String[args.length + extraArgs.size()]);
				System.arraycopy(args, 0, newArgs, extraArgs.size(), args.length);
				args = newArgs;

				// apply properties
				for (Map.Entry<String, String> e : extraProperties.entrySet()) {
					System.setProperty(e.getKey(), e.getValue());
				}
			} catch (IOException e) {
				warnNoop("parsing failed: "+e.toString());
			}
		}

		if(main.equals("net.fabricmc.loader.launch.knot.KnotClient") || main.equals("net.fabricmc.loader.launch.knot.KnotServer")) {
			Knot knot = new Knot(EnvType.valueOf(env.toUpperCase(Locale.ROOT)), null);
			Method init = Knot.class.getDeclaredMethod("init", String[].class);
			init.setAccessible(true);
			return (ClassLoader) init.invoke(knot, (Object) args);
		} else
			throw new UnsupportedOperationException(main);
	}

	private static void parseConfig(Path file, String env, List<String> extraArgs, Map<String, String> extraProperties) throws IOException {
		final int STATE_NONE = 0;
		final int STATE_ARGS = 1;
		final int STATE_PROPERTIES = 2;
		final int STATE_SKIP = 3;

		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String line;
			int state = STATE_NONE;

			while ((line = reader.readLine()) != null) {
				if (line.isEmpty()) continue;

				boolean indented = line.charAt(0) == ' ' || line.charAt(0) == '\t';
				line = line.trim();
				if (line.isEmpty()) continue;

				if (!indented) {
					int offset;

					// filter env
					if (line.startsWith("common")) {
						offset = "common".length();
					} else if (line.startsWith(env)) {
						offset = env.length();
					} else { // wrong env, skip
						state = STATE_SKIP;
						continue;
					}

					switch (line.substring(offset)) {
					case "Args":
						state = STATE_ARGS;
						break;
					case "Properties":
						state = STATE_PROPERTIES;
						break;
					default:
						throw new IOException("invalid attribute: "+line);
					}
				} else if (state == STATE_NONE) { // indented, no state/attribute
					throw new IOException("value without preceding attribute: "+line);
				} else if (state == STATE_ARGS) {
					extraArgs.add(line);
				} else if (state == STATE_PROPERTIES) {
					int pos = line.indexOf('=');
					String key = pos >= 0 ? line.substring(0, pos).trim() : line;
					String value = pos >= 0 ? line.substring(pos + 1).trim() : "";

					extraProperties.put(key, value);
				} else if (state == STATE_SKIP) {
					// Wrong environment for the section, skip the line
				} else { // shouldn't happen
					throw new IllegalStateException();
				}
			}
		}
	}

	private static void warnNoop(String msg) {
		System.out.printf("warning: dev-launch-injector in pass-through mode, %s%n", msg);
	}

	/**
	 * Decode tokens in the form @@x where x is 1-4 hex chars encoding an UTF-16 code unit.
	 *
	 * <p>Example: 'a@@20b' -> 'a b'
	 */
	private static String decodeEscaped(String s) {
		if (s.indexOf("@@") < 0) return s;

		Matcher matcher = Pattern.compile("@@([0-9a-fA-F]{1,4})").matcher(s);
		StringBuilder ret = new StringBuilder(s.length());
		int start = 0;

		while (matcher.find()) {
			ret.append(s, start, matcher.start());
			ret.append((char) Integer.parseInt(matcher.group(1), 16));
			start = matcher.end();
		}

		ret.append(s, start, s.length());

		return ret.toString();
	}
}
