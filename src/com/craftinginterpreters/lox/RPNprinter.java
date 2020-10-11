package com.craftinginterpreters.lox;

public class RPNprinter implements Expr.Visitor<String> {
    String print(Expr expr) {
        return expr.accept(this);
    }

    /**
     * Statements can't really be defined in RPN
     */

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return expr.left.accept(this) + " " + expr.right.accept(this) + " " + expr.operator.lexeme + " ";
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return expr.expression.accept(this) + " ";
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        return expr.value.toString() + " ";
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        String operator = expr.operator.lexeme;
        if (expr.operator.type == TokenType.MINUS) {
            operator = "~"; // need to distinguish unary and binary -
        }
        return expr.right.accept(this) + " " + operator + " ";

    }

    @Override
    public String visitConditionalExpr(Expr.Conditional expr) {
        // a ? b : c => a b c :?
        return expr.condition.accept(this) +
                expr.thenBranch.accept(this) +
                expr.elseBranch.accept(this) + " :?" + " ";
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        // like binary
        return expr.left.accept(this) + " " + expr.right.accept(this) + " " + expr.operator.lexeme + " ";
    }

    @Override
    public  String visitVariableExpr(Expr.Variable expr) {
        return expr.name + " "; // what can be RPN for variable name ?
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        return "";
    }

    @Override
    public String visitCallExpr(Expr.Call expr){
        return ""; // TODO: implement later
    }

    @Override
    public String visitGetExpr(Expr.Get expr) {
        return  expr.name.lexeme + " " + expr.object.accept(this); // TODO: implement w. object later?
    }

    @Override
    public String visitSetExpr(Expr.Set expr) {
        return  expr.name.lexeme + " " + expr.object.accept(this); // TODO: implement w. object later?
    }

    public static void main(String[] args) {
        Expr expression = new Expr.Binary(
                new Expr.Unary(
                        new Token(TokenType.MINUS, "-", null, 1),
                        new Expr.Literal(123)),
                new Token(TokenType.STAR, "*", null, 1),
                new Expr.Grouping(
                        new Expr.Literal("str")));

        // 1 == 1 ? 2 : 3 * 4
        Expr ternary_expression = new Expr.Conditional(
                new Expr.Binary(new Expr.Literal(1),
                new Token(TokenType.EQUAL_EQUAL,  "==", null, 1),
                new Expr.Literal(1)),
                new Expr.Literal(2),
                new Expr.Binary(new Expr.Literal(3),
                new Token(TokenType.STAR,  "*", null, 1),
                new Expr.Literal(4)));

        //        new Expr.Binary(new Expr.Literal(1),
        //                new Token(TokenType.PLUS,  "+", null, 1),
        //                new Expr.Literal(2))

        // System.out.println(new RPNprinter().print(expression));
        System.out.println(new RPNprinter().print(ternary_expression));
    }
}
