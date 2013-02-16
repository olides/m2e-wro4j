package org.jboss.tools.m2e.wro4j.internal.jshint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lint {
	private Map<String, LintFile> files;
	
	public Lint() {
		files = new HashMap<String,LintFile>();
	}
	
	public LintFile addFile(final String name) {
		if (files.containsKey(name)) {
			return files.get(name);
		}
		LintFile file = new LintFile(name);
		files.put(file.getName(), file);
		return file;
	}
	
	public List<LintFileIssue> getIssues() {
		List<LintFileIssue> issues = new ArrayList<LintFileIssue>();
		for (LintFile file : files.values()) {
			for (LintFileIssue issue : file.getIssues()) {
				issues.add(issue);
			}
		}
		return issues;
	}
}