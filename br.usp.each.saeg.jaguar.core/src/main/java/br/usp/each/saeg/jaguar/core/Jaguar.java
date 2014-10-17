package br.usp.each.saeg.jaguar.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import javax.xml.bind.JAXB;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.data.ExecutionDataStore;

import br.usp.each.saeg.jaguar.core.builder.CodeForestXmlBuilder;
import br.usp.each.saeg.jaguar.core.heuristic.Heuristic;
import br.usp.each.saeg.jaguar.core.heuristic.HeuristicCalculator;
import br.usp.each.saeg.jaguar.core.infra.StringUtils;
import br.usp.each.saeg.jaguar.core.model.core.CoverageStatus;
import br.usp.each.saeg.jaguar.core.model.core.TestRequirement;

/**
 * This class store the coverage information received from Jacoco and generate a
 * rank of more suspicious test requirement based on one SFL Heuristic.
 * 
 * @author Henrique Ribeiro
 */
public class Jaguar {

	private static final String XML_NAME = "codeforest.xml";
	private int nTests = 0;
	private int nTestsFailed = 0;
	private HashMap<Integer, TestRequirement> testRequirements = new HashMap<Integer, TestRequirement>();
	private Heuristic heuristic;
	private File classesDir;

	/**
	 * Construct the Jaguar object.
	 * 
	 * @param heuristic
	 *            the heuristic to be used on the fault localization rank.
	 * @param targetDir
	 *            the target dir created by eclipse
	 */
	public Jaguar(Heuristic heuristic, File classesDir) {
		this.heuristic = heuristic;
		this.classesDir = classesDir;
	}

	/**
	 * Receive the coverage information and store it on Test Requiremtns.
	 * 
	 * @param executionData
	 *            the covarege data from Jacoco
	 * @param currentTestFailed
	 *            result of the test
	 * 
	 */
	public void collect(final ExecutionDataStore executionData, boolean currentTestFailed) {
		final CoverageBuilder coverageVisitor = new CoverageBuilder();
		Analyzer analyzer = new Analyzer(executionData, coverageVisitor);

		try {
			analyzer.analyzeAll(classesDir);
		} catch (IOException e) {
			e.printStackTrace();
		}

		for (IClassCoverage clazz : coverageVisitor.getClasses()) {
			CoverageStatus coverageStatus = CoverageStatus.as(clazz.getClassCounter().getStatus());
			if (CoverageStatus.FULLY_COVERED == coverageStatus || CoverageStatus.PARTLY_COVERED == coverageStatus) {
				int firstLine = clazz.getFirstLine();
				int lastLine = clazz.getLastLine();
				if (firstLine >= 0) {
					for (int currentLine = firstLine; currentLine <= lastLine; currentLine++) {
						ILine line = clazz.getLine(currentLine);
						coverageStatus = CoverageStatus.as(line.getStatus());
						if (CoverageStatus.FULLY_COVERED == coverageStatus
								|| CoverageStatus.PARTLY_COVERED == coverageStatus) {
							updateRequirement(clazz, currentLine, currentTestFailed);
						}
					}
				}
			}
		}
	}

	/**
	 * Update the testRequirement info. If it does not exist, create a new one.
	 * If the test has failed, increment the cef (coefficient of executed and
	 * failed) If the test has passed, increment the cep (coefficient of
	 * executed and passed)
	 * 
	 * @param clazz
	 *            the class name, including package
	 * @param lineNumber
	 *            the line number
	 * @param failed
	 *            if the test has failed
	 * 
	 */
	private void updateRequirement(IClassCoverage clazz, int lineNumber, boolean failed) {
		TestRequirement testRequirement = new TestRequirement(clazz.getName(), lineNumber);
		TestRequirement foundRequirement = testRequirements.get(testRequirement.hashCode());

		if (foundRequirement == null) {
			testRequirement.setClassFirstLine(clazz.getFirstLine());
			Collection<IMethodCoverage> methods = clazz.getMethods();
			Integer methodId = 0;
			for (IMethodCoverage method : methods) {
				methodId++;
				if (method.getLine(lineNumber) != org.jacoco.core.internal.analysis.LineImpl.EMPTY) {
					testRequirement.setMethodLine(method.getFirstLine());
					String parametros = StringUtils.getParametros(method.getDesc());
					testRequirement.setMethodSignature(method.getName() + "(" + parametros + ")");
					testRequirement.setMethodId(methodId);
				}
			}
			testRequirements.put(testRequirement.hashCode(), testRequirement);
		} else {
			testRequirement = foundRequirement;
		}

		if (failed) {
			testRequirement.increaseFailed();
		} else {
			testRequirement.increasePassed();
		}
	}

	/**
	 * Calculate the rank based on the heuristic and testRequirements. Print the
	 * rank in descending order.
	 * 
	 * @return
	 * 
	 */
	public ArrayList<TestRequirement> generateRank() {
		System.out.println("Rank calculation started...");
		HeuristicCalculator calc = new HeuristicCalculator(heuristic, testRequirements.values(), nTests - nTestsFailed,
				nTestsFailed);
		ArrayList<TestRequirement> result = calc.calculateRank();
		System.out.println("Rank calculation finished.");
		return result;
	}

	// TODO javadoc
	/**
	 * 
	 * @param testRequirements
	 * @param projectDir
	 */
	public void generateXML(ArrayList<TestRequirement> testRequirements, File projectDir) {
		System.out.println("XML generation started.");
		CodeForestXmlBuilder xmlBuilder = new CodeForestXmlBuilder();
		xmlBuilder.project("fault localization");
		xmlBuilder.heuristic(heuristic);
		xmlBuilder.requirementType("LINE");
		for (TestRequirement testRequirement : testRequirements) {
			xmlBuilder.addTestRequirement(testRequirement);
		}
		File xmlFile = new File(projectDir.getAbsolutePath() + System.getProperty("file.separator") + XML_NAME);
		JAXB.marshal(xmlBuilder.build(), xmlFile);
		System.out.println("XML generation finished at " + xmlFile.getAbsolutePath());
	}

	public int getnTests() {
		return nTests;
	}

	public int getnTestsFailed() {
		return nTestsFailed;
	}

	public int increaseNTests() {
		return ++nTests;
	}

	public int increaseNTestsFailed() {
		return ++nTestsFailed;
	}

}
