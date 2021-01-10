package org.gap.eclipse.jdt.types;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.ui.text.java.CompletionProposalCollector;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

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
		if(qualifier instanceof IMethod) {
			if(Flags.isStatic(method.getFlags())) {
				return new MethodRefCompletionProposal(method.getDeclaringType().getElementName(), method.getElementName(), 9998, context);
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
