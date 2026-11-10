package ru.mirea.youdo.service;

/** Safe public message only; never include input passwords or persistence exceptions. */
public class AccountProblem extends RuntimeException {
    private final int status;
    private final String code;
    private final String field;
    public AccountProblem(int status, String code, String message, String field) {
        super(message); this.status = status; this.code = code; this.field = field;
    }
    public int status() { return status; }
    public String code() { return code; }
    public String field() { return field; }
    public static AccountProblem invalid(String field, String message) {
        return new AccountProblem(400, "VALIDATION_ERROR", message, field);
    }
    public static AccountProblem missing(String entity) {
        return new AccountProblem(404, "NOT_FOUND", entity + " не найден", null);
    }
    public static AccountProblem duplicate(String field) {
        return new AccountProblem(409, "DUPLICATE_VALUE", "Значение уже используется", field);
    }
}
