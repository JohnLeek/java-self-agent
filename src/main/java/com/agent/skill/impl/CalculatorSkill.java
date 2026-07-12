package com.agent.skill.impl;

import com.agent.skill.Skill;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** 计算器 Skill — 纯 Java 表达式求值 */
@Component
public class CalculatorSkill implements Skill {
    private boolean enabled = true;

    @Override public String name() { return "calculator"; }
    @Override public String description() { return "计算器：数学表达式求值"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { enabled = e; }

    @Tool(description = "计算数学表达式。支持 + - * / () ** sqrt abs sin cos log exp")
    public String calculate(@ToolParam(description = "数学表达式") String expression) {
        try {
            double r = Expr.eval(expression);
            return expression + " = " + (r == (long) r ? (long) r : r);
        } catch (Exception e) {
            return "计算错误: " + e.getMessage();
        }
    }

    private static class Expr {
        private final String s; private int p;
        Expr(String s) { this.s = s.replaceAll("\\s+", ""); }
        static double eval(String e) { return new Expr(e).addSub(); }
        double addSub() { double l = mulDiv(); while (p < s.length()) { char c = s.charAt(p); if (c == '+') { p++; l += mulDiv(); } else if (c == '-') { p++; l -= mulDiv(); } else break; } return l; }
        double mulDiv() { double l = pow(); while (p < s.length()) { char c = s.charAt(p); if (c == '*') { p++; l *= pow(); } else if (c == '/') { p++; l /= pow(); } else break; } return l; }
        double pow() { double l = unary(); while (p + 1 < s.length() && s.charAt(p) == '*' && s.charAt(p + 1) == '*') { p += 2; l = Math.pow(l, unary()); } return l; }
        double unary() { if (p < s.length() && s.charAt(p) == '-') { p++; return -atom(); } if (p < s.length() && s.charAt(p) == '+') { p++; return atom(); } return atom(); }
        double atom() {
            if (p >= s.length()) throw new RuntimeException("eof");
            char c = s.charAt(p);
            if (c == '(') { p++; double v = addSub(); p++; return v; }
            if (Character.isDigit(c) || c == '.') { int st = p; while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) p++; return Double.parseDouble(s.substring(st, p)); }
            if (s.startsWith("PI", p)) { p += 2; return Math.PI; }
            if (s.startsWith("sqrt(", p)) { p += 5; double v = addSub(); p++; return Math.sqrt(v); }
            if (s.startsWith("sin(", p)) { p += 4; double v = addSub(); p++; return Math.sin(v); }
            if (s.startsWith("cos(", p)) { p += 4; double v = addSub(); p++; return Math.cos(v); }
            if (s.startsWith("abs(", p)) { p += 4; double v = addSub(); p++; return Math.abs(v); }
            if (s.startsWith("log(", p)) { p += 4; double v = addSub(); p++; return Math.log(v); }
            if (s.startsWith("exp(", p)) { p += 4; double v = addSub(); p++; return Math.exp(v); }
            throw new RuntimeException("unexpected: " + c);
        }
    }
}
