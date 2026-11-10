package ru.mirea.youdo;

import org.junit.jupiter.api.Test;
import ru.mirea.youdo.service.*;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();
    @Test void acceptsBothByteBoundaries() {
        assertDoesNotThrow(() -> policy.validate("Aa1!" + "x".repeat(8)));
        assertDoesNotThrow(() -> policy.validate("Aa1!" + "x".repeat(68)));
        assertThrows(AccountProblem.class, () -> policy.validate("Aa1!" + "x".repeat(7)));
        assertThrows(AccountProblem.class, () -> policy.validate("Aa1!" + "x".repeat(69)));
    }
    @Test void countsUtf8BytesRatherThanJavaCharacters() {
        assertDoesNotThrow(() -> policy.validate("Яя1!" + "я".repeat(33))); // 72 bytes
        assertThrows(AccountProblem.class, () -> policy.validate("Яя1!" + "я".repeat(34)));
        assertDoesNotThrow(() -> policy.validate("Aa1!" + "😀".repeat(17)));
        assertThrows(AccountProblem.class, () -> policy.validate("Aa1!" + "😀".repeat(18)));
    }
    @Test void rejectsMissingClassesNullAndInvalidUnicodeWithoutLeakingInput() {
        for (String password : new String[]{null, "", "abcdefghijk1!", "ABCDEFGHIJK1!", "Abcdefghijk!!", "Abcdefghijk12", "Abcdefghij1 ", "Abcdefgh1!\u0000xx", "Abcdefgh1!\ud800xx"}) {
            var ex = assertThrows(AccountProblem.class, () -> policy.validate(password));
            assertEquals("newPassword", ex.field());
            if (password != null && !password.isEmpty()) assertFalse(ex.getMessage().contains(password));
        }
    }
}
