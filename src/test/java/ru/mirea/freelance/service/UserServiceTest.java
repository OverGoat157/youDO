package ru.mirea.freelance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.mirea.freelance.exception.BusinessException;
import ru.mirea.freelance.exception.EntityNotFoundException;
import ru.mirea.freelance.exception.ValidationException;
import ru.mirea.freelance.model.*;
import ru.mirea.freelance.repository.InMemoryOrderRepository;
import ru.mirea.freelance.repository.InMemoryUserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {
    private InMemoryUserRepository users;
    private InMemoryOrderRepository orders;
    private UserService service;
    private User customer;
    private User freelancer;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        orders = new InMemoryOrderRepository();
        service = new UserService(users, orders);
        customer = service.create("Заказчик", "customer@example.com", UserRole.CUSTOMER);
        freelancer = service.create("Исполнитель", "freelancer@example.com", UserRole.FREELANCER);
    }

    @Test
    void createUser_validInput_savesWithDefaults() {
        User user = service.create("  Карим  ", "  karim@example.com  ", UserRole.FREELANCER);
        assertNotNull(user.getId());
        assertEquals("Карим", service.getById(user.getId()).getName());
        assertEquals("karim@example.com", user.getEmail());
        assertEquals(0, user.getRating());
        assertEquals(LocalDate.now(), user.getRegisteredAt());
        assertEquals(3, service.findAll().size());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void createUser_emptyName_throwsValidation(String name) {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create(name, "new@example.com", UserRole.CUSTOMER));
        assertTrue(e.getMessage().contains("Имя не может быть пустым"));
        assertEquals(2, users.findAll().size());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid"})
    void createUser_invalidEmail_throwsValidation(String email) {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create("Имя", email, UserRole.CUSTOMER));
        assertTrue(e.getMessage().contains("Email"));
    }

    @Test
    void createUser_nullRole_throwsValidation() {
        assertThrows(ValidationException.class, () -> service.create("Имя", "new@example.com", null));
    }

    @Test
    void createUser_duplicateEmail_throwsValidation() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create("Другой", " customer@example.com ", UserRole.FREELANCER));
        assertEquals("Пользователь с email customer@example.com уже существует", e.getMessage());
        assertEquals(2, users.findAll().size());
    }

    @Test
    void updateUser_ownEmail_isAllowed() {
        User result = service.update(customer.getId(), "Новое имя", customer.getEmail(), UserRole.CUSTOMER);
        assertEquals(customer.getId(), result.getId());
        assertEquals("Новое имя", service.getById(customer.getId()).getName());
    }

    @Test
    void updateUser_duplicateEmail_leavesUserUnchanged() {
        assertThrows(ValidationException.class,
                () -> service.update(customer.getId(), "Другое имя", freelancer.getEmail(), UserRole.FREELANCER));
        assertEquals("Заказчик", service.getById(customer.getId()).getName());
        assertEquals("customer@example.com", customer.getEmail());
        assertEquals(UserRole.CUSTOMER, customer.getRole());
    }

    @Test
    void updateUser_invalidInput_leavesUserUnchanged() {
        assertThrows(ValidationException.class,
                () -> service.update(customer.getId(), " ", customer.getEmail(), UserRole.CUSTOMER));
        assertThrows(ValidationException.class,
                () -> service.update(customer.getId(), "Имя", "invalid", UserRole.CUSTOMER));
        assertThrows(ValidationException.class,
                () -> service.update(customer.getId(), "Имя", customer.getEmail(), null));
        assertEquals("Заказчик", customer.getName());
    }

    @Test
    void getById_unknown_throwsEntityNotFound() {
        EntityNotFoundException e = assertThrows(EntityNotFoundException.class, () -> service.getById(999L));
        assertEquals("Пользователь с ID 999 не найден", e.getMessage());
        assertEquals("Пользователь", e.getEntity());
        assertEquals(999L, e.getId());
    }

    @Test
    void updateAndDelete_unknownUser_throwEntityNotFound() {
        assertThrows(EntityNotFoundException.class,
                () -> service.update(999L, "Имя", "new@example.com", UserRole.CUSTOMER));
        assertThrows(EntityNotFoundException.class, () -> service.delete(999L));
        assertThrows(EntityNotFoundException.class, () -> service.getById(null));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"OPEN", "IN_PROGRESS", "ON_REVIEW"})
    void deleteUser_withActiveOrder_throwsBusiness(OrderStatus status) {
        saveOrder(status);
        for (User user : new User[]{customer, freelancer}) {
            BusinessException e = assertThrows(BusinessException.class, () -> service.delete(user.getId()));
            assertTrue(e.getMessage().contains("ID " + user.getId()));
            assertTrue(e.getMessage().contains("активные заказы"));
            assertTrue(users.existsById(user.getId()));
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"COMPLETED", "CANCELLED"})
    void deleteUser_withFinalOrder_isAllowedByBusinessRules(OrderStatus status) {
        saveOrder(status);
        service.delete(customer.getId());
        service.delete(freelancer.getId());
        assertTrue(service.findAll().isEmpty());
    }

    @Test
    void deleteUser_withoutOrders_removesUser() {
        service.delete(customer.getId());
        assertFalse(users.existsById(customer.getId()));
        assertTrue(users.existsById(freelancer.getId()));
    }

    private void saveOrder(OrderStatus status) {
        Order order = new Order("Заказ", "", OrderCategory.OTHER, BigDecimal.TEN,
                LocalDate.now().plusDays(1), customer.getId());
        order.setFreelancerId(freelancer.getId());
        order.setStatus(status);
        orders.save(order);
    }
}
