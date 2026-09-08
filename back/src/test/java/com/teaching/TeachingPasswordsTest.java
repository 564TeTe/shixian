package com.teaching;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeachingPasswordsTest {
    @Test void hashesWithSaltAndRejectsWrongOrLegacyPasswords() {
        String password = "Testing!2026";
        String one = TeachingPasswords.hash(password);
        assertNotEquals(one, TeachingPasswords.hash(password));
        assertTrue(TeachingPasswords.matches(password, one));
        assertFalse(TeachingPasswords.matches("wrong", one));
        assertFalse(TeachingPasswords.matches("123456", "123456"));
        assertFalse(TeachingPasswords.matches(null, one));
    }
    @Test void resetPasswordsCanBeEncoded() {
        String value = TeachingPasswords.newPassword();
        assertTrue(value.length() >= 12);
        assertTrue(TeachingPasswords.matches(value, TeachingPasswords.hash(value)));
        assertThrows(IllegalArgumentException.class, () -> TeachingPasswords.hash("short"));
    }
}
