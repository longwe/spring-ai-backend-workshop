package com.ezcloud;

import com.ezcloud.service.AgentTools.ExpressionParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpressionParserTest {

    @Test
    void evaluatesBasicArithmetic() {
        assertEquals(7.0, new ExpressionParser("3 + 4").parse());
        assertEquals(47.0, new ExpressionParser("(12.5 * 4) - 3").parse());
        assertEquals(2.5, new ExpressionParser("5 / 2").parse());
        assertEquals(-6.0, new ExpressionParser("-2 * 3").parse());
    }

    @Test
    void respectsOperatorPrecedence() {
        assertEquals(14.0, new ExpressionParser("2 + 3 * 4").parse());
        assertEquals(20.0, new ExpressionParser("(2 + 3) * 4").parse());
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> new ExpressionParser("2 +").parse());
        assertThrows(IllegalArgumentException.class, () -> new ExpressionParser("(2 + 3").parse());
        assertThrows(IllegalArgumentException.class, () -> new ExpressionParser("abc").parse());
    }
}
