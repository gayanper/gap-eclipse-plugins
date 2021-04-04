package org.gap.eclipse.jdt.common;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DistinctPredicate<T, K> implements Predicate<T> {
	private Set<K> keys = new HashSet<>();
	private Function<T, K> keyFunc;

	private DistinctPredicate(Function<T, K> keyFunc) {
		this.keyFunc = keyFunc;
	}

	public static <T, K> Predicate<T> distinct(Function<T, K> keyFunc) {
		return new DistinctPredicate<>(keyFunc);
	}

	@Override
	public boolean test(T t) {
		return this.keys.add(this.keyFunc.apply(t));
	}
}
