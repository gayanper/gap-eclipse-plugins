package org.gap.eclipse.jdt.types;

import static org.eclipse.jdt.internal.codeassist.RelevanceConstants.*;

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
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.codeassist.impl.AssistOptions;
import org.eclipse.jdt.internal.ui.text.java.LazyGenericTypeProposal;
import org.eclipse.jdt.internal.ui.text.java.LazyJavaTypeCompletionProposal;
import org.eclipse.jdt.ui.text.java.CompletionProposalCollector;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.gap.eclipse.jdt.CorePlugin;

@SuppressWarnings({ "restriction" })
public final class Proposals {
	private static final int R_METHOD_REF = 9998;
	private static final int R_LAMBDA = 10000;

	private Proposals() {
	}

	static Stream<ICompletionProposal> toLambdaProposal(IMethod method, JavaContentAssistInvocationContext context)
			throws JavaModelException {
		final String[] parameterNames = method.getParameterNames();
		ICompletionProposal[] proposals = new ICompletionProposal[2];

		int relevance = R_LAMBDA;
		if (hasToken(context)) {
			relevance = R_DEFAULT;
		}

		proposals[0] = new LambdaCompletionProposal(parameterNames, true, relevance, context);

		proposals[1] = new LambdaCompletionProposal(parameterNames, false, --relevance, context);

		return Stream.of(proposals);
	}

	static ICompletionProposal toMethodReferenceProposal(IJavaElement qualifier, IMethod method,
			JavaContentAssistInvocationContext context, AssistOptions assistOptions) throws JavaModelException {
		MethodRefCompletionProposal proposal;
		if (qualifier instanceof IMethod) {
			if (Flags.isStatic(method.getFlags())) {
				proposal = new MethodRefCompletionProposal(method.getDeclaringType().getElementName(),
						method.getElementName(), getToken(context), context);
			} else {
				proposal = new MethodRefCompletionProposal("this", method.getElementName(), getToken(context), context);
			}
		} else {
			proposal = new MethodRefCompletionProposal(qualifier.getElementName(), method.getElementName(),
					getToken(context), context);
		}
		proposal.setRelevance(
				computeMethodRefRelavance(proposal.getDisplayString(), context, assistOptions));
		proposal.setMatchRule(deriveMatchRule(proposal.getRelevance()));

		return proposal;
	}

	private static int deriveMatchRule(int relevance) {
		switch (relevance) {
			case R_DEFAULT:
			case R_METHOD_REF:
				return -1;
			default: {
				switch(relevance - R_METHOD_REF) {
					case R_CAMEL_CASE:
						return SearchPattern.R_CAMELCASE_MATCH;
					case R_SUBSTRING:
						return SearchPattern.R_SUBSTRING_MATCH;
					case R_SUBWORD:
						return SearchPattern.R_SUBWORD_MATCH;
					default:
						return -1;
						
					}
			}
		}
	}

	private static int computeMethodRefRelavance(String proposalName, JavaContentAssistInvocationContext context,
			AssistOptions options) {
		if (hasToken(context)) {
			char[] token = context.getCoreContext().getToken();
			String tokenStr = String.valueOf(token);

			if (options.camelCaseMatch && CharOperation.camelCaseMatch(token, proposalName.toCharArray())) {
				return R_METHOD_REF + R_CAMEL_CASE;
			} else if (options.substringMatch && proposalName.startsWith(tokenStr)) {
				return R_METHOD_REF + R_SUBSTRING;
			} else if (options.subwordMatch && proposalName.contains(tokenStr)) {
				return R_METHOD_REF + R_SUBWORD;
			} else {
				return R_DEFAULT;
			}
		}
		return R_METHOD_REF;
	}

	static String getToken(JavaContentAssistInvocationContext context) {
		if (hasToken(context)) {
			return String.valueOf(context.getCoreContext().getToken());
		} else {
			return "";
		}
	}

	private static boolean hasToken(JavaContentAssistInvocationContext context) {
		return context.getCoreContext() != null && context.getCoreContext().getToken() != null
				&& context.getCoreContext().getToken().length > 0;
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
