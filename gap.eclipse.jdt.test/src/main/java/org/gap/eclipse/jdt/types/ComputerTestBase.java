package org.gap.eclipse.jdt.types;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IEditorPart;

@SuppressWarnings("restriction")
public class ComputerTestBase {

	protected IJavaProject project;
	protected IPackageFragmentRoot javaSrc;
	protected IPackageFragment pkg;
	private Document document;
	private AbstractSmartProposalComputer proposalComputer;

	protected final void setupProject(AbstractSmartProposalComputer proposalComputer) throws CoreException {
		this.proposalComputer = proposalComputer;
		project = JavaProjectHelper.createJavaProject("TestProject", "bin");
		JavaProjectHelper.addRTJar18(project);
		javaSrc = JavaProjectHelper.addSourceContainer(project, "src");
		pkg = javaSrc.createPackageFragment("completion.test", false, null);
	}

	protected final void disposeProject() throws CoreException {
		this.document = null;
		this.proposalComputer = null;
		JavaProjectHelper.delete(project);
	}

	protected List<ICompletionProposal> computeCompletionProposals(ICompilationUnit cu, int completionIndex) throws Exception {
		IEditorPart editor= EditorUtility.openInEditor(cu);
		ITextViewer viewer= new TextViewer(editor.getSite().getShell(), SWT.NONE);
		this.document = new Document(cu.getSource());
		viewer.setDocument(document);
		JavaContentAssistInvocationContext ctx= new JavaContentAssistInvocationContext(viewer, completionIndex, editor);
		viewer.setSelectedRange(completionIndex, 0);
	
		return this.proposalComputer.computeCompletionProposals(ctx, null);
	}

	protected String computeExpected(StringBuilder code, String replace, String expectedCompletion) {
		int offset = code.indexOf(replace);
		code.delete(offset, offset + replace.length()).insert(offset, expectedCompletion);
		return code.toString();
	}

	protected String computeActual(ICompletionProposal proposal, ICompilationUnit cu, int completionIndex)
			throws Exception {
		proposal.apply(this.document);
		return this.document.get();
	}

}
