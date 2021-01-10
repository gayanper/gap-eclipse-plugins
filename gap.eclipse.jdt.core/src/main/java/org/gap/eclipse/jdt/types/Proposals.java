package org.gap.eclipse.jdt.types;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.ui.text.java.LazyGenericTypeProposal;
import org.eclipse.jdt.internal.ui.text.java.LazyJavaTypeCompletionProposal;
import org.eclipse.jdt.ui.text.java.CompletionProposalCollector;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.gap.eclipse.jdt.CorePlugin;

@SuppressWarnings({ "restriction" })
public final class Proposals {
	private Proposals() {
	}

	static Stream<ICompletionProposal> toLambdaProposal(IMethod method, JavaContentAssistInvocationContext context)
			throws JavaModelException {
		final String[] parameterNames = method.getParameterNames();
		ICompletionProposal[] proposals = new ICompletionProposal[2];

		proposals[0] = new LambdaCompletionProposal(parameterNames, true, 10000, context);

		proposals[1] = new LambdaCompletionProposal(parameterNames, false, 9999, context);

		return Stream.of(proposals);
	}

	static ICompletionProposal toMethodReferenceProposal(IJavaElement qualifier, IMethod method,
			JavaContentAssistInvocationContext context) throws JavaModelException {
		if (qualifier instanceof IMethod) {
			if (Flags.isStatic(method.getFlags())) {
				return new MethodRefCompletionProposal(method.getDeclaringType().getElementName(),
						method.getElementName(), 9998, context);
			}
			return new MethodRefCompletionProposal("this", method.getElementName(), 9998, context);
		}
		return new MethodRefCompletionProposal(qualifier.getElementName(), method.getElementName(), 9998, context);
	}

	static ICompletionProposal toMethodProposal(IMethod method, JavaContentAssistInvocationContext context)
			throws JavaModelException {

		CompletionProposal proposal = CompletionProposal.create(CompletionProposal.METHOD_REF,
				context.getInvocationOffset());
		String fullyQualifiedName = method.getDeclaringType().getElementName().concat(".")
				.concat(method.getElementName());
		proposal.setName(method.getElementName().toCharArray());
		proposal.setCompletion(createMethodCompletion(method));
		proposal.setDeclarationSignature(
				Signature.createTypeSignature(method.getDeclaringType().getFullyQualifiedName(), true).toCharArray());
		proposal.setFlags(method.getFlags());
		float relevance = context.getHistoryRelevance(fullyQualifiedName);
		proposal.setRelevance((int) (100 * (relevance < 0.1 ? 0.1 : relevance)));
		proposal.setReplaceRange(ContextUtils.computeInvocationOffset(context), ContextUtils.computeEndOffset(context));
		proposal.setSignature(method.getSignature().replaceAll("/", ".").toCharArray());
		proposal.setRequiredProposals(
				new CompletionProposal[] { createImportProposal(context, method.getDeclaringType()) });

		CompletionProposalCollector collector = new CompletionProposalCollector(context.getCompilationUnit());
		collector.setInvocationContext(context);
		collector.acceptContext(context.getCoreContext());
		collector.accept(proposal);

		return collector.getJavaCompletionProposals()[0];
	}

	static ICompletionProposal toConstructorProposal(IMethod method, JavaContentAssistInvocationContext context)
			throws JavaModelException {

		CompletionProposal proposal = CompletionProposal.create(CompletionProposal.CONSTRUCTOR_INVOCATION,
				context.getInvocationOffset());
		String fullyQualifiedName = method.getDeclaringType().getElementName().concat(".")
				.concat(method.getElementName());
		proposal.setName(method.getElementName().toCharArray());
		proposal.setCompletion(createMethodCompletion(method));
		proposal.setDeclarationSignature(
				Signature.createTypeSignature(method.getDeclaringType().getFullyQualifiedName(), true).toCharArray());
		proposal.setFlags(method.getFlags());
		float relevance = context.getHistoryRelevance(fullyQualifiedName);
		proposal.setRelevance((int) (100 * (relevance < 0.1 ? 0.1 : relevance)));
		proposal.setReplaceRange(ContextUtils.computeInvocationOffset(context), ContextUtils.computeEndOffset(context));
		proposal.setSignature(method.getSignature().replaceAll("/", ".").toCharArray());
		proposal.setRequiredProposals(
				new CompletionProposal[] { createTypeProposal(method.getDeclaringType(), context) });

		CompletionProposalCollector collector = new CompletionProposalCollector(context.getCompilationUnit());
		collector.setInvocationContext(context);
		collector.acceptContext(context.getCoreContext());
		collector.accept(proposal);

		return collector.getJavaCompletionProposals()[0];
	}

	static ICompletionProposal toFieldProposal(IField field, JavaContentAssistInvocationContext context)
			throws JavaModelException {

		CompletionProposal proposal = CompletionProposal.create(CompletionProposal.FIELD_REF,
				context.getInvocationOffset());
		String fullyQualifiedName = field.getDeclaringType().getElementName().concat(".")
				.concat(field.getElementName());
		proposal.setName(field.getElementName().toCharArray());
		proposal.setCompletion(fullyQualifiedName.toCharArray());
		proposal.setDeclarationSignature(
				Signature.createTypeSignature(field.getDeclaringType().getFullyQualifiedName(), true).toCharArray());
		proposal.setFlags(field.getFlags());
		float relevance = context.getHistoryRelevance(fullyQualifiedName);
		proposal.setRelevance((int) (50 * (relevance < 0.1 ? 0.1 : relevance)));
		proposal.setReplaceRange(context.getInvocationOffset(), ContextUtils.computeEndOffset(context));
		proposal.setSignature(field.getTypeSignature().toCharArray());
		proposal.setRequiredProposals(
				new CompletionProposal[] { createImportProposal(context, field.getDeclaringType()) });

		CompletionProposalCollector collector = new CompletionProposalCollector(context.getCompilationUnit());
		collector.setInvocationContext(context);
		collector.acceptContext(context.getCoreContext());
		collector.accept(proposal);

		return collector.getJavaCompletionProposals()[0];
	}

	static ICompletionProposal toTypeProposal(IType type, JavaContentAssistInvocationContext context,
			boolean array, boolean initialize) {
		try {
			CompletionProposal proposal = createTypeProposal(type, context);

			if (array) {
				return new LazyArrayJavaTypeProposal(proposal, context, initialize);
			}

			if (supportGenerics(context.getProject())) {
				return new LazyGenericTypeProposal(proposal, context);
			} else {
				return new LazyJavaTypeCompletionProposal(proposal, context);
			}
		} catch (JavaModelException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		}
		return null;
	}

	private static CompletionProposal createTypeProposal(IType type, JavaContentAssistInvocationContext context)
			throws JavaModelException {
		CompletionProposal proposal = CompletionProposal.create(CompletionProposal.TYPE_REF,
				context.getInvocationOffset());
		String fullyQualifiedName = type.getFullyQualifiedName();
		proposal.setCompletion(fullyQualifiedName.toCharArray());
		proposal.setDeclarationSignature(type.getPackageFragment().getElementName().toCharArray());
		proposal.setFlags(type.getFlags());
		float relevance = context.getHistoryRelevance(fullyQualifiedName);
		proposal.setRelevance((int) (1000 * (relevance < 0.1 ? 0.1 : relevance)));
		proposal.setReplaceRange(context.getInvocationOffset(), ContextUtils.computeEndOffset(context));
		proposal.setSignature(Signature.createTypeSignature(fullyQualifiedName, true).toCharArray());
		return proposal;
	}

	private static boolean supportGenerics(IJavaProject project) {
		String source = project.getOption(JavaCore.COMPILER_SOURCE, true);
		return source != null && JavaCore.compareJavaVersions(JavaCore.VERSION_1_5, source) <= 0;
	}

	private static CompletionProposal createImportProposal(JavaContentAssistInvocationContext context, IType type)
			throws JavaModelException {
		CompletionProposal proposal = CompletionProposal.create(CompletionProposal.TYPE_IMPORT,
				context.getInvocationOffset());
		String fullyQualifiedName = type.getFullyQualifiedName();
		proposal.setCompletion(fullyQualifiedName.toCharArray());
		proposal.setDeclarationSignature(type.getPackageFragment().getElementName().toCharArray());
		proposal.setFlags(type.getFlags());
		proposal.setSignature(Signature.createTypeSignature(fullyQualifiedName, true).toCharArray());

		return proposal;
	}

	private static char[] createMethodCompletion(IMethod method) throws JavaModelException {
		StringBuilder builder = new StringBuilder();
		builder.append(method.getDeclaringType().getElementName());
		builder.append(".").append(method.getElementName());
		builder.append("(");
		builder.append(Arrays.stream(method.getParameterNames()).collect(Collectors.joining(",")));
		builder.append(")");
		return builder.toString().toCharArray();
	}

}
