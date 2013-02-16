package org.jboss.tools.m2e.wro4j.internal.jshint;

public class LintFileIssue {
	private final LintFile file;
	
	private int column;
	private int line;
	private String evidence;
	private String reason;
	private String severity;
	
	public LintFileIssue(final LintFile file) {
		this.file = file;
	}

	public int getColumn() {
		return column;
	}

	public String getEvidence() {
		return evidence;
	}

	public LintFile getFile() {
		return file;
	}

	public int getLine() {
		return line;
	}

	public String getReason() {
		return reason;
	}

	public String getSeverity() {
		return severity;
	}

	public void setColumn(int column) {
		this.column = column;
	}

	public void setEvidence(String evidence) {
		this.evidence = evidence;
	}

	public void setLine(int line) {
		this.line = line;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public void setSeverity(String severity) {
		this.severity = severity;
	}
}
