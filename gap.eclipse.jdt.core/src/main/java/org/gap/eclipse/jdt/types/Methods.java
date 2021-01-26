package org.gap.eclipse.jdt.types;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;

public final class Methods {
	private static List<Entry<String, String>> methodSignaturesToIgnore = List.of(
			Map.entry("equals", "(Ljava/lang/Object;)Z"), Map.entry("wait", "()V"), Map.entry("wait", "(JI)V"),
			Map.entry("wait", "(J)V"));

	private Methods() {
	}

	public static boolean notIgnoredMethod(IMethod method) throws JavaModelException {
		String name = method.getElementName();
		String signature = method.getSignature();
		return methodSignaturesToIgnore.stream()
				.noneMatch(e -> e.getKey().equals(name) && e.getValue().equals(signature));
	}

}
