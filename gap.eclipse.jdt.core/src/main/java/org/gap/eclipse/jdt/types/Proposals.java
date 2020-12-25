package org.gap.eclipse.jdt.types;

import java.util.StringJoiner;
import java.util.stream.Stream;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

public final class Proposals {
	private Proposals() {
	}

	static Stream<ICompletionProposal> toLambdaProposal(IMethod method, JavaContentAssistInvocationContext context)
			throws JavaModelException {
		final String[] parameterNames = method.getParameterNames();
		StringBuilder completionString = new StringBuilder(4 + parameterNames.length);

		if (parameterNames.length != 1) {
			completionString.append("(");
		}

		StringJoiner joiner = new StringJoiner(",");
		Stream.of(parameterNames).forEach(joiner::add);
		completionString.append(joiner.toString());

		if (parameterNames.length != 1) {
			completionString.append(")");
		}
		completionString.append(" -> ");

		ICompletionProposal[] proposals = new ICompletionProposal[2];

		proposals[0] = new LambdaCompletionProposal(context.getInvocationOffset(),
				ContextUtils.computeReplacementLength(context), completionString.toString(), completionString.length(),
				10000);

		completionString.append("{}");

		proposals[1] = new LambdaCompletionProposal(context.getInvocationOffset(),
				ContextUtils.computeReplacementLength(context), completionString.toString(),
				completionString.length() - 1, 9999);

		return Stream.of(proposals);
	}
}
