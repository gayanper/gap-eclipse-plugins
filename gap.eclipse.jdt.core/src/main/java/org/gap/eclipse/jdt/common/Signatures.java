package org.gap.eclipse.jdt.common;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

public final class Signatures {
	private Signatures() {
		
	}
	
	public static boolean isAssignable(String signature, String toSignature) {
		// check if the both signatures have the same erasure types
		if(!Signature.getTypeErasure(signature).equals(Signature.getTypeErasure(toSignature))) {
			return false;
		}

		// check if the both signatures have the same type parameters
		String[] sigArguments = Signature.getTypeArguments(signature);
		String[] toArguments = Signature.getTypeArguments(toSignature);
		
		for(int i = 0; i < toArguments.length; i++) {
			final String toArg = toArguments[i];
			final String sigArg = sigArguments[i];
			
			if(!toArg.equals(sigArg)) {
				// check if the sigArg is a upper bound
				int startSigOffset = 0;
				// if its upper bound then try to ignore it for now
				if(sigArg.startsWith("+")) {
					startSigOffset = 1;
				}
				if(!Signature.getSignatureQualifier(sigArg.substring(startSigOffset)).isEmpty()) {
					// if not type param then the type should match
					if(!toArg.equals(sigArg.substring(startSigOffset))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	public static String getFullQualifiedReturnType(IMethod method) throws JavaModelException {
		final String returnType = method.getReturnType();

		if(returnType.startsWith("Q")) {
			StringBuilder builder = new StringBuilder();
			String packageName = method.getDeclaringType().getPackageFragment().getElementName();
			builder.append("L");
			if(!packageName.equals(IPackageFragment.DEFAULT_PACKAGE_NAME)) {
				builder.append(packageName).append(".");
			}
			builder.append(returnType.substring(1));
			return builder.toString();
		}
		return returnType;
	}
}
