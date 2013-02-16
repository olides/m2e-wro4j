package org.jboss.tools.m2e.wro4j.internal.jshint;

import java.util.ArrayList;
import java.util.List;

public class LintFile {
	private final String name;
	private final List<LintFileIssue> issues;
	
	public LintFile(final String name) {
		this.name = name;
		issues = new ArrayList<LintFileIssue>();
	}

	public LintFileIssue addIssue(int line, int column, String reason, String evidence, String severity) {
		LintFileIssue issue = new LintFileIssue(this);
		issue.setLine(line);
		issue.setColumn(column);
		issue.setReason(reason);
		issue.setEvidence(evidence);
		issue.setSeverity(severity);
		issues.add(issue);
		return issue;
	}

	public List<LintFileIssue> getIssues() {
		return issues;
	}
	
	public String getName() {
		return name;
	}
}
