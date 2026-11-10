package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.domain.TaskExecution;
import org.apache.velocity.app.event.EventCartridge;
import org.apache.velocity.app.event.InvalidReferenceEventHandler;
import org.apache.velocity.context.Context;
import org.apache.velocity.util.introspection.Info;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Notes each value that a setting asks for and nothing gives. Velocity leaves the text as it was written and goes on,
 * so without the note a wrong name looks like a step that worked.
 */
final class MissingValues implements InvalidReferenceEventHandler {
    private static final int MOST_FIELDS_NAMED = 12;
    private static final Pattern STEP_NAME = Pattern.compile("^\\$!?\\{?steps\\[[\"']([^\"']+)[\"']]");

    private final TaskExecution execution;
    private final String[] lines;

    private MissingValues(TaskExecution execution, String text) {
        this.execution = execution;
        this.lines = text.split("\n", -1);
    }

    /** Notes on the run each value that the text asks for and does not get. */
    static void noteOn(Context context, TaskExecution execution, String text) {
        EventCartridge events = new EventCartridge();
        events.addEventHandler(new MissingValues(execution, text));
        events.attachToContext(context);
    }

    @Override
    public Object invalidGetMethod(Context context, String reference, Object object, String property, Info info) {
        note(reference, object, property, info);
        return null;
    }

    @Override
    public boolean invalidSetMethod(Context context, String leftReference, String rightReference, Info info) {
        return false;
    }

    @Override
    public Object invalidMethod(Context context, String reference, Object object, String method, Info info) {
        note(reference, object, method, info);
        return null;
    }

    private void note(String reference, Object object, String asked, Info info) {
        String written = asWritten(info);
        // ${name|'value'} says what to use when the value is empty, so there is nothing to tell.
        if (written != null && written.startsWith("${") && written.contains("|")) {
            return;
        }
        String asWritten = written == null ? reference : written;
        String unknownStep = stepThatGaveNothing(asWritten);
        execution.noteMissingValue(asWritten + " has no value." + (unknownStep != null ? unknownStep : whatIsThere(object, asked)));
    }

    /** A wrong step name is the usual cause when a whole step is missing, so the note names the steps that are there. */
    private String stepThatGaveNothing(String written) {
        Matcher step = STEP_NAME.matcher(written);
        Map<String, ?> given = execution.getStepOutputs();
        if (!step.find() || given.containsKey(step.group(1))) {
            return null;
        }
        String names = given.keySet().stream().limit(MOST_FIELDS_NAMED).collect(Collectors.joining(", "));
        return " No step named " + step.group(1) + " has run before this one." + (given.isEmpty() ? "" : " The steps that have: " + names + ".");
    }

    /** The reference as the person wrote it, since Velocity gives back $steps["Get order"] as $steps.null. */
    private String asWritten(Info info) {
        if (info.getLine() < 1 || info.getLine() > lines.length) {
            return null;
        }
        String line = lines[info.getLine() - 1];
        int from = line.lastIndexOf('$', Math.min(info.getColumn(), line.length() - 1));
        if (from < 0) {
            return null;
        }
        int at = from + 1;
        if (at < line.length() && line.charAt(at) == '!') {
            at++;
        }
        boolean inBraces = at < line.length() && line.charAt(at) == '{';
        if (inBraces) {
            int close = endOfGroup(line, at, '{', '}');
            return line.substring(from, close);
        }
        while (at < line.length()) {
            char next = line.charAt(at);
            if (next == '[') {
                at = endOfGroup(line, at, '[', ']');
            } else if (next == '(') {
                at = endOfGroup(line, at, '(', ')');
            } else if (Character.isLetterOrDigit(next) || next == '_' || next == '-' || (next == '.' && at + 1 < line.length() && Character.isLetter(line.charAt(at + 1)))) {
                at++;
            } else {
                break;
            }
        }
        return line.substring(from, at);
    }

    /** The place after the bracket that closes the one at this place. Brackets inside quotes do not count. */
    private static int endOfGroup(String line, int open, char opening, char closing) {
        int depth = 0;
        char quote = 0;
        for (int at = open; at < line.length(); at++) {
            char next = line.charAt(at);
            if (quote != 0) {
                quote = next == quote ? 0 : quote;
            } else if (next == '"' || next == '\'') {
                quote = next;
            } else if (next == opening) {
                depth++;
            } else if (next == closing && --depth == 0) {
                return at + 1;
            }
        }
        return line.length();
    }

    private static String whatIsThere(Object object, String asked) {
        if (object instanceof Map<?, ?> fields && asked != null) {
            if (fields.containsKey(asked)) {
                return " The field " + asked + " is there and it is empty.";
            }
            if (fields.isEmpty()) {
                return " There are no fields where " + asked + " was looked for.";
            }
            String names = fields.keySet().stream().limit(MOST_FIELDS_NAMED).map(String::valueOf).collect(Collectors.joining(", "));
            return " There is no " + asked + " there. It has: " + names + (fields.size() > MOST_FIELDS_NAMED ? " and more." : ".");
        }
        if (object != null && asked != null) {
            // The method is there and gave nothing, which a tool does when it cannot read what it got.
            if (Arrays.stream(object.getClass().getMethods()).anyMatch(method -> method.getName().equals(asked))) {
                return " " + asked + " could not use the value that it got.";
            }
            return " The value before ." + asked + " is " + kindOf(object) + ", which has no " + asked + ".";
        }
        return "";
    }

    private static String kindOf(Object value) {
        if (value instanceof String) {
            return "text";
        }
        if (value instanceof Number) {
            return "a number";
        }
        if (value instanceof Iterable) {
            return "a list";
        }
        return "a " + value.getClass().getSimpleName();
    }
}
