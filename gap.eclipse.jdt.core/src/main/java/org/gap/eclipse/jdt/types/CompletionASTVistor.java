package org.gap.eclipse.jdt.types;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
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
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.gap.eclipse.jdt.CorePlugin;

import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;

class CompletionASTVistor extends ASTVisitor {
	private int offset;
	private IJavaProject project;
	private boolean preceedSpace = false;
	private ASTNode lastFoundNode;
	private boolean doneProcessing = false;
	private Set<Entry<ITypeBinding, IType>> typeEntries;

	private CodeRange lastVisited;
	private Supplier<List<ASTNode>> argumentSupplier;
	private Function<IMethodBinding, List<ITypeBinding>> parameterSupplier;
	private Supplier<IMethodBinding> bindingSupplier;
	private boolean searchInOverloadMethods;

	public CompletionASTVistor(JavaContentAssistInvocationContext context) {
		this(context, true);
	}
	public CompletionASTVistor(JavaContentAssistInvocationContext context, boolean searchInOverloadMethods) {
		this.searchInOverloadMethods = searchInOverloadMethods;
		this.typeEntries = new HashSet<>();
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

	@Override
	public boolean visit(LambdaExpression node) {
		resetVistor();
		return true;
	}

	@Override
	public boolean visit(PostfixExpression node) {
		// .map(s ->); is identified as a postfix expression in map method

		resetVistor();
		return true;
	}

	private void resetVistor() {
		this.lastFoundNode = null;
		this.lastVisited = null;
		this.argumentSupplier = null;
		this.parameterSupplier = null;
		this.bindingSupplier = null;
	}

	private boolean visitNode(Expression node, Supplier<List<ASTNode>> argumentSupplier, Function<IMethodBinding, List<ITypeBinding>> parameterSupplier, Supplier<IMethodBinding> bindingSupplier) {
		final CodeRange current = new CodeRange(node.getStartPosition(), node.getStartPosition() + node.getLength(),
				node);

		final IMethodBinding methodBinding = bindingSupplier.get();
		if (methodBinding == null) {
			return false;
		}

		if (current.inRange(offset) && (lastVisited == null || lastVisited.inRange(current))) {
			lastVisited = current;
			lastFoundNode = node;
			this.argumentSupplier = argumentSupplier;
			this.parameterSupplier = parameterSupplier;
			this.bindingSupplier = bindingSupplier;

			return true;
		}
		return !doneProcessing;
	}

	@Override
	public void endVisit(MethodInvocation node) {
		if (lastFoundNode != null && lastFoundNode.equals(node)) {
			processNodeForType(lastFoundNode);
			doneProcessing = true;
			lastFoundNode = null;
		}
	}
	
	private void processNodeForType(ASTNode node) {
		Set<ITypeBinding> typesAtOffset = findParameterTypeAtOffset(getDeclaringType(node));
		typesAtOffset.stream().map(t -> {
			try {
				return new AbstractMap.SimpleImmutableEntry<>(t, project.findType(Signature.getTypeErasure(t.getQualifiedName())));
			} catch (JavaModelException | IllegalArgumentException e) {
				CorePlugin.getDefault().logError(e.getMessage(), e);
				return null;
			}
		}).filter(e -> e.getValue() != null).forEach(t -> typeEntries.add(t));
	}

	private Set<ITypeBinding> findParameterTypeAtOffset(ITypeBinding containerType) {
		final IMethodBinding binding = bindingSupplier.get();

		if (binding == null) {
			return Collections.emptySet();
		}

		final List<ASTNode> arguments = argumentSupplier.get();
		final List<ITypeBinding> parameters = parameterSupplier.apply(binding);
		final List<IMethodBinding> overloads = searchInOverloadMethods ? findFromOverloaded(binding, containerType) : Collections.emptyList();
		boolean checkInOverloads =!overloads.isEmpty();
		if (arguments.isEmpty()) {
			if (!parameters.isEmpty()) {
				if(checkInOverloads) {
					return overloads.stream().filter(m -> m.getParameterTypes().length > 0)
							.map(m -> resolveType(m.getParameterTypes()[0]))
							.collect(Collectors.toSet());
				} else {
					return Sets.newHashSet(resolveType(parameters.get(0)));
				}
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
			
			// we might be trying to resolve the first argument while others are filled in like
			// collect(<caret>, new X(), new Y()), but we want overloads if those other arguments
			// are not filled.
			if (typeIndex == -1 && checkOffset < arguments.get(0).getStartPosition()
					&& binding.getParameterTypes().length - arguments.size() == 1) {
				typeIndex = 0;
				checkInOverloads = false;
			}

			if (binding != null && typeIndex > -1) {
				final int pIndex = typeIndex;
				final ITypeBinding lType = lastType;
				if (checkInOverloads) {
					return overloads.stream().filter(m -> m.getParameterTypes().length >= pIndex + 1 || m.isVarargs())
							.map(m -> {
								// on statement line test(field$) we end up in this block even for the first parameter.
								// so we should handle is gracefully here.
								if (m.isVarargs()) {
									return resolveType(m.getParameterTypes()[m.getParameterTypes().length - 1]);
								} else if (pIndex == 0 ||  isNonGenericEqual(m.getParameterTypes()[pIndex - 1], lType)) {
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
		return typeEntries.stream().map(Entry::getValue).collect(Collectors.toSet());
	}
	
	public Set<ITypeBinding> getExpectedTypeBindings() {
		return typeEntries.stream().map(Entry::getKey).collect(Collectors.toSet());
	}

	public IType getExpectedType() {
		Optional<Entry<ITypeBinding,IType>> first = typeEntries.stream().findFirst();
		if (first.isPresent()) {
			return first.get().getValue();
		}
		return null;
	}
	
	public ITypeBinding getExpectedTypeBinding() {
		Optional<Entry<ITypeBinding,IType>> first = typeEntries.stream().findFirst();
		if (first.isPresent()) {
			return first.get().getKey();
		}
		return null;
	}

	
	public Set<Entry<ITypeBinding, IType>> getExpectedTypeEntries() {
		return typeEntries;
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