package org.gap.eclipse.jdt.types;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
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
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
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

		final IMethodBinding methodBinding = bindingSupplier.get();
		if (methodBinding == null) {
			return false;
		}

		if (current.inRange(offset) && (lastVisited == null || lastVisited.inRange(current))) {
			Set<ITypeBinding> typesAtOffset = findParameterTypeAtOffset(argumentSupplier, parameterSupplier,
					bindingSupplier, getDeclaringType(node));
			typesAtOffset.stream().map(t -> {
				try {
					return project.findType(Signature.getTypeErasure(t.getQualifiedName()));
				} catch (JavaModelException | IllegalArgumentException e) {
					CorePlugin.getDefault().logError(e.getMessage(), e);
					return null;
				}
			}).filter(Predicates.notNull()).forEach(t -> expectedTypes.add(t));
			lastVisited = current;
			return true;
		}
		return false;
	}

	private Set<ITypeBinding> findParameterTypeAtOffset(Supplier<List<ASTNode>> argumentSupplier,
			Function<IMethodBinding, List<ITypeBinding>> parameterSupplier, 
			Supplier<IMethodBinding> bindingSupplier, ITypeBinding containerType) {
		final List<ASTNode> arguments = argumentSupplier.get();
		final IMethodBinding binding = bindingSupplier.get();

		if (binding == null) {
			return Collections.emptySet();
		}

		List<ITypeBinding> parameters = parameterSupplier.apply(binding);
		if (arguments.isEmpty()) {
			if (!parameters.isEmpty()) {
				return Sets.newHashSet(resolveType(parameters.get(0)));
			}
		} else {
			int typeIndex = -1;
			int checkOffset = preceedSpace ? offset - 1 : offset;
			ITypeBinding lastType = null;

			for (int i = 0; i < arguments.size(); i++) {
				final ASTNode astNode = arguments.get(i);
				if (astNode.getStartPosition() <= checkOffset
						&& (astNode.getStartPosition() + astNode.getLength()) >= checkOffset) {
					typeIndex = i;
					break;
				}
				lastType = resolveTypeBinding(astNode);
			}

			if (binding != null) {
				final int pIndex = typeIndex;
				final ITypeBinding lType = lastType;
				List<IMethodBinding> overloads = findFromOverloaded(binding, containerType);
				if (overloads.size() > 1) {
					return overloads.stream().filter(m -> m.getParameterTypes().length >= pIndex + 1 || m.isVarargs())
							.map(m -> {
								if (m.isVarargs()) {
									return resolveType(m.getParameterTypes()[m.getParameterTypes().length - 1]);
								} else if (isNonGenericEqual(m.getParameterTypes()[pIndex - 1], lType)) {
									return resolveType(m.getParameterTypes()[pIndex]);
								}
								return null;
							})
							.filter(Predicates.notNull())
							.collect(Collectors.toSet());
				} else {
					if ((pIndex > -1) && pIndex < parameters.size()) {
						return Sets.newHashSet(parameters.get(typeIndex));
					} else if (binding.isVarargs()) {
						return Sets.newHashSet(resolveType(parameters.get(parameters.size() - 1)));
					}
					return Collections.emptySet();
				}
			}
		}
		return Collections.emptySet();
	}

	private List<IMethodBinding> findFromOverloaded(IMethodBinding binding, ITypeBinding containerType) {
		ITypeBinding type = binding.getDeclaringClass();
		return Arrays.stream(type.getDeclaredMethods()).filter(m -> {
			return m.getName().equals(binding.getName())
					&& ((m.getDeclaringClass().equals(containerType) && Modifier.isPrivate(m.getModifiers()))
							|| Modifier.isPublic(m.getModifiers()));
		}).collect(Collectors.toList());
	}
	
	private ITypeBinding resolveTypeBinding(ASTNode node) {
		if (node instanceof Expression) {
			return ((Expression) node).resolveTypeBinding();
		}
		return null;
	}

	private ITypeBinding resolveType(ITypeBinding binding) {
		return (binding.isArray()) ? binding.getComponentType() : binding;
	}

	public Set<IType> getExpectedTypes() {
		return expectedTypes;
	}

	public IType getExpectedType() {
		Iterator<IType> iterator = expectedTypes.iterator();
		if (iterator.hasNext()) {
			return iterator.next();
		}
		return null;
	}
	
	public boolean isNonGenericEqual(ITypeBinding left, ITypeBinding right) {
		 String leftName = Optional.ofNullable(left).map(ITypeBinding::getQualifiedName).map(this::genericErasure).orElse("");
		 String rightName = Optional.ofNullable(right).map(ITypeBinding::getQualifiedName).map(this::genericErasure).orElse("");
		 return leftName.equals(rightName);
	}
	
	private String genericErasure(String fqn) {
		int i = fqn.indexOf("<");
		if(i > -1) {
			return fqn.substring(0, i);
		}
		return fqn;
	}
	
	private ITypeBinding getDeclaringType(ASTNode node) {
		ASTNode n = node.getParent();
		while(n != null) {
			if(n instanceof TypeDeclaration) {
				return ((TypeDeclaration) n).resolveBinding();
			}
			n = n.getParent();
		}
		return null;
	}

}