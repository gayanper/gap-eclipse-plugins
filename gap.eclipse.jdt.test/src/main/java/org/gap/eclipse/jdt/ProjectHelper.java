package org.gap.eclipse.jdt;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.util.CoreUtility;

@SuppressWarnings("restriction")
public class ProjectHelper {
	private static final String BIN_FOLDER_NAME = "bin";

	private static final String SRC_FOLDER_NAME = "src";

	private static final IPath[] EMPTY_FILTERS = new IPath[0];

	private static final IPath RT_STUBS_18 = new Path("rt/rtstubs18.jar");

	public static IPackageFragmentRoot createJavaProject(String projectName) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = root.getProject(projectName);
		if (!project.exists()) {
			project.create(null);
			project.open(null);
			addNatureToProject(project, JavaCore.NATURE_ID);

			IFolder binFolder = project.getFolder(BIN_FOLDER_NAME);
			if (!binFolder.exists()) {
				CoreUtility.createFolder(binFolder, false, true, null);
			}
			IPath outputLocation = binFolder.getFullPath();
			IJavaProject jproject = JavaCore.create(project);

			jproject.setOutputLocation(outputLocation, null);

			IFolder srcFolder = project.getFolder(SRC_FOLDER_NAME);
			if (!srcFolder.exists()) {
				CoreUtility.createFolder(srcFolder, false, true, null);
			}
			IPackageFragmentRoot pfr = jproject.getPackageFragmentRoot(srcFolder);
			IClasspathEntry sourceCpe = JavaCore.newSourceEntry(pfr.getPath(), EMPTY_FILTERS, EMPTY_FILTERS,
					outputLocation, new IClasspathAttribute[0]);

			IClasspathEntry rtCpe = JavaCore.newLibraryEntry(getFileInPlugin(RT_STUBS_18), null, null);
			jproject.setRawClasspath(new IClasspathEntry[] { sourceCpe, rtCpe }, null);

			Map<String, String> options = jproject.getOptions(false);
			JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
			jproject.setOptions(options);

			return pfr;
		} else {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
			IJavaProject jproject = JavaCore.create(project);
			IFolder srcFolder = project.getFolder(SRC_FOLDER_NAME);
			return jproject.getPackageFragmentRoot(srcFolder);
		}

	}

	private static void addNatureToProject(IProject proj, String natureId) throws CoreException {
		IProjectDescription description = proj.getDescription();
		description.setNatureIds(new String[] { natureId });
		proj.setDescription(description, null);
	}

	public static IPath getFileInPlugin(IPath path) throws CoreException {
		try {
			URL installURL = new URL(CorePlugin.getDefault().getBundle().getEntry("/"), path.toString());
			URL localURL = FileLocator.toFileURL(installURL);
			return Path.fromOSString(localURL.getFile());
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, CorePlugin.PLUGIN_ID, IStatus.ERROR, e.getMessage(), e));
		}
	}
	
	public static int getCompletionIndex(StringBuilder builder) {
		return builder.toString().indexOf('$');
	}
	
	public static ICompilationUnit getCompilationUnit(IPackageFragment fragment, StringBuilder builder, String name) throws JavaModelException {
		return fragment.createCompilationUnit(name, builder.toString().replace("$", ""), false, null);
	}
}
