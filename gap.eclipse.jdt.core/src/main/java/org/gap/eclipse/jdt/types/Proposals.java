package org.gap.eclipse.jdt.types;

import java.util.stream.Stream;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaElement;
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
}
