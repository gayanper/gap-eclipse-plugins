package org.gap.eclipse.jdt.quickfixes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;

@SuppressWarnings("restriction")
public class StringFormatAsistProcessor implements IQuickAssistProcessor {
	private static final String CONVERT_TO_STRING_FORMAT_ID = "org.gap.eclipse.plugins.extras.fixes.asist.StringFormatAsistProcessor.assist"; //$NON-NLS-1$ ;
	
	@Override
	public boolean hasAssists(IInvocationContext context) throws CoreException {
		if(!isJavaVersionSatisfied(context))
			return false;
		
		return findProposals(context, true) != null;
	}

	@Override
	public IJavaCompletionProposal[] getAssists(IInvocationContext context, IProblemLocation[] locations)
			throws CoreException {
		if(!isJavaVersionSatisfied(context))
			return null;
		return findProposals(context, false);
	}

	private IJavaCompletionProposal[] findProposals(IInvocationContext context, boolean evalOnly) {
		ASTNode node = context.getCoveringNode();
		BodyDeclaration parentDecl = ASTHelper.findParentBodyDeclaration(node);
		if (!(parentDecl instanceof MethodDeclaration || parentDecl instanceof Initializer))
			return null;

		AST ast = node.getAST();
		ITypeBinding stringBinding = ast.resolveWellKnownType("java.lang.String"); //$NON-NLS-1$

		if (node instanceof Expression && !(node instanceof InfixExpression)) {
			node = node.getParent();
		}
		if (node instanceof VariableDeclarationFragment) {
			node = ((VariableDeclarationFragment) node).getInitializer();
		} else if (node instanceof Assignment) {
			node = ((Assignment) node).getRightHandSide();
		}

		InfixExpression oldInfixExpression = null;
		while (node instanceof InfixExpression) {
			InfixExpression curr = (InfixExpression) node;
			if (curr.resolveTypeBinding() == stringBinding && curr.getOperator() == InfixExpression.Operator.PLUS) {
				oldInfixExpression = curr; // is a infix expression we can use
			} else {
				break;
			}
			node = node.getParent();
		}
		if (oldInfixExpression == null)
			return null;
		
		if(evalOnly) {
			return new IJavaCompletionProposal[0];
		}
		
		return createProposalForStringFormat(context, ast, oldInfixExpression);
	}
	
	
	private IJavaCompletionProposal[] createProposalForStringFormat(IInvocationContext context, AST ast,
			InfixExpression oldInfixExpression) {
		final ICompilationUnit cu = context.getCompilationUnit();
		final ASTRewrite rewrite = ASTRewrite.create(ast);

		List<Expression> operands = new ArrayList<>();
		collectInfixPlusOperands(oldInfixExpression, operands);
		
		List<Expression> formatArguments = new ArrayList<>();
		StringBuilder formatString = new StringBuilder();
		for (Iterator<Expression> iterator = operands.iterator(); iterator.hasNext();) {
			Expression operand = iterator.next();

			if (operand instanceof StringLiteral) {
				String value = ((StringLiteral) operand).getEscapedValue();
				value = value.substring(1, value.length() - 1);
				value = value.replaceAll("'", "''");
				formatString.append(value);
			} else {
				formatString.append("%s");
				formatArguments.add((Expression) rewrite.createCopyTarget(operand));
			}
		}
		
		if(formatArguments.size() == 0) {
			return null;
		}
		
		ASTRewriteCorrectionProposal proposal = 
				new ASTRewriteCorrectionProposal("Use 'String.format' for string concatenation", cu, rewrite,
				0, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE));
		proposal.setCommandId(CONVERT_TO_STRING_FORMAT_ID);
	
		MethodInvocation formatInvocation = ast.newMethodInvocation();
		formatInvocation.setExpression(ast.newSimpleName("String"));
		formatInvocation.setName(ast.newSimpleName("format"));
		
		@SuppressWarnings("unchecked")
		List<Expression> arguments = formatInvocation.arguments();

		StringLiteral formatStringArgument = ast.newStringLiteral();
		formatStringArgument.setEscapedValue("\"" + formatString + "\""); //$NON-NLS-1$ //$NON-NLS-2$
		arguments.add(formatStringArgument);
		
		for (Iterator<Expression> iterator = formatArguments.iterator(); iterator.hasNext();) {
			arguments.add(iterator.next());
		}

		rewrite.replace(oldInfixExpression, formatInvocation, null);

		return new IJavaCompletionProposal[] {proposal};
	}

	private void collectInfixPlusOperands(Expression expression, List<Expression> collector) {
		if (expression instanceof InfixExpression
				&& ((InfixExpression) expression).getOperator() == InfixExpression.Operator.PLUS) {
			InfixExpression infixExpression = (InfixExpression) expression;

			collectInfixPlusOperands(infixExpression.getLeftOperand(), collector);
			collectInfixPlusOperands(infixExpression.getRightOperand(), collector);
			@SuppressWarnings("unchecked")
			List<Expression> extendedOperands = infixExpression.extendedOperands();
			for (Iterator<Expression> iter = extendedOperands.iterator(); iter.hasNext();) {
				collectInfixPlusOperands(iter.next(), collector);
			}

		} else {
			collector.add(expression);
		}
	}

	
	private boolean isJavaVersionSatisfied(IInvocationContext context)
	{
		IJavaProject project = context.getCompilationUnit().getJavaProject();
		String sourceLevel = project.getOption(JavaCore.COMPILER_SOURCE, true);
		return (JavaCore.compareJavaVersions(sourceLevel, JavaCore.VERSION_1_5) >= 0);
	}
}
