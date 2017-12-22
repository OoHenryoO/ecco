package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.listener.WriteListener;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.java.JavaTreeArtifactData.NodeType;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.formatter.DefaultCodeFormatter;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static at.jku.isse.ecco.adapter.java.JavaTreeArtifactData.NodeType.*;

public class JavaWriter implements ArtifactWriter<Set<Node>, Path> {
    @Override
    public String getPluginId() {
        return JavaPlugin.getPluginIdStatic();
    }


    @Override
    public Path[] write(Set<Node> input) {
        return write(Paths.get("."), input);
    }

    Collection<WriteListener> writeListeners = new ArrayList<>();

    @Override
    public void addListener(WriteListener listener) {
        writeListeners.add(listener);
    }

    @Override
    public void removeListener(WriteListener listener) {
        writeListeners.remove(listener);
    }

    //Out: Array of changed files
    @Override
    public Path[] write(Path base, Set<Node> input) {
        Path[] toreturn = input.parallelStream().map(node -> processNode(node, base)).filter(Objects::nonNull).toArray(Path[]::new);
        if (toreturn.length != input.size())
            throw new IllegalStateException("Not all files could be written!");
        return toreturn;
    }


    /**
     * @param baseNode The base node which should be processed
     * @param basePath The base path (need to parse package hierarchy
     * @return The path were the file got placed
     */
    private Path processNode(Node baseNode, Path basePath) {
        if (!(baseNode.getArtifact().getData() instanceof PluginArtifactData)) return null;
        PluginArtifactData rootData = (PluginArtifactData) baseNode.getArtifact().getData();
        final List<? extends Node> children = baseNode.getChildren();
        if (children.size() != 1)
            return null;

        //These  are import / package and class nodes
        final List<? extends Node> javaFileNodes = children.get(0).getChildren();

        Path returnPath = basePath.resolve(rootData.getPath());
        StringBuilder stringBuilder = new StringBuilder();

        //Rebuild Java file
        javaFileNodes.forEach(childNode -> {
            if (childNode.getArtifact().getData() instanceof JavaTreeArtifactData) {
                final JavaTreeArtifactData artifactData = (JavaTreeArtifactData) childNode.getArtifact().getData();
                processJavaAst(stringBuilder, childNode, artifactData);
            }
        });

        try {
            formatCodeAndWriteFile(returnPath, stringBuilder.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return returnPath;
    }

    private void formatCodeAndWriteFile(Path path, String contents) throws Exception {
        // format code string
        CodeFormatter cf = new DefaultCodeFormatter();
        TextEdit te = cf.format(CodeFormatter.K_COMPILATION_UNIT, contents, 0, contents.length(), 0, null);
        IDocument dc = new Document(contents);
        path = path.toAbsolutePath();
        final Path parentFolder = path.getParent();
        Files.createDirectories(parentFolder);
        try (BufferedWriter fileWriter = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            te.apply(dc);
            String formattedContent = dc.get();
            fileWriter.write(formattedContent);
        }
    }

    private void processJavaAst(StringBuilder stringBuilder, NodeArtifactEntry nodeArtifactEntry) {
        processJavaAst(stringBuilder, nodeArtifactEntry.getNode(), nodeArtifactEntry.getArtifact());
    }

    private void processJavaAst(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        final NodeType curNodeType = artifactData.getType();
        if (curNodeType == null)
            stringBuilder.append(artifactData.getDataAsString());
        else {
            switch (curNodeType) {
                case TYPE_DECLARATION:
                case ANNOTATIONTYPE_DECLARATION:
                case FIELD_DECLARATION:
                case METHOD_DECLARATION:
                case ENUM_DECLARATION:
                case ANNOTATIONMEMBER:
                case STATEMENT_VARIABLE_DECLARATION:
                case BLOCK:
                case DIMENSION:
                    String modifiers = findChildren(curNode, MODIFIER)
                            .map(e -> e.getNode().getChildren())
                            .flatMap(Collection::stream)
                            .map(NodeArtifactEntry::fromNode)
                            .filter(Objects::nonNull)
                            .map(e -> e.getArtifact().getDataAsString())
                            .collect(Collectors.joining(" ", " ", " "));
                    stringBuilder.append(modifiers);
                    break;
            }
            // Handle rest
            switch (curNodeType) {
                case EXPRESSION_PREFIX:
                case SIMPLE_JUST_A_STRING:
                    stringBuilder.append(artifactData.getDataAsString());
                    break;
                case EXPRESSION_VARIABLE_DECLARATION:
                    handleVariableDeclarationExpression(stringBuilder, curNode, artifactData);
                    break;
                case ASSIGNMENT:
                    handleAssignment(stringBuilder, curNode, artifactData);
                    break;
                case LAMBDA:
                    handleLambda(stringBuilder, curNode, artifactData);
                    break;
                case PARAMETERS:
                    handleParameters(stringBuilder, curNode, artifactData);
                    break;
                case METHOD_INVOCATION:
                    handleMethodInvokation(stringBuilder, curNode, artifactData);
                    break;
                case STATEMENT_EXPRESSION:
                    handleStatementExpression(stringBuilder, curNode, artifactData);
                    break;
                case STATEMENT_VARIABLE_DECLARATION:
                    handleVariableDeclarationStatement(stringBuilder, curNode, artifactData);
                    break;
                case VARIABLE_DECLARATION_FRAGMENT:
                    handleVariableDeclarationFragment(stringBuilder, curNode, artifactData);
                    break;
                case LOOP_FOR:
                    handleVariableForLoop(stringBuilder, curNode, artifactData);
                    break;
                case METHOD_DECLARATION:
                    handleMethodDeclaration(stringBuilder, curNode, artifactData);
                    break;
                case ANONYMOUS_CLASS_DECLARATION:
                case BLOCK:
                    handleBlockDeclaration(stringBuilder, curNode, artifactData);
                    break;
                case CLASS_INSTANCE_CREATION:
                    handleClassInstanceCreation(stringBuilder, curNode, artifactData);
                    break;
                case SYNCHRONIZED_STATEMENT:
                    handleSynchronizedStatement(stringBuilder, curNode, artifactData);
                    break;
                case TYPE_DECLARATION:
                    handleTypeDeclaration(stringBuilder, curNode, artifactData);
                    break;
                case MODIFIER:
                    //should be handled above
                    break;
                case SWITCH_SWITCH:
                    handleSwitchCase(stringBuilder, curNode, artifactData);
                    break;
                case SWITCH_CASE:
                    handleSwitchCaseCase(stringBuilder, curNode, artifactData);
                    break;
                case THROW_STATEMENT:
                    handleThrowStatement(stringBuilder, curNode, artifactData);
                    break;
                case EXPRESSION_PARENTHESIS:
                    handleExpressionParenthesis(stringBuilder, curNode, artifactData);
                    break;
                case LOOP_WHILE:
                    handleWhileLoop(stringBuilder, curNode, artifactData);
                    break;
                case LOOP_DO_WHILE:
                    handleDoWhileLoop(stringBuilder, curNode, artifactData);
                    break;
                case ENUM_DECLARATION:
                    handleEnumDeclaration(stringBuilder, curNode, artifactData);
                    break;
                case STATEMENT_IFFS:
                    handleIfStatements(stringBuilder, curNode, artifactData);
                    break;
                case EXPRESSION_CAST:
                    handleCast(stringBuilder, curNode, artifactData);
                    break;
                case EXPRESSION_TRENARY:
                    handleTrenaryExpression(stringBuilder, curNode, artifactData);
                    break;
                case ANNOTATIONTYPE_DECLARATION:
                    handleAnnotationTypeDeclaration(stringBuilder, curNode, artifactData);
                    break;
                case ANNOTATIONMEMBER:
                    handleAnnotationmember(stringBuilder, curNode, artifactData);
                    break;
                case ANNOTATIONMEMBER_DEFAULT:
                    handleAnnotationmemberDefault(stringBuilder, curNode, artifactData);
                    break;
                case STATEMENT_RETURN:
                    handleReturnStatement(stringBuilder, curNode, artifactData);
                    break;
                case LOOP_ENHANCED_FOR:
                    handleAdvancedFor(stringBuilder, curNode, artifactData);
                    break;
                case TRY_META:
                    handleTry(stringBuilder, curNode, artifactData);
                    break;
                case CATCH:
                    handleCatch(stringBuilder, curNode, artifactData);
                    break;
                case FIELD_DECLARATION:
                    handleFieldDeclaration(stringBuilder, curNode, artifactData);
                    break;
                case STATEMENT_ELSE:
                    handleElse(stringBuilder, curNode, artifactData);
                    break;
                case STATEMENT_ASSERT:
                    handleAssert(stringBuilder, curNode, artifactData);
                    break;
                case LAMBDA_PARAMETERS:
                    handleLambdaParameters(stringBuilder, curNode, artifactData);
                    break;
                default:
                    throw new IllegalStateException(curNodeType + " is not supported");
            }
        }
    }

    private void handleLambdaParameters(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        final List<? extends Node> children = curNode.getChildren();
        handleCommaSeperatedExpressions(children, stringBuilder);
    }

    private void handleAssert(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("assert ");

        processJavaAstForChildsChildren(curNode, STATEMENT_ASSERT_CONDITION, stringBuilder);

        final boolean hasMessage = findChildren(curNode, STATEMENT_ASSERT_MESSAGE).map(e -> e.getNode().getChildren()).mapToInt(List::size).sum() > 0;
        if (hasMessage) {
            stringBuilder.append(':');
            processJavaAstForChildsChildren(curNode, STATEMENT_ASSERT_MESSAGE, stringBuilder);
        }

        stringBuilder.append(';');

    }

    private void handleElse(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("else ");
        getChildrenAsStream(curNode).forEach(it -> processJavaAst(stringBuilder, it));
    }

    private void handleFieldDeclaration(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(' ').append(artifactData.getDataAsString()).append(' ');

        final List<NodeArtifactEntry> variableDeclarationFragments = findChildren(curNode, VARIABLE_DECLARATION_FRAGMENT).collect(Collectors.toList());

        final int maxComma = variableDeclarationFragments.size() - 1;
        for (int i = 0; i < variableDeclarationFragments.size(); i++) {
            final NodeArtifactEntry nae = variableDeclarationFragments.get(i);
            processJavaAst(stringBuilder, nae);
            if (i < maxComma)
                stringBuilder.append(',');
        }

        stringBuilder.append(';');
    }

    private void handleTry(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("try");
        final boolean tryRessources = findChildren(curNode, TRY_RESSOURCES).mapToInt(e -> e.getNode().getChildren().size()).sum() > 0;
        if (tryRessources) {
            stringBuilder.append('(');

            processJavaAstForChildsChildren(curNode, TRY_RESSOURCES, stringBuilder);

            stringBuilder.append(')');
        }

        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
        processJavaAstForChildsChildren(curNode, CATCH_META, stringBuilder);

        final boolean finallyStatement = findChildren(curNode, FINALLY).mapToInt(e -> e.getNode().getChildren().size()).sum() > 0;
        if (finallyStatement) {
            stringBuilder.append("finally");
            processJavaAstForChildsChildren(curNode, FINALLY, stringBuilder);
        }
    }

    private void handleCatch(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(" catch(").append(artifactData.getDataAsString()).append(')');
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }

    private void handleAdvancedFor(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("for(").append(artifactData.getDataAsString()).append(':');

        processJavaAstForChildsChildren(curNode, BEFORE, stringBuilder);

        stringBuilder.append(')');

        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }

    private void handleReturnStatement(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("return ");
        getChildrenAsStream(curNode).forEach(it -> processJavaAst(stringBuilder, it));
        stringBuilder.append(';');
    }

    private void handleAnnotationmemberDefault(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(" default ");
        getChildrenAsStream(curNode).forEach(it -> processJavaAst(stringBuilder, it));
    }

    private void handleAnnotationmember(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());
        getChildrenAsStream(curNode).forEach(it -> processJavaAst(stringBuilder, it));
        stringBuilder.append(';');
    }

    private void handleAnnotationTypeDeclaration(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());
        findChildren(curNode, BLOCK).forEach(it -> processJavaAst(stringBuilder, it));
    }

    private void handleTrenaryExpression(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        processJavaAstForChildsChildren(curNode, CONDITION, stringBuilder);
        stringBuilder.append('?');
        processJavaAstForChildsChildren(curNode, BEFORE, stringBuilder);
        stringBuilder.append(':');
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }

    private void handleCast(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());
        getChildrenAsStream(curNode).forEach(it -> processJavaAst(stringBuilder, it));

    }

    private void handleIfStatements(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        StringJoiner stringJoiner = new StringJoiner("else ");
        findChildren(curNode, STATEMENT_IF).forEach(it -> handleIfStatement(stringJoiner, it));
        stringBuilder.append(stringJoiner.toString());
        findChildren(curNode, STATEMENT_ELSE).forEach(it -> processJavaAst(stringBuilder, it));

    }

    private void handleIfStatement(StringJoiner stringBuilder, NodeArtifactEntry nae) {
        handleIfStatement(stringBuilder, nae.getNode(), nae.getArtifact());
    }

    private void handleIfStatement(StringJoiner stringJoiner, Node curNode, JavaTreeArtifactData artifactData) {
        StringBuilder ifStatementAsString = new StringBuilder();
        ifStatementAsString.append(artifactData.getDataAsString());

        getChildrenAsStream(curNode).forEach(it -> processJavaAst(ifStatementAsString, it));

        stringJoiner.add(ifStatementAsString.toString());
    }

    private void handleEnumDeclaration(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());
        processJavaAstForChildsChildren(curNode, DECLARATION_IMPLEMENTS, stringBuilder);
        stringBuilder.append('{');
        String enumConstants = findChildren(curNode, ENUM_CONSTANTS).map(e -> e.getNode()).map(this::subelementsCommaSeperated).collect(Collectors.joining(","));
        stringBuilder.append(enumConstants).append(';');
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
        stringBuilder.append('}');
    }

    private void handleWhileLoop(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("while(");
        processJavaAstForChildsChildren(curNode, CONDITION, stringBuilder);
        stringBuilder.append(')');
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }

    private void handleDoWhileLoop(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("do ");
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
        stringBuilder.append(" while(");
        processJavaAstForChildsChildren(curNode, CONDITION, stringBuilder);
        stringBuilder.append(");");
    }


    private void handleExpressionParenthesis(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append('(');

        getChildrenAsStream(curNode).forEach(e -> processJavaAst(stringBuilder, e));

        stringBuilder.append(')');
    }

    private void handleThrowStatement(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("throw ");
        getChildrenAsStream(curNode).forEach(c -> processJavaAst(stringBuilder, c));
        stringBuilder.append(';');
    }

    private void handleSwitchCaseCase(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());
        getChildrenAsStream(curNode).forEach(e -> processJavaAst(stringBuilder, e));
    }

    private void handleSwitchCase(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString()).append('{');

        getChildrenAsStream(curNode).forEach(e -> processJavaAst(stringBuilder, e));

        stringBuilder.append('}');
    }

    private void handleMethodInvokation(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        boolean hasChild = processJavaAstForChildsChildren(curNode, BEFORE, stringBuilder);
        if (hasChild)
            stringBuilder.append('.');
        stringBuilder.append(artifactData.getDataAsString());
        getChildrenAsStream(curNode).filter(e -> !BEFORE.equals(e.getArtifact().getType())).forEach(it -> processJavaAst(stringBuilder, it));
    }

    private void handleParameters(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        final List<? extends Node> children = curNode.getChildren();
        stringBuilder.append('(');

        handleCommaSeperatedExpressions(children, stringBuilder);
        stringBuilder.append(')');
    }

    private void handleClassInstanceCreation(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(" new ").append(artifactData.getDataAsString());

        findChildren(curNode, PARAMETERS).forEach(it -> processJavaAst(stringBuilder, it));

        getChildrenAsStream(curNode).filter(it -> !PARAMETERS.equals(it.getArtifact().getType())).forEach(it -> processJavaAst(stringBuilder, it));
    }

    private void handleStatementExpression(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        getChildrenAsStream(curNode).forEach(it -> processJavaAst(stringBuilder, it));
        stringBuilder.append(';');
    }

    private void handleAssignment(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        processJavaAstForChildsChildren(curNode, BEFORE, stringBuilder);
        stringBuilder.append(artifactData.getDataAsString());
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }

    private void handleVariableForLoop(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("for(");
        processJavaAstForChildsChildren(curNode, FOR_INITALIZER, stringBuilder);
        stringBuilder.append(';');
        processJavaAstForChildsChildren(curNode, CONDITION, stringBuilder);
        stringBuilder.append(';');
        final List<? extends Node> updaterNodes = findChildren(curNode, FOR_UPDATERS).map(e -> e.getNode().getChildren()).flatMap(Collection::stream).collect(Collectors.toList());
        handleCommaSeperatedExpressions(updaterNodes, stringBuilder);
        stringBuilder.append(')');
        processJavaAstForChildsChildren(curNode, AFTER, stringBuilder);
    }


    private void handleLambda(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        final boolean hasParanthesis = Boolean.parseBoolean(artifactData.getDataAsString());

        if (hasParanthesis)
            stringBuilder.append('(');

        findChildren(curNode, LAMBDA_PARAMETERS).forEach(it -> processJavaAst(stringBuilder, it));

        if (hasParanthesis)
            stringBuilder.append(')');

        stringBuilder.append("->");

        getChildrenAsStream(curNode).filter(e -> !LAMBDA_PARAMETERS.equals(e.getArtifact().getType())).forEach(nae -> processJavaAst(stringBuilder, nae));
    }

    private void handleSynchronizedStatement(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append("synchronized(").append(artifactData.getDataAsString()).append(')');
        curNode.getChildren().stream().map(NodeArtifactEntry::fromNode).forEach(it -> processJavaAst(stringBuilder, it));
    }

    private void handleVariableDeclarationExpression(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString()).append(' ');
        final List<? extends Node> children = curNode.getChildren();
        //Ignore index [0] --> mofifier
        final List<? extends Node> variabledeclarationFragments = children.subList(1, children.size());
        int maxComma = variabledeclarationFragments.size() - 1;

        for (int i = 0; i < variabledeclarationFragments.size(); i++) {
            final Node node = variabledeclarationFragments.get(i);
            processJavaAst(stringBuilder, NodeArtifactEntry.fromNode(node));
            if (i < maxComma)
                stringBuilder.append(',');
        }
    }

    private void handleVariableDeclarationStatement(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        handleVariableDeclarationExpression(stringBuilder, curNode, artifactData);
        stringBuilder.append(';');
    }

    private boolean processJavaAstForChildsChildren(Node node, NodeType nodeType, StringBuilder stringBuilder) {
        return findChildren(node, nodeType).map(NodeArtifactEntry::getNode).map(Node::getChildren).flatMap(Collection::stream)
                .map(NodeArtifactEntry::fromNode).peek(it -> processJavaAst(stringBuilder, it)).findAny().isPresent();
    }

    private void handleVariableDeclarationFragment(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());

        curNode.getChildren().stream().map(NodeArtifactEntry::fromNode).forEach(nae -> processJavaAst(stringBuilder, nae));
    }

    private void handleBlockDeclaration(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append('{');
        curNode.getChildren().stream().map(NodeArtifactEntry::fromNode).forEach(nae -> processJavaAst(stringBuilder, nae));
        stringBuilder.append('}');
    }

    private void handleMethodDeclaration(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        handleGenerics(curNode, stringBuilder);

        stringBuilder.append(artifactData.getDataAsString());

        findChildren(curNode, THROWS_LIST).forEach(throwsNode -> {
            stringBuilder.append("throws ");
            final String throwsElements = subelementsCommaSeperated(throwsNode);
            stringBuilder.append(throwsElements);
        });

        if (hasChild(curNode, BLOCK))
            findChildren(curNode, BLOCK).forEach(nae -> processJavaAst(stringBuilder, nae));
        else
            stringBuilder.append(';');
    }

    private void handleGenerics(Node curNode, StringBuilder stringBuilder) {
        final List<? extends Node> genericArgs = findChildren(curNode, GENERIC_TYPE_INFO).map(nae -> nae.getNode().getChildren()).flatMap(Collection::stream).collect(Collectors.toList());
        if (genericArgs.size() == 0)
            return;
        stringBuilder.append('<');

        StringJoiner sj = new StringJoiner(",");
        for (Node n : genericArgs) {
            final JavaTreeArtifactData data = (JavaTreeArtifactData) n.getArtifact().getData();
            assert SIMPLE_JUST_A_STRING.equals(data.getType());

            sj.add(data.getDataAsString());
        }

        stringBuilder.append(sj.toString()).append('>');
    }

    private void handleTypeDeclaration(StringBuilder stringBuilder, Node curNode, JavaTreeArtifactData artifactData) {
        stringBuilder.append(artifactData.getDataAsString());

        handleGenerics(curNode, stringBuilder);

        stringBuilder.append(' ');
        final String extendsPart = findChildren(curNode, DECLARATION_EXTENDS).map(this::subelementsCommaSeperated).collect(Collectors.joining(" "));
        final String implementsPart = findChildren(curNode, DECLARATION_IMPLEMENTS).map(this::subelementsCommaSeperated).collect(Collectors.joining(" "));
        if (!extendsPart.isEmpty())
            stringBuilder.append(" extends ").append(extendsPart);
        if (!implementsPart.isEmpty())
            stringBuilder.append(" implements ").append(implementsPart);
        stringBuilder.append('{');
        //Recursion -> body elements
        final NodeArtifactEntry afterNode = findChildren(curNode, AFTER).findFirst().orElse(null);
        if (afterNode != null) {
            getChildrenAsStream(afterNode).forEach(nae -> processJavaAst(stringBuilder, nae));
        }
        stringBuilder.append('}');
    }

    private Stream<NodeArtifactEntry> getChildrenAsStream(NodeArtifactEntry nae) {
        return getChildrenAsStream(nae.getNode());
    }

    private Stream<NodeArtifactEntry> getChildrenAsStream(Node node) {
        return node.getChildren().stream()
                .map(NodeArtifactEntry::fromNode)
                .filter(Objects::nonNull);
    }

    private String subelementsCommaSeperated(NodeArtifactEntry entry) {
        return subelementsCommaSeperated(entry.getNode());
    }

    private String subelementsCommaSeperated(Node node) {
        return node.getChildren().stream().map(this::mapToJavaArtifact)
                .map(JavaTreeArtifactData::getDataAsString).collect(Collectors.joining(","));

    }

    private JavaTreeArtifactData mapToJavaArtifact(Node node) {
        final ArtifactData data = node.getArtifact().getData();
        if (data instanceof JavaTreeArtifactData)
            return (JavaTreeArtifactData) data;
        return null;
    }


    private Stream<NodeArtifactEntry> findChildren(Node node, NodeType nodeType) {
        return node.getChildren().stream().sequential().
                filter(e -> nodeType.equals(((JavaTreeArtifactData) e.getArtifact().getData()).getType())).map(NodeArtifactEntry::fromNode);
    }

    private boolean hasChild(Node node, NodeType nodeType) {
        return node.getChildren().stream().
                anyMatch(e -> nodeType.equals(((JavaTreeArtifactData) e.getArtifact().getData()).getType()));
    }


    private void handleCommaSeperatedExpressions(List<? extends Node> children, StringBuilder stringBuilder) {
        final int maxComma = children.size() - 1;
        int i = 0;
        for (Node fragment : children) {
            JavaTreeArtifactData data = (JavaTreeArtifactData) fragment.getArtifact().getData();
            processJavaAst(stringBuilder, fragment, data);
            if (i < maxComma)
                stringBuilder.append(',');
            i++;
        }
    }

}