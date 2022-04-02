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
import org.eclipse.core.runtime.Platform;
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
import org.gap.eclipse.jdt.common.DistinctPredicate;
import org.gap.eclipse.jdt.common.Log;
import org.osgi.framework.Version;

import com.google.common.base.Preconditions;

@SuppressWarnings("restriction")
public class Java8ProposalComputer extends AbstractSmartProposalComputer implements IJavaCompletionProposalComputer {
	private List<Entry<String, String>> methodSignaturesToIgnore;

	private MethodReferenceFinder methodReferenceFinder;

	private AssistOptions assistOptions;

	private boolean enableLambda;

	private static final Version VERSION_FOR_LAMBDA = new Version(4, 22, 0);

	public Java8ProposalComputer() {
		this.methodSignaturesToIgnore = List.of(Map.entry("equals", "(Ljava/lang/Object;)Z"), Map.entry("wait", "()V"),
				Map.entry("wait", "(JI)V"), Map.entry("wait", "(J)V"));
		this.methodReferenceFinder = new MethodReferenceFinder();
		enableLambda = Platform.getBundle("org.eclipse.platform").getVersion().compareTo(VERSION_FOR_LAMBDA) <= 0;
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
			// can remove after fixing https://bugs.eclipse.org/bugs/show_bug.cgi?id=572359
			final ASTResult result = findInAST(context, monitor);
			if (!result.isInsideLambda()) {
				return computeJava8Proposals(result.getExpectedTypeBindings(), context);
			}
			return Collections.emptyList();
		}
	}

	@Override
	public void sessionEnded() {
		super.sessionEnded();
		this.assistOptions = null;
	}

	private List<ICompletionProposal> computeJava8Proposals(Collection<? extends IBinding> bindings,
			JavaContentAssistInvocationContext context) {
		return bindings.stream().filter(ITypeBinding.class::isInstance).map(ITypeBinding.class::cast)
				.map(this::functionalTypeMethod)
				.filter(Objects::nonNull)
				.filter(DistinctPredicate.<IMethodBinding, Integer>distinct(m -> m.getParameterTypes().length))
				.flatMap(e -> toLambdaProposal(context, e))
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
			}).filter(Objects::nonNull), 
					enableLambda ? Proposals.toLambdaProposal((IMethod) binding.getJavaElement(), context): Stream.empty());
		} catch (JavaModelException e) {
			logError(e);
			return Stream.empty();
		}
	}

	private IMethodBinding functionalTypeMethod(ITypeBinding binding) {
		if (binding.isInterface()) {
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
				return candidates.get(0);
			}
		}
		return null;
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