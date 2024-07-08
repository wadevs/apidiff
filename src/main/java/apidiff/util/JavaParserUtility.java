package apidiff.util;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Helper to parse Java code.
 *
 * @author Manuel Ohrndorf
 *
 *         adapted slightly by Lukas Bosshart
 */
public class JavaParserUtility {

	/**
	 * The AST's Java language specification used for parsing.
	 */
	@SuppressWarnings("deprecation")
	public static int JAVA_LANGUAGE_SPECIFICATION = AST.JLS8; // getJLSLatest(); // Latest long term support version of
																// Java.

	/**
	 * Represents a code token mapped to an AST node.
	 */
	public static class Token {
		public ASTNode node;
		public List<ASTNode> additionalNodes = new ArrayList<>();
		public String code;
		public int start;
		public int end;

	}

	/**
	 * Parses a single Java source file.
	 *
	 * @param source            The Java source file.
	 * @param parseMethodBodies <code>true</code> if the method bodies should be
	 *                          parsed in the AST; <code>false</code> to ignore
	 *                          method bodies.
	 * @return The source code mapped to the parsed Java AST.
	 */
	public static TypeDeclaration parseExpression(String source, boolean parseMethodBodies) {
		ASTParser parser = ASTParser.newParser(JAVA_LANGUAGE_SPECIFICATION);
		parser.setKind(ASTParser.K_CLASS_BODY_DECLARATIONS);
		parser.setSource(source.toCharArray());
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true); // generates binding also for none resolvable/missing types
		parser.setIgnoreMethodBodies(!parseMethodBodies);
		parser.setUnitName("unit_name");
		parser.setEnvironment(null, new String[] {}, null, false);
		return (TypeDeclaration) parser.createAST(null);
	}

	/**
	 * Parses a single Java source file.
	 *
	 * @param source            The Java source file.
	 * @param parseMethodBodies <code>true</code> if the method bodies should be
	 *                          parsed in the AST; <code>false</code> to ignore
	 *                          method bodies.
	 * @return The source code mapped to the parsed Java AST.
	 */
	public static CompilationUnit parse(String source, boolean parseMethodBodies) {
		ASTParser parser = ASTParser.newParser(JAVA_LANGUAGE_SPECIFICATION);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true); // generates binding also for none resolvable/missing types
		parser.setIgnoreMethodBodies(!parseMethodBodies);
		parser.setUnitName("unit_name");
		parser.setEnvironment(null, new String[] {}, null, false);
		return (CompilationUnit) parser.createAST(null);
	}

	/**
	 * Maps the Java AST nodes to tokens in the code.
	 *
	 * @param source The Java source file.
	 * @param unit   The parsed Java AST.
	 * @return The Java AST node to tokens mapping.
	 */
	public static List<Token> tokensToAST(String source, CompilationUnit unit) throws InvalidInputException {
		// Sort tokens:
		SortedMap<Integer, Token> sortedTokens = new TreeMap<>();
		List<Token> scannedTokens = scan(source);
		scannedTokens.forEach(token -> sortedTokens.put(token.start, token));

		// Visit AST:
		unit.accept(new ASTVisitor(true) {

			@Override
			public void preVisit(ASTNode node) {
				int nodeStart = node.getStartPosition();
				int nodeEnd = nodeStart + node.getLength();

				// Find tokens in range, assuming tokens are traversed from small to the largest
				// segment start position:
				for (Token token : sortedTokens.tailMap(nodeStart).values()) {
					// Is token in node range?
					if (nodeStart <= token.start && token.end <= nodeEnd) {
						if (token.node != null && token.node.getStartPosition() == nodeStart
								&& token.node.getLength() == node.getLength()) {
							token.additionalNodes.add(node);
						} else {
							token.node = node; // assign token
							token.additionalNodes.clear();
						}
					} else {
						break;
					}
				}
			}
		});

		return scannedTokens;
	}

	/**
	 * Scanner for a single Java source file.
	 *
	 * @param source The Java source file.
	 * @return A list of tokens representing the source file.
	 */
	public static List<Token> scan(String source) throws InvalidInputException {
		IScanner scanner = ToolFactory.createScanner(false, true, true, "" + JAVA_LANGUAGE_SPECIFICATION);
		int sourceLength = source.length();
		scanner.setSource(source.toCharArray());

		List<Token> tokens = new ArrayList<>();

		while (scanner.getCurrentTokenEndPosition() < sourceLength - 1) {
			scanner.getNextToken();

			Token codeToken = new Token();
			codeToken.start = scanner.getCurrentTokenStartPosition();
			codeToken.end = scanner.getCurrentTokenEndPosition();
			if (codeToken.end >= source.length()) {
				continue;
			}
			codeToken.code = source.substring(codeToken.start, codeToken.end + 1);

			tokens.add(codeToken);
		}

		return tokens;
	}
}
