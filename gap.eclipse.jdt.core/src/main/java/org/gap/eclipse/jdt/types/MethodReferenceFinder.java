package org.gap.eclipse.jdt.types;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.gap.eclipse.jdt.common.Log;

@SuppressWarnings("restriction")
public class MethodReferenceFinder {

	public @NonNull Stream<Entry<IJavaElement, IMethod>> find(@NonNull IMethod lambdaMethod,
			@NonNull List<IJavaElement> visibleElements,
			@NonNull JavaContentAssistInvocationContext context) throws JavaModelException {
		String lambdaSignature = lambdaMethod.getSignature();
		if (context.getCoreContext() == null) {
			return Stream.empty();
		}

		final boolean isInStaticContext = isInStaticContext(context.getCoreContext().getEnclosingElement());

		return visibleElements.stream().filter(this::isAcceptableElement).flatMap(e -> {
			try {
				Stream<Entry<IJavaElement, IMethod>> methods;
				// enhance to support method accessibility.

				if (e instanceof IField) {
					IField field = (IField) e;
					methods = methodsFromType(context, e, field.getTypeSignature(),
							field.getTypeRoot().findPrimaryType());
				} else if (e instanceof ILocalVariable) {
					ILocalVariable localVariable = (ILocalVariable) e;
					methods = methodsFromType(context, e, localVariable.getTypeSignature(),
							localVariable.getTypeRoot().findPrimaryType());
				} else if (e instanceof IType) {
					methods = Stream.of(((IType) e).getMethods()).map(m -> Map.entry(e, m));
				} else if (e instanceof IMethod) {
					methods = Stream.of(Map.entry(e, (IMethod) e));
				} else {
					methods = Stream.empty();
				}

				return methods.filter(me -> {
					try {
						return Flags.isStatic(me.getValue().getFlags()) == isInStaticContext;
					} catch (JavaModelException ex) {
						Log.error(ex);
						return false;
					}
				}).filter(me -> isMatchingMethod(lambdaSignature, me.getValue()));
			} catch (JavaModelException ex) {
				Log.error(ex);
			}
			return Stream.empty();
		});
	}

	protected Stream<Entry<IJavaElement, IMethod>> methodsFromType(JavaContentAssistInvocationContext context,
			IJavaElement e, String typeSignature, IType primary) throws JavaModelException {
		Stream<Entry<IJavaElement, IMethod>> methods;
		IType type = context.getProject().findType(JavaModelUtil.getResolvedTypeName(typeSignature, primary));
		methods = Optional.ofNullable(type).stream().flatMap(t -> {
			try {
				return Stream.of(t.getMethods());
			} catch (JavaModelException ex) {
				Log.error(ex);
				return Stream.empty();
			}
		}).map(m -> Map.entry(e, m));
		return methods;
	}

	private boolean isInStaticContext(IJavaElement enclosingElement) throws JavaModelException {
		if (enclosingElement instanceof IMethod) {
			return Flags.isStatic(((IMethod) enclosingElement).getFlags());
		}
		return false;
	}

	private boolean isMatchingMethod(String lambdaSignature, IMethod m) {
		try {
			return lambdaSignature.equals(m.getSignature());
		} catch (JavaModelException e) {
			Log.error(e);
			return false;
		}
	}

	private boolean isAcceptableElement(IJavaElement element) {
		if (element instanceof IField) {
			try {
				return Signature
						.getTypeSignatureKind(((IField) element).getTypeSignature()) == Signature.CLASS_TYPE_SIGNATURE;
			} catch (JavaModelException e) {
				Log.error(e);
			}
		} else if (element instanceof ILocalVariable) {
			return Signature.getTypeSignatureKind(
					((ILocalVariable) element).getTypeSignature()) == Signature.CLASS_TYPE_SIGNATURE;
		}

		return (element instanceof IType) || (element instanceof IMethod);
	}

}
