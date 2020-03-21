package org.gap.eclipse.jdt.debug;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILogicalStructureType;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JsonGenerator {
	private Set<String> terminalTypes = ImmutableSet.of("java.lang.String");

	public JsonObject construct(IJavaVariable variable) throws CoreException {
		JsonObject jsonObject = new JsonObject();
		dumpToJson(Iterators.forArray(variable), jsonObject);
		return jsonObject;
	}

	private void dumpToJson(final Iterator<IVariable> variables, final JsonObject json) throws CoreException {
		final Deque<IterationEntry> entryStack = new ArrayDeque<>();
		Iterator<IVariable> runningIterator = variables;
		JsonObject runningJson = json;

		while (runningIterator.hasNext()) {
			IJavaVariable variable = (IJavaVariable) runningIterator.next();
			IValue value = logicalValue(variable.getValue());

			if (isTerminalType(value) || isArray(value)) {
				if (isTerminalType(value)) {
					runningJson.addProperty(variable.getName(), value.getValueString());
				} else {
					final IJavaValue[] values = ((IJavaArray) value).getValues();
					runningJson.add(variable.getName(), createJsonArray(values));
				}

				if (!runningIterator.hasNext()) {
					if (entryStack.isEmpty()) {
						break;
					}

					// pop the stack
					IterationEntry entry = entryStack.pop();
					runningIterator = entry.variables;
					runningJson = entry.json;
				}
			} else {
				// complex variable push to stack and continue
				entryStack.push(IterationEntry.entry(runningIterator, runningJson));
				runningIterator = Iterators.forArray(value.getVariables());
				runningJson = new JsonObject();
				entryStack.peek().json.add(variable.getName(), runningJson);
			}
		}
	}

	private IValue logicalValue(IValue value) throws CoreException {
		ILogicalStructureType[] types = DebugPlugin.getLogicalStructureTypes(value);
		if (types.length > 0) {
			ILogicalStructureType type = DebugPlugin.getDefaultStructureType(types);
			if (type != null) {
				return type.getLogicalStructure(value);
			}
		}

		return value;
	}

	private JsonElement createJsonArray(IJavaValue[] values) throws CoreException {
		JsonArray array = new JsonArray();
		for (IJavaValue value : values) {
			if (isTerminalType(value)) {
				array.add(value.getValueString());
			} else if (isArray(value)) {
				array.add(createJsonArray(((IJavaArray) value).getValues()));
			} else {
				JsonObject innerObject = new JsonObject();
				array.add(innerObject);
				for (IVariable variable : value.getVariables()) {
					dumpToJson(Iterators.forArray((IJavaVariable) variable), innerObject);
				}
			}
		}

		return array;
	}

	private boolean isTerminalType(IValue value) throws DebugException {
		if (value instanceof IJavaPrimitiveValue) {
			return true;
		} else {
			return terminalTypes.contains(value.getReferenceTypeName());
		}
	}

	private boolean isArray(IValue value) {
		return value instanceof IJavaArray;
	}

	public static class IterationEntry {
		public Iterator<IVariable> variables;

		public JsonObject json;

		private IterationEntry() {
		}

		public static IterationEntry entry(Iterator<IVariable> variables, JsonObject json) {
			IterationEntry entry = new IterationEntry();
			entry.variables = variables;
			entry.json = json;
			return entry;
		}
	}
}
