package org.gap.eclipse.jdt.types;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.gap.eclipse.jdt.CorePlugin;
import org.gap.eclipse.jdt.common.Log;

public class SubTypeFinder {

	public Stream<ICompletionProposal> find(final IType expectedType, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor, Duration timeout) {
		return performSearch(expectedType, context, monitor, timeout)
				.map(m -> {
					try {
						return Proposals.toConstructorProposal(m, context);
					} catch (JavaModelException e) {
						Log.error(e);
						return null;
					}
				}).filter(Objects::nonNull);
	}

	private Stream<IMethod> performSearch(IType expectedType, JavaContentAssistInvocationContext context,
			IProgressMonitor monitor, Duration timeout) {
		final List<IMethod> resultAccumerlator = Collections.synchronizedList(new ArrayList<>());

		final ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<?> task = executor.submit(() -> {
			try {
				IType[] subtypes = expectedType.newTypeHierarchy(monitor).getAllSubtypes(expectedType);
				Set<IMethod> constructors = Stream.of(subtypes).filter(t -> {
					try {
						return !Flags.isAbstract(t.getFlags()) && Flags.isPublic(t.getFlags());
					} catch (JavaModelException e) {
						Log.error(e);
						return false;
					}
				}).flatMap(t -> {
					try {
						return Stream.of(t.getMethods()).filter(m -> {
							try {
								return m.isConstructor();
							} catch (JavaModelException e) {
								Log.error(e);
								return false;
							}
						});
					} catch (JavaModelException e) {
						Log.error(e);
						return Stream.empty();
					}
				}).collect(Collectors.toSet());
				resultAccumerlator.addAll(constructors);
			} catch (CoreException e) {
				CorePlugin.getDefault().logError(e.getMessage(), e);
			}
		});

		try {
			if (timeout != null) {
				task.get(timeout.getSeconds(), TimeUnit.SECONDS);
			} else {
				task.get();
			}
		} catch (TimeoutException e) {
			// do nothing since we return what we have collected so far.
		} catch (Exception e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		} finally {
			executor.shutdownNow();
		}
		return new ArrayList<>(resultAccumerlator).stream(); // copy and create the stream.
	}
}
