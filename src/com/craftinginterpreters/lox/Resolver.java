package com.craftinginterpreters.lox;

import com.sun.org.apache.xpath.internal.operations.Bool;

import java.util.*;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private static class Variable {
        final Token name;
        VariableState state;

        private Variable(Token name, VariableState state) {
            this.name = name;
            this.state = state;
        }
    }

    private enum VariableState {
        DECLARED,
        DEFINED,
        READ
    }

    private final Interpreter interpreter;
    // private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    private final Stack<Map<String, Variable>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;


    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private enum FunctionType {
        NONE,
        FUNCTION,
        INITIALIZER,
        METHOD
    }

    private enum ClassType {
        NONE,
        CLASS,
        SUBCLASS,
        TRAIT
    }

    private ClassType currentClass = ClassType.NONE;

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitTraitStmt(Stmt.Trait stmt) {
        declare(stmt.name);
        define(stmt.name);
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.TRAIT;

        for (Expr trait : stmt.traits) {
            resolve(trait);
        }

        beginScope();
        scopes.peek().put("this", new Variable(new Token(TokenType.THIS,
                "this " + stmt.name.lexeme,
                stmt.name.literal,
                stmt.name.line), VariableState.DECLARED));

        for (Stmt.Function method : stmt.methods) {
            FunctionType declaration = FunctionType.METHOD;
            resolveFunction(method, declaration);
        }

        endScope();

        currentClass = enclosingClass;
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;

        declare(stmt.name);
        define(stmt.name);

        if (stmt.superclass != null &&
                stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
            Lox.error(stmt.superclass.name,
                    "A class can't inherit from itself.");
        }

        if (stmt.superclass != null) {
            currentClass = ClassType.SUBCLASS;
            resolve(stmt.superclass);
        }
        /*
         * "this" and "super" are special variables that jump from declared to read
         * Since these variables get defined when they are used, we don't mark the state
         * as "DEFINED". Marking it as "READ" might be misleading so "DECLARED" is best here?
         * */
        if (stmt.superclass != null) {
            beginScope();

            scopes.peek().put("super", new Variable(
                    new Token(TokenType.SUPER,
                            "super " + stmt.superclass.name.lexeme,
                            stmt.superclass.name.literal,
                            stmt.superclass.name.line),
                    VariableState.DECLARED)); // only create env if there is a superclass
        }

        beginScope();
        scopes.peek().put("this", new Variable(new Token(TokenType.THIS,
                "this " + stmt.name.lexeme,
                stmt.name.literal,
                stmt.name.line), VariableState.DECLARED));

        for (Stmt.Function method : stmt.methods) {
            FunctionType declaration = FunctionType.METHOD;
            if (method.name.lexeme.equals("init")) {
                declaration = FunctionType.INITIALIZER;
            }
            resolveFunction(method, declaration);
        }

        for(Stmt.Function method: stmt.classMethods) {
            beginScope();
            scopes.peek().put("this", new Variable(new Token(TokenType.THIS,
                    "this " + stmt.name.lexeme,
                    stmt.name.literal,
                    stmt.name.line), VariableState.DECLARED));
            resolveFunction(method, FunctionType.METHOD);
            endScope();
        }

        if (stmt.superclass != null) endScope(); // only create env if there is a superclass

        endScope();
        currentClass = enclosingClass;  // "pop" the class from the resolver
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }

        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword,
                        "Can't return a value from an initializer.");
            }
            resolve(stmt.value);
        }

        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }


    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name, false);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr argument : expr.arguments) {
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }


    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword,
                    "Can't use 'super' outside of a class.");
        } else if (currentClass == ClassType.TRAIT) {
            Lox.error(expr.keyword,
                    "Can't use 'super' in a trait.");
        } else if (currentClass != ClassType.SUBCLASS) {
            Lox.error(expr.keyword,
                    "Can't use 'super' in a class with no superclass.");
        }
        resolveLocal(expr, expr.keyword, true);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword,
                    "Can't use 'this' outside of a class.");
            return null;
        }

        resolveLocal(expr, expr.keyword, true);
        return null;
    }


    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty() &&
                scopes.peek().containsKey(expr.name.lexeme) &&
                scopes.peek().get(expr.name.lexeme).state == VariableState.DECLARED) {
            Lox.error(expr.name,
                    "Can't read local variable in its own initializer.");
        }

        resolveLocal(expr, expr.name, true);
        return null;
    }

    @Override
    public Void visitConditionalExpr(Expr.Conditional expr) {
        resolve(expr.condition);
        resolve(expr.thenBranch);
        resolve(expr.elseBranch);
        return null;
    }


    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolveFunction(Stmt.Function function, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;
        beginScope();
        if (function.params != null){
            for (Token param : function.params) {
                declare(param);
                define(param);
            }
        }
        resolve(function.body);
        endScope();
        currentFunction = enclosingFunction;
    }

    private void beginScope() {
        scopes.push(new HashMap<String, Variable>());
    }

    private void endScope() {
        Map<String, Variable> scope = scopes.pop();

        for (Map.Entry<String, Variable> entry : scope.entrySet()) {
            if (entry.getValue().state == VariableState.DEFINED) {
                Lox.error(entry.getValue().name, "Local variable is not used.");
            }
        }
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) return;

        Map<String, Variable> scope = scopes.peek();
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name,
                    "Already variable with this name in this scope.");
        }
        scope.put(name.lexeme, new Variable(name, VariableState.DECLARED));
    }

    private void define(Token name) {
        if (scopes.isEmpty()) return;
        scopes.peek().get(name.lexeme).state = VariableState.DEFINED;
    }

    private void resolveLocal(Expr expr, Token name, boolean isRead) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);

                // Mark variable as read
                if (isRead) {
                    scopes.get(i).get(name.lexeme).state = VariableState.READ;
                }

                return;
            }
        }

        // Not found. Assume it is global.
    }
}