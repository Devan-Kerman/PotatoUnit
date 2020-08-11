package net.devtech.potatounit.internals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.launch.knot.Knot;

public final class Main {
	public static void main(String[] args, Consumer<Knot> loader) throws Throwable {
		String env = "server"; // desired environment, for config section selection
		String config = System.clearProperty("fabric.dli.config"); // config file location
		Path configFile;

		if (config == null) {
			warnNoop("missing fabric.dli.env or fabric.dli.config properties");
		} else if (!Files.isRegularFile(configFile = Paths.get(decodeEscaped(config)))
				|| !Files.isReadable(configFile)) {
			warnNoop("missing or unreadable config file ("+configFile+")");
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

		String gameJarPath = System.getProperty("fabric.gameJarPath");
		Constructor<Knot> ctor = Knot.class.getDeclaredConstructor(EnvType.class, File.class);
		ctor.setAccessible(true);
		Knot knot = ctor.newInstance(EnvType.SERVER, gameJarPath != null ? new File(gameJarPath) : null);
		loader.accept(knot);
		// invoke via method handle to minimize extra stack frames
		Method method = Knot.class.getDeclaredMethod("init", String[].class);
		method.setAccessible(true);
		method.invoke(knot, (Object) args);
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
				if (line.isEmpty()) {
					continue;
				}

				boolean indented = line.charAt(0) == ' ' || line.charAt(0) == '\t';
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}

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
		if (s.indexOf("@@") < 0) {
			return s;
		}

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