package org.gap.eclipse.jdt.types;

import java.util.ArrayList;
import java.util.Collection;
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
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.internal.codeassist.impl.AssistOptions;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.gap.eclipse.jdt.common.Log;

@SuppressWarnings("restriction")
public class Java8ProposalComputer extends AbstractSmartProposalComputer implements IJavaCompletionProposalComputer {
	private List<Entry<String, String>> methodSignaturesToIgnore;

	private MethodReferenceFinder methodReferenceFinder;

	private AssistOptions assistOptions;

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

		assistOptions = getAssistOptions(context);
		final Set<String> expectedTypes = resolveExpectedTypes(context);

		if (!expectedTypes.isEmpty()) {
			List<IBinding> bindings = resolveBindings(resolveTypesFromProject(expectedTypes, context, monitor),
					context, monitor);
			if(bindings.isEmpty()) {
				return Collections.emptyList();
			}
			
			return computeJava8Proposals(bindings, context);
		} else {
			final ASTResult result = findInAST(context, monitor);
			return computeJava8Proposals(result.getExpectedTypeBindings(), context);
		}
	}

	@Override
	public void sessionEnded() {
		super.sessionEnded();
		this.assistOptions = null;
	}

	private List<ICompletionProposal> computeJava8Proposals(Collection<? extends IBinding> bindings,
			JavaContentAssistInvocationContext context) {
		return bindings.stream().filter(b -> b instanceof ITypeBinding).map(ITypeBinding.class::cast)
				.flatMap(this::functionalTypeMethod).flatMap(e -> toLambdaProposal(context, e))
				.collect(Collectors.toList());
	}

	private Stream<? extends ICompletionProposal> toLambdaProposal(JavaContentAssistInvocationContext context,
			IMethodBinding binding) {
		try {
			List<IJavaElement> elements = new ArrayList<>(List.of(Optional.ofNullable(context.getCoreContext())
					.map(c -> c.getVisibleElements(null)).orElse(new IJavaElement[0])));
			elements.addAll(resolveInbuiltSuggestions(context));

			@NonNull
			Stream<Entry<IJavaElement, IMethod>> methodReferences = !isPreceedMethodReferenceOpt(context)
					? methodReferenceFinder.find(binding, elements, context)
					: Stream.empty();

			return Stream.concat(methodReferences.map(e -> {
				try {
					return Proposals.toMethodReferenceProposal(e.getKey(), e.getValue(), context, assistOptions);
				} catch (JavaModelException ex) {
					Log.error(ex);
					return null;
				}
			}).filter(Objects::nonNull), Proposals.toLambdaProposal((IMethod) binding.getJavaElement(), context));
		} catch (JavaModelException e) {
			logError(e);
			return Stream.empty();
		}
	}

	private Stream<IMethodBinding> functionalTypeMethod(ITypeBinding binding) {
		if (!binding.isInterface()) {
			return Stream.empty();
		}

		List<IMethodBinding> candidates = Stream.of(binding.getDeclaredMethods()).filter(m -> {
			try {
				return !Modifier.isStatic(m.getModifiers()) && !Modifier.isDefault(m.getModifiers())
						&& notIgnoredMethod(m);
			} catch (JavaModelException e) {
				logError(e);
				return false;
			}
		}).collect(Collectors.toList());

		if (candidates.size() == 1) {
			return candidates.stream();
		}
		return Stream.empty();
	}

	private boolean notIgnoredMethod(IMethodBinding method) throws JavaModelException {
		String name = method.getName();
		String signature = ((IMethod) method.getJavaElement()).getSignature();
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