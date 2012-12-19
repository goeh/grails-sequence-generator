/*
 * Copyright (c) 2012 Rico Krasowski.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package grails.plugins.sequence;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.BooleanExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.grails.compiler.injection.GrailsASTUtils;
import org.codehaus.groovy.syntax.Token;

import java.lang.reflect.Modifier;
import java.util.List;

final class BeforeValidateInjection {

    private static final Token ASSIGN_OPERATOR = Token.newSymbol("=", -1, -1);
    private static final Token EQUALS_OPERATOR = Token.newSymbol("==", -1, -1);

    public static void generate(ClassNode clazz, String fieldName) {
        MethodNode beforeValidateMethod = findMethod(clazz);
        if (beforeValidateMethod == null) {
            addMethod(clazz, fieldName);
        }
        else {
            injectMethod(beforeValidateMethod, fieldName);
        }
    }

    private static MethodNode findMethod(ClassNode clazz) {
        final List<MethodNode> methods = clazz.getMethods("beforeValidate");
        if (methods != null && methods.size() > 0) {
            for (MethodNode methodNode : methods) {
                if (methodNode.getParameters().length == 0) {
                    return methodNode;
                }
            }
        }
        return null;
    }

    private static void addMethod(ClassNode clazz, String fieldName) {
        final BlockStatement block = new BlockStatement();
        block.addStatement(createBeforeValidateAst(fieldName));
        block.addStatement(new ReturnStatement(GrailsASTUtils.NULL_EXPRESSION));
        clazz.addMethod(new MethodNode("beforeValidate", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, block));
    }

    private static void injectMethod(MethodNode methodNode, String fieldName) {
        final BlockStatement methodBody = (BlockStatement) methodNode.getCode();
        List<Statement> statements = methodBody.getStatements();
        statements.add(0, createBeforeValidateAst(fieldName));
    }

    /**
     * Generates Statement:
     * if (fieldName == null) fieldName = this.getNextSequenceNumber();
     */
    private static Statement createBeforeValidateAst(String fieldName) {
        return new IfStatement(
                /* (fieldName == null) */
                new BooleanExpression(new BinaryExpression(
                        new VariableExpression(fieldName),
                        EQUALS_OPERATOR,
                        GrailsASTUtils.NULL_EXPRESSION)
                ),
                /* fieldName = this.getNextSequenceNumber(); */
                new ExpressionStatement(new BinaryExpression(
                        new VariableExpression(fieldName),
                        ASSIGN_OPERATOR,
                        new MethodCallExpression(new VariableExpression("this"), "getNextSequenceNumber", ArgumentListExpression.EMPTY_ARGUMENTS)
                )),
                new EmptyStatement()
        );
    }
}
