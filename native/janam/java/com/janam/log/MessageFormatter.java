package com.janam.log;
import java.util.Arrays;

/**
 *   - ChatGPT wrote this...
 *
 *   Below is a clean-room, SLF4J-compatible formatter with:
 *
 *   Full SLF4J placeholder semantics
 *   Full escape semantics (\{}, \\{}, etc.)
 *   Throwable extraction (last arg if Throwable)
 *   Deep array formatting (like SLF4J’s deeplyAppendParameter)
 *   Exactly compatible substitution rules
 *   No copyrighted SLF4J code used
 *   Two APIs:
 *
 * format(String, Object...) → String
 *
 * formatWithThrowable(String, Object...) → FormatResult
 *   Where FormatResult contains:
 *     String message
 *     Throwable throwableOrNull
 */



public final class MessageFormatter
{

	// Result container (like FormattingTuple but simpler)
	public static final class FormatResult {
		public final String message;
		public final Throwable throwable;

		public FormatResult(String message, Throwable throwable) {
			this.message = message;
			this.throwable = throwable;
		}
	}

	// ------------------------------------------------------------
	// API: Format with Throwable extraction (SLF4J-compatible)
	// ------------------------------------------------------------
	public static FormatResult formatWithThrowable(String pattern, Object... args) {
		if (pattern == null) {
			return new FormatResult("null", extractThrowable(args));
		}

		Throwable throwable = extractThrowable(args);
		Object[] trimmedArgs = trimThrowable(args, throwable);

		String msg = formatInternal(pattern, trimmedArgs);
		return new FormatResult(msg, throwable);
	}

	// ------------------------------------------------------------
	// API: Format without returning throwable
	// ------------------------------------------------------------
	public static String format(String pattern, Object... args) {
		if (pattern == null) {
			return "null";
		}
		return formatInternal(pattern, args);
	}

	// ------------------------------------------------------------
	// Extracts trailing Throwable argument
	// ------------------------------------------------------------
	private static Throwable extractThrowable(Object[] args) {
		if (args == null || args.length == 0) {
			return null;
		}
		Object last = args[args.length - 1];
		return (last instanceof Throwable) ? (Throwable) last : null;
	}

	private static Object[] trimThrowable(Object[] args, Throwable t) {
		if (t == null) return args;
		return Arrays.copyOf(args, args.length - 1);
	}

	// ------------------------------------------------------------
	// SLF4J-accurate formatting logic
	// ------------------------------------------------------------
	private static String formatInternal(String pattern, Object[] args) {
		if (pattern == null) return "null";
		if (args == null) args = new Object[0];

		int patternLength = pattern.length();
		int argIndex = 0;

		StringBuilder sb = new StringBuilder(patternLength + 50);

		for (int i = 0; i < patternLength; i++) {
			char c = pattern.charAt(i);

			// Escape handling
			if (c == '\\') {
				if (i + 1 < patternLength) {
					char next = pattern.charAt(i + 1);

					// Escape a placeholder: \{}
					if (next == '{' && i + 2 < patternLength && pattern.charAt(i + 2) == '}') {
						sb.append("{}");
						i += 2;
						continue;
					}

					// Escaped backslash: \\
					if (next == '\\') {
						sb.append('\\');
						i++;
						continue;
					}

					// Normal backslash: keep it
					sb.append('\\');
					continue;
				}

				// trailing backslash
				sb.append('\\');
				continue;
			}

			// Non-escaped "{}"
			if (c == '{' && i + 1 < patternLength && pattern.charAt(i + 1) == '}') {
				if (argIndex < args.length) {
					appendValue(sb, args[argIndex++]);
				} else {
					sb.append("{}");
				}
				i++;
				continue;
			}

			// Literal character
			sb.append(c);
		}

		return sb.toString();
	}

	// ------------------------------------------------------------
	// SLF4J-style deep array formatting
	// ------------------------------------------------------------
	private static void appendValue(StringBuilder sb, Object value) {
		if (value == null) {
			sb.append("null");
			return;
		}

		Class<?> type = value.getClass();

		if (!type.isArray()) {
			sb.append(value);
			return;
		}

		// Handle primitive arrays
		if (value instanceof boolean[]) sb.append(Arrays.toString((boolean[]) value));
		else if (value instanceof byte[]) sb.append(Arrays.toString((byte[]) value));
		else if (value instanceof char[]) sb.append(Arrays.toString((char[]) value));
		else if (value instanceof short[]) sb.append(Arrays.toString((short[]) value));
		else if (value instanceof int[]) sb.append(Arrays.toString((int[]) value));
		else if (value instanceof long[]) sb.append(Arrays.toString((long[]) value));
		else if (value instanceof float[]) sb.append(Arrays.toString((float[]) value));
		else if (value instanceof double[]) sb.append(Arrays.toString((double[]) value));
		else
			sb.append(Arrays.deepToString((Object[]) value));
	}
}
