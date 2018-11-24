package org.gap.eclipse.recommenders.types;


import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.recommenders.completion.rcp.IRecommendersCompletionContext;
import org.eclipse.recommenders.completion.rcp.processable.IProcessableProposal;
import org.eclipse.recommenders.completion.rcp.processable.OverlayImageProposalProcessor;
import org.eclipse.recommenders.completion.rcp.processable.ProposalProcessorManager;
import org.eclipse.recommenders.completion.rcp.processable.ProposalTag;
import org.eclipse.recommenders.completion.rcp.processable.SessionProcessor;
import org.eclipse.recommenders.completion.rcp.processable.SimpleProposalProcessor;
import org.eclipse.recommenders.rcp.SharedImages;
import org.eclipse.recommenders.utils.names.ITypeName;
import org.eclipse.recommenders.utils.names.Names;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

public class TypesCompletionSessionProcessor extends SessionProcessor {
	private static final CompletionProposal NULL_PROPOSAL = new CompletionProposal();
	public static final int BOOST = 50;

	private ImmutableSet<String> subtypes;
	private final OverlayImageProposalProcessor overlayDecorator;

	@Inject
	public TypesCompletionSessionProcessor(SharedImages images) {
		overlayDecorator = new OverlayImageProposalProcessor(images.getDescriptor(SharedImages.Images.OVR_STAR),
				IDecoration.TOP_LEFT);
	}

	@Override
	public boolean startSession(IRecommendersCompletionContext context) {
		Set<ITypeName> expectedTypes = context.getExpectedTypeNames();
		if (expectedTypes.isEmpty()) {
			return false;
		}

		Builder<String> results = ImmutableSet.builder();
		for (ITypeName expectedType : expectedTypes) {
			if (isUnsupported(expectedType)) {
				continue;
			}
			results.addAll(subtypes(expectedType, context.getProject()));
		}
		subtypes = results.build();
		return !subtypes.isEmpty();
	}

	private boolean isUnsupported(ITypeName typeName) {
		return typeName.isArrayType() || typeName.isPrimitiveType();
	}

	private Iterable<? extends String> subtypes(ITypeName expected, IJavaProject project) {
		Set<String> result = new HashSet<>();
		try {
			IType type = project.findType(Names.vm2srcQualifiedType(expected));
			IType[] subtypes = type.newTypeHierarchy(new NullProgressMonitor()).getAllSubtypes(type);
			for (IType subtype : subtypes) {
				result.add(subtype.getFullyQualifiedName());
			}
		} catch (JavaModelException e) {
			TypesPlugin.getDefault().log("Error while searching type hierarchy.", e);
		}
		return result;
	}

	// the following logics are from the code recommenders
	// https://raw.githubusercontent.com/eclipse/recommenders/master/plugins/org.eclipse.recommenders.types.rcp/src/org/eclipse/recommenders/internal/types/rcp/TypesCompletionSessionProcessor.java.
	@Override
	public void process(IProcessableProposal proposal) throws Exception {
		if (subtypes == null || subtypes.isEmpty()) {
			return;
		}

		final CompletionProposal coreProposal = proposal.getCoreProposal().or(NULL_PROPOSAL);
		switch (coreProposal.getKind()) {
		case CompletionProposal.FIELD_REF:
		case CompletionProposal.LOCAL_VARIABLE_REF:
		case CompletionProposal.TYPE_REF:
			handleProposal(proposal, coreProposal.getSignature());
			break;
		case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
		case CompletionProposal.CONSTRUCTOR_INVOCATION:
			handleProposal(proposal, coreProposal.getDeclarationSignature());
			break;
		case CompletionProposal.METHOD_REF:
			handleProposal(proposal, Signature.getReturnType(coreProposal.getSignature()));
			break;
		case CompletionProposal.FIELD_REF_WITH_CASTED_RECEIVER:
		case CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER:
			handleProposal(proposal, coreProposal.getReceiverSignature());
			break;
		}
	}

	private void handleProposal(IProcessableProposal proposal, char[] typeSignature) {
		if (isSubtype(typeSignature)) {
			proposal.setTag(ProposalTag.RECOMMENDERS_SCORE, BOOST);
			ProposalProcessorManager mgr = proposal.getProposalProcessorManager();
			mgr.addProcessor(new SimpleProposalProcessor(BOOST));
			mgr.addProcessor(overlayDecorator);
		}
	}

	private boolean isSubtype(char[] typeSignature) {
		if (Signature.getArrayCount(typeSignature) > 0) {
			// No support for the subtype relation amongst array types yet.
			return false;
		}
		if (isPrimitiveOrVoid(typeSignature)) {
			return false;
		}

		// No support for generics yet.
		char[] erasedTypeSignature = Signature.getTypeErasure(typeSignature);
		String type = new String(erasedTypeSignature, 1, erasedTypeSignature.length - 2);
		return subtypes.contains(type);
	}

	private boolean isPrimitiveOrVoid(char[] typeSignature) {
		return typeSignature.length == 1;
	}

}
