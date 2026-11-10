package ru.mirea.youdo.security;

/** Integration port for the account module's PasswordPolicy. */
@FunctionalInterface
public interface NewPasswordPolicy { void validate(String password); }
