package vontus.magicbottle.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import vontus.magicbottle.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads a YAML file of the plugin's folder into an object mapped with Configurate. The object's field initializers
 * are the defaults: the options missing from the file keep them, and the file is written back with those options
 * added (and the comments of the {@code @Comment} annotations), so the admin's values are never touched.
 */
final class ConfigFile {
	// A line with a key of a map: its indentation and the key without quotes (list items start with "- ")
	private static final Pattern KEY = Pattern.compile("^( *)\"?([^\"\\s:-][^\":]*?)\"?:(?: .*)?$");

	private ConfigFile() {
	}

	static <T> T load(Plugin plugin, String fileName, Class<T> type, String header) {
		Path path = plugin.getDataPath().resolve(fileName);
		YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
				.path(path)
				.nodeStyle(NodeStyle.BLOCK)
				.indent(2)
				.defaultOptions(options -> options.serializers(builder -> builder
						.register(Ingredient.class, Ingredient.SERIALIZER)
						.register(EnchantParser.class, EnchantParser.SERIALIZER)))
				.build();
		try {
			CommentedConfigurationNode node = loader.load();
			T value = node.get(type);
			if (value == null) {
				value = newInstance(type);
			}
			node.set(type, value);
			try {
				Files.createDirectories(path.getParent());
				loader.save(node);
				writeComments(path, header, node);
			} catch (IOException e) {
				Plugin.logger.severe("Could not save " + fileName + ": " + e.getMessage());
			}
			return value;
		} catch (ConfigurateException e) {
			// The message includes the path of the wrong option. The file isn't saved, so the admin can fix it.
			Plugin.logger.severe("Could not load " + fileName + ": " + e.getMessage() + ". Using the default values.");
			return newInstance(type);
		}
	}

	// The YAML loader keeps the comments in the nodes but doesn't write them, so they are added to the saved file:
	// each key gets the comment of its node above it, with its indentation, and the header goes first.
	private static void writeComments(Path path, String header, CommentedConfigurationNode root) throws IOException {
		StringBuilder out = new StringBuilder();
		header.lines().forEach(line -> out.append("# ").append(line).append('\n'));
		out.append('\n');

		Deque<String> keys = new ArrayDeque<>();
		Deque<Integer> indents = new ArrayDeque<>();
		boolean inHeader = true;
		for (String line : Files.readAllLines(path)) {
			// The loader keeps the header the file had, which is replaced by the current one
			inHeader &= line.startsWith("#") || line.isBlank();
			if (inHeader) {
				continue;
			}
			Matcher key = KEY.matcher(line);
			if (key.matches()) {
				String indent = key.group(1);
				while (!indents.isEmpty() && indents.peek() >= indent.length()) {
					indents.pop();
					keys.pop();
				}
				indents.push(indent.length());
				keys.push(key.group(2));
				String comment = root.node(keys.reversed().toArray()).comment();
				if (comment != null) {
					comment.lines().forEach(c -> out.append(indent).append("# ").append(c).append('\n'));
				}
			}
			out.append(line).append('\n');
		}
		Files.writeString(path, out.toString());
	}

	private static <T> T newInstance(Class<T> type) {
		try {
			return type.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
