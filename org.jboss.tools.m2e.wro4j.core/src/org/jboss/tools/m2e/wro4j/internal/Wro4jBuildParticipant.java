/*******************************************************************************
 * Copyright (c) 2012 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.jboss.tools.m2e.wro4j.internal;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.plugin.MojoExecution;
import org.codehaus.plexus.util.Scanner;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.MavenProjectUtils;
import org.eclipse.m2e.core.project.configurator.MojoExecutionBuildParticipant;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Wro4jBuildParticipant extends MojoExecutionBuildParticipant {

	private static final String WROJ_JSHINT_MARKER = "org.jboss.tools.m2e.wro4j.internal.jshintmarker";

	private static final Pattern WRO4J_FILES_PATTERN = Pattern
			.compile("^(\\/?.*\\/)?wro\\.(xml|groovy|properties)$");

	private static final Pattern WEB_RESOURCES_PATTERN = Pattern
			.compile("([^\\s]+(\\.(?i)(js|css|scss|sass|less|coffee|json|template))$)");

	private static final String DESTINATION_FOLDER = "destinationFolder";
	private static final String CSS_DESTINATION_FOLDER = "cssDestinationFolder";
	private static final String JS_DESTINATION_FOLDER = "jsDestinationFolder";

	public Wro4jBuildParticipant(MojoExecution execution) {
		super(execution, true);
	}

	private MessageConsole findConsole(String name) {
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		IConsoleManager conMan = plugin.getConsoleManager();
		IConsole[] existing = conMan.getConsoles();
		for (int i = 0; i < existing.length; i++)
			if (name.equals(existing[i].getName()))
				return (MessageConsole) existing[i];
		// no console found, so create a new one
		MessageConsole myConsole = new MessageConsole(name, null);
		conMan.addConsoles(new IConsole[] { myConsole });
		return myConsole;
	}

	private IMarker[] findJSHintMarkers(IResource target) throws CoreException {
		return target.findMarkers(WROJ_JSHINT_MARKER, true,
				IResource.DEPTH_INFINITE);
	}

	private void deleteProjectJSHintMarkers(IProject project)
			throws CoreException {
		project.deleteMarkers(WROJ_JSHINT_MARKER, true,
				IResource.DEPTH_INFINITE);
	}

	@Override
	public Set<IProject> build(int kind, IProgressMonitor monitor)
			throws Exception {

		MojoExecution mojoExecution = getMojoExecution();
		if (mojoExecution == null) {
			return null;
		}

		Set<IProject> result = null;
		Xpp3Dom originalConfiguration = null;
		boolean restoreConfig = false;
		MessageConsoleStream stream = null;

		try {
			MessageConsole msgConsole = findConsole("Wro4J");

			msgConsole.activate();
			stream = msgConsole.newMessageStream();

			if (getMojoExecution().getMojoDescriptor().getFullGoalName()
					.contains("jshint")) {
				stream.println("Running jshint goal...");
				if (monitor != null) {
					String taskName = NLS.bind("Invoking {0} on {1}",
							getMojoExecution().getMojoDescriptor()
									.getFullGoalName(), getMavenProjectFacade()
									.getProject().getName());
					monitor.setTaskName(taskName);
				}
				IMaven maven = MavenPlugin.getMaven();
				maven.execute(getSession(), getMojoExecution(), monitor);

				IMavenProjectFacade facade = getMavenProjectFacade();
				IProject project = facade.getProject();

				String target = facade.getMavenProject().getBuild()
						.getDirectory();
				File reportFile = new File(target, "wro4j-reports"
						+ File.separator + "jshint.xml");
				parseJsHintReportFile(project, reportFile, stream);
				stream.write("Done\n");
			} else {
				restoreConfig = true;
				BuildContext buildContext = getBuildContext();
				if (notCleanFullBuild(kind)
						&& !wroResourceChangeDetected(mojoExecution,
								buildContext)) {
					stream.println("No change detected");
					return null;
				}

				originalConfiguration = mojoExecution.getConfiguration();

				stream.write("Running goal ");
				stream.write(getMojoExecution().getMojoDescriptor()
						.getFullGoalName());
				stream.write("...\n");
				File destinationFolder = getFolder(mojoExecution,
						DESTINATION_FOLDER);
				File jsDestinationFolder = getFolder(mojoExecution,
						JS_DESTINATION_FOLDER);
				File cssDestinationFolder = getFolder(mojoExecution,
						CSS_DESTINATION_FOLDER);

				Xpp3Dom customConfiguration = customize(originalConfiguration,
						destinationFolder, jsDestinationFolder,
						cssDestinationFolder);
				// Add custom configuration
				mojoExecution.setConfiguration(customConfiguration);

				if (monitor != null) {
					String taskName = NLS.bind("Invoking {0} on {1}",
							getMojoExecution().getMojoDescriptor()
									.getFullGoalName(), getMavenProjectFacade()
									.getProject().getName());
					monitor.setTaskName(taskName);
				}
				// execute mojo
				result = super.build(kind, monitor);

				// tell m2e builder to refresh generated folder
				refreshFolders(mojoExecution, buildContext);

			}
		} catch (Exception e) {
			e.printStackTrace();
			if (stream != null) {
				stream.println("Error while executing plugin");
				StringWriter swriter = new StringWriter();
				PrintWriter pwriter = new PrintWriter(swriter);
				e.printStackTrace(pwriter);
				stream.println(swriter.toString());
			} else {
				throw e;
			}
		} finally {
			// restore original configuration
			if (restoreConfig)
				mojoExecution.setConfiguration(originalConfiguration);
			if (stream != null) {
				stream.close();
			}
		}

		return result;
	}

	private void parseJsHintReportFile(IProject project, File reportFile,
			MessageConsoleStream stream) {
		try {
			if (reportFile.exists()) {
				// Delete existing markers accross builds.
				deleteProjectJSHintMarkers(project);
				
				stream.println("FOUND File " + reportFile.getAbsolutePath());
				DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
						.newInstance();
				DocumentBuilder builder = docBuilderFactory
						.newDocumentBuilder();
				Document doc = builder.parse(reportFile);
				Element rootElement = doc.getDocumentElement();
				NodeList childNodes = rootElement.getChildNodes();
				if (childNodes != null) {
					int len = childNodes.getLength();
					for (int i = 0; i < len; i++) {
						Node n = childNodes.item(i);
						if (n.getNodeType() == Node.ELEMENT_NODE
								&& n.getNodeName().equals("file")) {
							Element fileElem = (Element) n;
							String fileName = fileElem.getAttribute("name");
							NodeList fileChildren = fileElem.getChildNodes();
							if (fileChildren != null) {
								int fileChildLen = fileChildren.getLength();
								for (int fileIdx = 0; fileIdx < fileChildLen; fileIdx++) {
									Node cn = fileChildren.item(fileIdx);
									if (cn.getNodeType() == Node.ELEMENT_NODE
											&& cn.getNodeName().equals("issue")) {
										Element issueElem = (Element) cn;
										String reason = issueElem
												.getAttribute("reason");
										String evidence = issueElem
												.getAttribute("evidence");
										int line = Integer.parseInt(issueElem
												.getAttribute("line"));
										int col = Integer.parseInt(issueElem
												.getAttribute("char"));
										addErrorMarker(project, fileName,
												reason, evidence, line, col,
												stream);
									}
								}
							}
						}
					}
				}
			} else {
				stream.println("ReportFile " + reportFile.getAbsolutePath()
						+ " not found.");
			}
		} catch (Exception parseEx) {
			stream.println("Error while parsing result file "
					+ parseEx.getMessage());
		}
	}

	@SuppressWarnings("restriction")
	private void addErrorMarker(IProject project, String fileName,
			String reason, String evidence, int line, int column,
			MessageConsoleStream stream) {
		try {
			IFile file = project.getFile(fileName);

			if (file == null || !file.exists()) {
				StringBuilder webAppPath = new StringBuilder("src")
						.append(File.separator).append("main")
						.append(File.separator).append("webapp");
				File errorWebAppFile = new File(webAppPath.toString(), fileName);
				stream.println("error file location "
						+ errorWebAppFile.getPath());

				file = project.getFile(errorWebAppFile.getPath());
			}

			String message = MessageFormat.format("{0} - evidence: \"{1}\"",
					reason, evidence);
			IResource markerResource = null;
			if (file != null && file.exists()) {
				markerResource = file;
			} else {
				stream.println("Unable to retrieve project's file: "
						+ file.getFullPath().toPortableString());
				markerResource = project;
			}
			createJSHintMarker(markerResource, message, IMarker.SEVERITY_ERROR,
					line);
			stream.println(MessageFormat
					.format("File: {0},\nline: {1}\ncolumn: {2}\nreason: {3}\nevidence: \"{4}\"\n",
							fileName, line, column, reason, evidence));
		} catch (Exception e) {
			stream.println("Unable to create marker " + e.getMessage());
			StringWriter writer = new StringWriter();
			PrintWriter pWriter = new PrintWriter(writer);
			e.printStackTrace(pWriter);
			stream.println(writer.toString());
		}
	}

	private IMarker createJSHintMarker(IResource resource, String message,
			int severity, int line) throws CoreException {
		IMarker marker = resource.createMarker(WROJ_JSHINT_MARKER);
		marker.setAttribute(IMarker.MESSAGE, message);
		marker.setAttribute(IMarker.SEVERITY, severity);
		marker.setAttribute(IMarker.LINE_NUMBER, line);
		return marker;
	}

	private File getFolder(MojoExecution mojoExecution, String folderName)
			throws CoreException {
		IMaven maven = MavenPlugin.getMaven();
		File folder = maven.getMojoParameterValue(getSession(), mojoExecution,
				folderName, File.class);
		return folder;
	}

	private boolean wroResourceChangeDetected(MojoExecution mojoExecution,
			BuildContext buildContext) throws CoreException {

		// If the pom file changed, we force wro4j's invocation
		if (isPomModified()) {
			return true;
		}

		// check if any of the web resource files changed
		File source = getFolder(mojoExecution, "contextFolder");
		// TODO also analyze output classes folders as wro4j can use classpath
		// files
		Scanner ds = buildContext.newScanner(source); // delta or full scanner
		ds.scan();
		String[] includedFiles = ds.getIncludedFiles();
		if (includedFiles == null || includedFiles.length <= 0) {
			return false;
		}

		// Quick'n dirty trick to avoid calling wro4j for ANY file change
		// Let's restrict ourselves to a few known extensions
		for (String file : includedFiles) {
			String portableFile = file.replace('\\', '/');
			// use 2 matchers only to improve readability
			Matcher m = WRO4J_FILES_PATTERN.matcher(portableFile);
			if (m.matches()) {
				return true;
			}

			m = WEB_RESOURCES_PATTERN.matcher(portableFile);
			if (m.matches()) {
				return true;
			}
		}

		// TODO analyze wro.xml for file patterns,
		// check if includedFiles match wro4j-maven-plugin's select targetGroups
		// filePatterns
		return false;
	}

	private boolean isPomModified() {
		IMavenProjectFacade facade = getMavenProjectFacade();
		IResourceDelta delta = getDelta(facade.getProject());
		if (delta == null) {
			return false;
		}

		if (delta.findMember(facade.getPom().getProjectRelativePath()) != null) {
			return true;
		}
		return false;
	}

	private void refreshFolders(MojoExecution mojoExecution,
			BuildContext buildContext) throws CoreException {
		refreshFolder(mojoExecution, buildContext, DESTINATION_FOLDER);
		refreshFolder(mojoExecution, buildContext, CSS_DESTINATION_FOLDER);
		refreshFolder(mojoExecution, buildContext, JS_DESTINATION_FOLDER);
	}

	private void refreshFolder(MojoExecution mojoExecution,
			BuildContext buildContext, String directoryName)
			throws CoreException {
		File outputDirectory = getFolder(mojoExecution, directoryName);
		if (outputDirectory != null && outputDirectory.exists()) {
			buildContext.refresh(outputDirectory);
		}
	}

	private Xpp3Dom customize(Xpp3Dom originalConfiguration,
			File originalDestinationFolder, File originalJsDestinationFolder,
			File originalCssDestinationFolder) throws IOException {
		IMavenProjectFacade facade = getMavenProjectFacade();
		if (!"war".equals(facade.getPackaging())) {
			// Not a war project, we don't know how to customize that
			return originalConfiguration;
		}

		IProject project = facade.getProject();
		String target = facade.getMavenProject().getBuild().getDirectory();
		IPath relativeTargetPath = MavenProjectUtils.getProjectRelativePath(
				project, target);
		if (relativeTargetPath == null) {
			// target folder not under the project directory, we bail
			return originalConfiguration;
		}

		IFolder webResourcesFolder = project.getFolder(relativeTargetPath
				.append("m2e-wtp").append("web-resources"));
		if (!webResourcesFolder.exists()) {
			// Not a m2e-wtp project, we don't know how to customize either
			// TODO Try to support Sonatype's webby instead?
			return originalConfiguration;
		}

		IPath fullTargetPath = new Path(target);
		IPath defaultOutputPathPrefix = fullTargetPath.append(facade
				.getMavenProject().getBuild().getFinalName());

		Xpp3Dom customConfiguration = new Xpp3Dom("configuration");
		Xpp3DomUtils.mergeXpp3Dom(customConfiguration, originalConfiguration);

		customizeFolder(originalDestinationFolder, webResourcesFolder,
				defaultOutputPathPrefix, customConfiguration,
				DESTINATION_FOLDER);

		customizeFolder(originalJsDestinationFolder, webResourcesFolder,
				defaultOutputPathPrefix, customConfiguration,
				JS_DESTINATION_FOLDER);

		customizeFolder(originalCssDestinationFolder, webResourcesFolder,
				defaultOutputPathPrefix, customConfiguration,
				CSS_DESTINATION_FOLDER);

		return customConfiguration;
	}

	private void customizeFolder(File originalDestinationFolder,
			IFolder webResourcesFolder, IPath defaultOutputPathPrefix,
			Xpp3Dom configuration, String folderParameterName)
			throws IOException {

		if (originalDestinationFolder != null) {
			IPath customPath = getReplacementPath(originalDestinationFolder,
					webResourcesFolder, defaultOutputPathPrefix);
			if (customPath != null) {
				Xpp3Dom dom = configuration.getChild(folderParameterName);
				if (dom == null) {
					dom = new Xpp3Dom(folderParameterName);
					configuration.addChild(dom);
				}
				dom.setValue(customPath.toOSString());
			}
		}
	}

	private IPath getReplacementPath(File originalFolder,
			IFolder webResourcesFolder, IPath defaultOutputPathPrefix)
			throws IOException {
		IPath originalDestinationFolderPath = Path.fromOSString(originalFolder
				.getCanonicalPath());

		if (!defaultOutputPathPrefix.isPrefixOf(originalDestinationFolderPath)) {
			return null;
		}

		IPath relativePath = originalDestinationFolderPath
				.makeRelativeTo(defaultOutputPathPrefix);
		IPath customPath = webResourcesFolder.getLocation()
				.append(relativePath);
		return customPath;
	}

	private boolean notCleanFullBuild(int kind) {
		return IncrementalProjectBuilder.FULL_BUILD != kind
				&& IncrementalProjectBuilder.CLEAN_BUILD != kind;
	}
}
