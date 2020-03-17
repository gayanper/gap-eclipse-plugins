package org.gap.eclipse.jdt.types;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.gap.eclipse.jdt.CorePlugin;

import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;

class CompletionASTVistor extends ASTVisitor {
	private int offset;
	private Set<IType> expectedTypes;
	private IJavaProject project;
	private boolean preceedSpace = false;

	private CodeRange lastVisited;

	public CompletionASTVistor(JavaContentAssistInvocationContext context) {
		this.expectedTypes = new HashSet<>();
		this.offset = context.getInvocationOffset();
		this.project = context.getProject();
		try {
			preceedSpace = context.getDocument().get(offset - 1, 1).equals(" ");
		} catch (BadLocationException e) {
			CorePlugin.getDefault().logError(e.getMessage(), e);
		}
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		return visitNode(node, Suppliers.memoize(node::arguments), method -> Arrays.asList(method.getParameterTypes()),
				Suppliers.memoize(node::resolveConstructorBinding));
	}

	@Override
	public boolean visit(MethodInvocation node) {
		return visitNode(node, Suppliers.memoize(node::arguments), method -> Arrays.asList(method.getParameterTypes()),
				Suppliers.memoize(node::resolveMethodBinding));
	}

	private boolean visitNode(ASTNode node, Supplier<List<ASTNode>> argumentSupplier,
			Function<IMethodBinding, List<ITypeBinding>> parameterSupplier, Supplier<IMethodBinding> bindingSupplier) {
		final CodeRange current = new CodeRange(node.getStartPosition(), node.getStartPosition() + node.getLength(),
				node);

		// for varargs they are considered as single parameter methods with a array.
		int varArgsOffset = preceedSpace ? 2 : 1;
		final IMethodBinding methodBinding = bindingSupplier.get();
		if (methodBinding == null) {
			return false;
		}

		offset = methodBinding.isVarargs() ? offset - varArgsOffset
				: offset - (argumentSupplier.get().isEmpty() ? 0 : preceedSpace ? 2 : 1);
		if (current.inRange(offset) && (lastVisited == null || lastVisited.inRange(current))) {
			Set<ITypeBinding> typesAtOffset = findParameterTypeAtOffset(argumentSupplier, parameterSupplier, bindingSupplier);
			typesAtOffset.stream()
			.map(t -> {
				try {
					return project.findType(Signature.getTypeErasure(t.getQualifiedName()));
				} catch (JavaModelException | IllegalArgumentException e) {
					CorePlugin.getDefault().logError(e.getMessage(), e);
					return null;
				}
			})
			.filter(Predicates.notNull())
			.forEach(t -> expectedTypes.add(t));
			lastVisited = current;
			return true;
		}
		return false;
	}

	private Set<ITypeBinding> findParameterTypeAtOffset(Supplier<List<ASTNode>> argumentSupplier,
			Function<IMethodBinding, List<ITypeBinding>> parameterSupplier, Supplier<IMethodBinding> bindingSupplier) {
		final List<ASTNode> arguments = argumentSupplier.get();
		final IMethodBinding binding = bindingSupplier.get();

		if (binding == null) {
			return Collections.emptySet();
		}

		List<ITypeBinding> parameters = parameterSupplier.apply(binding);
		if (arguments.isEmpty()) {
			if (!parameters.isEmpty()) {
				if (binding.isVarargs()) {
					return Sets.newHashSet(parameters.get(0).getElementType());
				} else {
					return Sets.newHashSet(parameters.get(0));
				}
			}
		} else {
			int typeIndex = -1;
			int checkOffset = preceedSpace ? offset - 1 : offset;

			for (int i = 0; i < arguments.size(); i++) {
				final ASTNode astNode = arguments.get(i);
				if (astNode.getStartPosition() <= checkOffset
						&& (astNode.getStartPosition() + astNode.getLength()) >= checkOffset) {
					typeIndex = i + 1;
					break;
				}
			}

			if(binding != null) {
				if ((typeIndex > -1) && typeIndex < parameters.size()) {
					return Sets.newHashSet(parameters.get(typeIndex));
				} else if (typeIndex == parameters.size()) {
					// This might mean we are at a overloaded method. So lets look for overloads.
					return findFromOverloaded(typeIndex, binding);
				}
			}
		}
		return Collections.emptySet();
	}

	private Set<ITypeBinding> findFromOverloaded(int typeIndex, IMethodBinding binding) {
		ITypeBinding type = binding.getDeclaringClass();
     	return Arrays.stream(type.getDeclaredMethods())
     		.filter(m -> {
     			return m.getName().equals(binding.getName()) && !Modifier.isPrivate(m.getModifiers());
     		})
     		.filter(m -> m.getParameterTypes().length >= typeIndex + 1)
     		.map(m -> m.getParameterTypes()[typeIndex])
     		.collect(Collectors.toSet());
	}

	public Set<IType> getExpectedTypes() {
		return expectedTypes;
	}
	
	public IType getExpectedType() {
		Iterator<IType> iterator = expectedTypes.iterator();
		if(iterator.hasNext()) {
			return iterator.next();
		}
		return null;
	}
	
}