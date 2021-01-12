package org.gap.eclipse.jdt.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.gap.eclipse.jdt.common.Log;

public class Java8ProposalComputer extends AbstractSmartProposalComputer implements IJavaCompletionProposalComputer {
	private List<Entry<String, String>> methodSignaturesToIgnore;

	private MethodReferenceFinder methodReferenceFinder;

	public Java8ProposalComputer() {
		this.methodSignaturesToIgnore = List.of(Map.entry("equals", "(Ljava/lang/Object;)Z"), Map.entry("wait", "()V"),
				Map.entry("wait", "(JI)V"), Map.entry("wait", "(J)V"));
		this.methodReferenceFinder = new MethodReferenceFinder();
	}

	@Override
	protected List<ICompletionProposal> computeSmartCompletionProposals(JavaContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		if (isPreceedSpaceNewKeyword(context)) {
			return Collections.emptyList();
		}

		if (context.getExpectedType() != null) {
			return computeJava8Proposals(Set.of(context.getExpectedType()), context);
		} else {
			final ASTResult result = findInAST(context, monitor);
			return computeJava8Proposals(result.getExpectedTypes(), context);
		}
	}

	private List<ICompletionProposal> computeJava8Proposals(Set<IType> types,
			JavaContentAssistInvocationContext context) {
		return types.stream().flatMap(this::functionalTypeMethod).flatMap(m -> toLambdaProposal(context, m))
				.collect(Collectors.toList());
	}

	private Stream<? extends ICompletionProposal> toLambdaProposal(JavaContentAssistInvocationContext context,
			IMethod method) {
		try {
			List<IJavaElement> elements = new ArrayList<>(List.of(Optional.ofNullable(context.getCoreContext())
					.map(c -> c.getVisibleElements(null)).orElse(new IJavaElement[0])));
			elements.addAll(resolveInbuiltSuggestions(context));

			@NonNull
			Stream<Entry<IJavaElement, IMethod>> methodReferences = methodReferenceFinder.find(method,
					elements,
					context);

			return Stream.concat(methodReferences.map(e -> {
				try {
					return Proposals.toMethodReferenceProposal(e.getKey(), e.getValue(), context);
				} catch (JavaModelException ex) {
					Log.error(ex);
					return null;
				}
			}).filter(Objects::nonNull), Proposals.toLambdaProposal(method, context));
		} catch (JavaModelException e) {
			logError(e);
			return Stream.empty();
		}
	}

	private Stream<IMethod> functionalTypeMethod(IType type) {
		try {
			if (!type.isInterface()) {
				return Stream.empty();
			}

			List<IMethod> candidates = Stream.of(type.getMethods()).filter(m -> {
				try {
					return !Flags.isStatic(m.getFlags()) && !Flags.isDefaultMethod(m.getFlags()) && notIgnoredMethod(m);
				} catch (JavaModelException e) {
					logError(e);
					return false;
				}
			}).collect(Collectors.toList());

			if (candidates.size() == 1) {
				return candidates.stream();
			}

			return Stream.empty();
		} catch (JavaModelException e) {
			logError(e);
			return Stream.empty();
		}
	}

	private boolean notIgnoredMethod(IMethod method) throws JavaModelException {
		String name = method.getElementName();
		String signature = method.getSignature();
		return this.methodSignaturesToIgnore.stream()
				.noneMatch(e -> e.getKey().equals(name) && e.getValue().equals(signature));
	}

	private List<IType> resolveInbuiltSuggestions(JavaContentAssistInvocationContext context) {
		return InBuiltSuggestion.getMethodReferenceTypeSuggestions().stream().map(t -> {
			try {
				return context.getProject().findType(t);
			} catch (JavaModelException ex) {
				Log.error(ex);
			}
			return null;
		}).filter(Objects::nonNull).collect(Collectors.toList());
	}
}