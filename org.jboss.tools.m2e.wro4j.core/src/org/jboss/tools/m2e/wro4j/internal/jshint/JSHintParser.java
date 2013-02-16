package org.jboss.tools.m2e.wro4j.internal.jshint;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class JSHintParser {
	public JSHintParser() {

	}

	public Lint parse(File file) throws ParserConfigurationException,
			SAXException, IOException {
		Lint lint = new Lint();
		if (file.exists()) {
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder builder = docBuilderFactory.newDocumentBuilder();
			Document doc = builder.parse(file);
			Element rootElement = doc.getDocumentElement();
			NodeList childNodes = rootElement.getChildNodes();
			if (childNodes != null) {
				int len = childNodes.getLength();
				for (int i = 0; i < len; i++) {
					Node n = childNodes.item(i);
					if (n.getNodeType() == Node.ELEMENT_NODE
							&& n.getNodeName().equals("file")) {
						final Element fileElem = (Element) n;
						parseFileElement(lint, fileElem);
					}
				}
			}
		}
		return lint;
	}

	private void parseFileElement(final Lint lint, final Element fileElem) {
		String fileName = fileElem.getAttribute("name");
		LintFile lintFile = lint.addFile(fileName);
		NodeList fileChildren = fileElem.getChildNodes();
		if (fileChildren != null) {
			int fileChildLen = fileChildren.getLength();
			for (int fileIdx = 0; fileIdx < fileChildLen; fileIdx++) {
				Node cn = fileChildren.item(fileIdx);
				if (cn.getNodeType() == Node.ELEMENT_NODE
						&& cn.getNodeName().equals("issue")) {
					final Element issueElem = (Element) cn;
					parseLintFileIssue(lintFile, issueElem);
				}
			}
		}
	}

	private void parseLintFileIssue(final LintFile lintFile,
			final Element issueElem) {
		final String reason = issueElem.getAttribute("reason");
		final String evidence = issueElem.getAttribute("evidence");
		final int line = Integer.parseInt(issueElem.getAttribute("line"));
		final int col = Integer.parseInt(issueElem.getAttribute("char"));
		String severity = "warning";
		if (issueElem.hasAttribute("severity")) {
			severity = issueElem.getAttribute("severity");
		}
		lintFile.addIssue(line, col, reason, evidence, severity);
	}
}