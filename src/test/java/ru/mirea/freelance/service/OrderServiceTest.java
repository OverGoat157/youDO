package ru.mirea.freelance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.mirea.freelance.exception.BusinessException;
import ru.mirea.freelance.exception.EntityNotFoundException;
import ru.mirea.freelance.exception.ValidationException;
import ru.mirea.freelance.model.*;
import ru.mirea.freelance.repository.InMemoryOrderRepository;
import ru.mirea.freelance.repository.InMemoryUserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    private InMemoryUserRepository users;
    private InMemoryOrderRepository orders;
    private OrderService service;
    private User customer;
    private User freelancer;
    private LocalDate deadline;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        orders = new InMemoryOrderRepository();
        UserService userService = new UserService(users, orders);
        customer = userService.create("Заказчик", "customer@example.com", UserRole.CUSTOMER);
        freelancer = userService.create("Исполнитель", "freelancer@example.com", UserRole.FREELANCER);
        service = new OrderService(orders, users);
        deadline = LocalDate.now().plusDays(7);
    }

    @Test
    void createOrder_validInput_savesOpenOrder() {
        Order order = service.create("  Сайт  ", "Описание", OrderCategory.DEVELOPMENT,
                new BigDecimal("1000.50"), deadline, customer.getId());
        assertNotNull(order.getId());
        assertEquals("Сайт", service.getById(order.getId()).getTitle());
        assertEquals(OrderStatus.OPEN, order.getStatus());
        assertNull(order.getFreelancerId());
        assertNotNull(order.getCreatedAt());
        assertEquals(customer.getId(), order.getCustomerId());
        assertEquals(List.of(order), service.findAll());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void createOrder_emptyTitle_throwsValidation(String title) {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create(title, "", OrderCategory.OTHER, BigDecimal.TEN, deadline, customer.getId()));
        assertTrue(e.getMessage().contains("Название заказа не может быть пустым"));
        assertTrue(service.findAll().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1.00"})
    void createOrder_nonPositiveBudget_throwsValidation(String budget) {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create("Заказ", "", OrderCategory.OTHER, new BigDecimal(budget), deadline, customer.getId()));
        assertTrue(e.getMessage().contains("Бюджет должен быть больше нуля"));
        assertTrue(e.getMessage().contains(budget));
    }

    @Test
    void createOrder_nullBudget_throwsValidation() {
        assertThrows(ValidationException.class,
                () -> service.create("Заказ", "", OrderCategory.OTHER, null, deadline, customer.getId()));
    }

    @Test
    void createOrder_pastDeadline_throwsValidation() {
        LocalDate past = LocalDate.now().minusDays(1);
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create("Заказ", "", OrderCategory.OTHER, BigDecimal.TEN, past, customer.getId()));
        assertTrue(e.getMessage().contains("Дедлайн не может быть в прошлом"));
        assertTrue(e.getMessage().contains(past.toString()));
    }

    @Test
    void createOrder_todayDeadline_isAllowed() {
        Order order = service.create("Заказ", "", OrderCategory.OTHER, BigDecimal.ONE, LocalDate.now(), customer.getId());
        assertEquals(LocalDate.now(), order.getDeadline());
    }

    @Test
    void createOrder_nullDeadlineOrCategory_throwsValidation() {
        assertThrows(ValidationException.class,
                () -> service.create("Заказ", "", OrderCategory.OTHER, BigDecimal.TEN, null, customer.getId()));
        assertThrows(ValidationException.class,
                () -> service.create("Заказ", "", null, BigDecimal.TEN, deadline, customer.getId()));
    }

    @Test
    void createOrder_customerIsFreelancer_throwsBusiness() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.create("Заказ", "", OrderCategory.OTHER, BigDecimal.TEN, deadline, freelancer.getId()));
        assertEquals("Пользователь с ID " + freelancer.getId() + " не является заказчиком", e.getMessage());
        assertTrue(service.findAll().isEmpty());
    }

    @Test
    void createOrder_unknownCustomer_throwsEntityNotFound() {
        EntityNotFoundException e = assertThrows(EntityNotFoundException.class,
                () -> service.create("Заказ", "", OrderCategory.OTHER, BigDecimal.TEN, deadline, 999L));
        assertEquals("Пользователь с ID 999 не найден", e.getMessage());
        assertThrows(EntityNotFoundException.class,
                () -> service.create("Заказ", "", OrderCategory.OTHER, BigDecimal.TEN, deadline, null));
    }

    @Test
    void getById_unknown_throwsEntityNotFound() {
        EntityNotFoundException e = assertThrows(EntityNotFoundException.class, () -> service.getById(999L));
        assertEquals("Заказ с ID 999 не найден", e.getMessage());
        assertEquals("Заказ", e.getEntity());
        assertEquals(999L, e.getId());
        assertThrows(EntityNotFoundException.class, () -> service.getById(null));
    }

    @Test
    void updateOrder_validInput_preservesWorkflowFields() {
        Order order = createOrder();
        order.setFreelancerId(freelancer.getId());
        order.setStatus(OrderStatus.IN_PROGRESS);
        LocalDateTime createdAt = order.getCreatedAt();
        service.update(order.getId(), "  Макет  ", "Новое описание", OrderCategory.DESIGN,
                new BigDecimal("25.50"), deadline.plusDays(1));
        Order saved = service.getById(order.getId());
        assertEquals("Макет", saved.getTitle());
        assertEquals("Новое описание", saved.getDescription());
        assertEquals(OrderCategory.DESIGN, saved.getCategory());
        assertEquals(new BigDecimal("25.50"), saved.getBudget());
        assertEquals(deadline.plusDays(1), saved.getDeadline());
        assertEquals(OrderStatus.IN_PROGRESS, saved.getStatus());
        assertEquals(freelancer.getId(), saved.getFreelancerId());
        assertEquals(customer.getId(), saved.getCustomerId());
        assertEquals(createdAt, saved.getCreatedAt());
    }

    @Test
    void updateOrder_invalidInput_leavesOrderUnchanged() {
        Order order = createOrder();
        assertThrows(ValidationException.class,
                () -> service.update(order.getId(), " ", "", OrderCategory.OTHER, BigDecimal.TEN, deadline));
        assertThrows(ValidationException.class,
                () -> service.update(order.getId(), "Новое", "", OrderCategory.OTHER, BigDecimal.ZERO, deadline));
        assertThrows(ValidationException.class,
                () -> service.update(order.getId(), "Новое", "", OrderCategory.OTHER, BigDecimal.TEN, LocalDate.now().minusDays(1)));
        assertThrows(ValidationException.class,
                () -> service.update(order.getId(), "Новое", "", null, BigDecimal.TEN, deadline));
        assertEquals("Заказ", service.getById(order.getId()).getTitle());
        assertEquals(BigDecimal.TEN, order.getBudget());
        assertEquals(deadline, order.getDeadline());
    }

    @Test
    void deleteOrder_existing_removesOrder() {
        Order order = createOrder();
        service.delete(order.getId());
        assertTrue(service.findAll().isEmpty());
    }

    @Test
    void updateAndDelete_unknownOrder_throwEntityNotFound() {
        assertThrows(EntityNotFoundException.class,
                () -> service.update(999L, "Заказ", "", OrderCategory.OTHER, BigDecimal.TEN, deadline));
        assertThrows(EntityNotFoundException.class, () -> service.delete(999L));
    }

    @Test
    void assignFreelancer_validUser_savesAssignment() {
        Order order = createOrder();
        Order assigned = service.assignFreelancer(order.getId(), freelancer.getId());
        assertEquals(freelancer.getId(), assigned.getFreelancerId());
        assertEquals(freelancer.getId(), service.getById(order.getId()).getFreelancerId());
        assertEquals(OrderStatus.OPEN, assigned.getStatus());
    }

    @Test
    void assignFreelancer_userIsCustomer_throwsBusiness() {
        Order order = createOrder();
        User other = users.save(new User("Другой заказчик", "other@example.com", UserRole.CUSTOMER));
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.assignFreelancer(order.getId(), other.getId()));
        assertEquals("Пользователь с ID " + other.getId() + " не является исполнителем", e.getMessage());
        assertNull(order.getFreelancerId());
    }

    @Test
    void assignFreelancer_sameAsCustomer_throwsBusiness() {
        Order order = createOrder();
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.assignFreelancer(order.getId(), customer.getId()));
        assertTrue(e.getMessage().contains("Заказчик не может быть исполнителем своего заказа"));
        assertNull(order.getFreelancerId());
    }

    @Test
    void assignFreelancer_unknownUser_throwsEntityNotFound() {
        Order order = createOrder();
        EntityNotFoundException e = assertThrows(EntityNotFoundException.class,
                () -> service.assignFreelancer(order.getId(), 999L));
        assertEquals("Пользователь с ID 999 не найден", e.getMessage());
        assertThrows(EntityNotFoundException.class, () -> service.assignFreelancer(order.getId(), null));
        assertNull(order.getFreelancerId());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"COMPLETED", "CANCELLED"})
    void assignFreelancer_finalOrder_throwsBusiness(OrderStatus status) {
        Order order = createOrder();
        order.setStatus(status);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.assignFreelancer(order.getId(), freelancer.getId()));
        assertTrue(e.getMessage().contains("заказ с ID " + order.getId()));
        assertTrue(e.getMessage().contains(status.name()));
        assertNull(order.getFreelancerId());
    }

    @Test
    void changeStatus_openToCompleted_throwsBusiness() {
        Order order = createOrder();
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.changeStatus(order.getId(), OrderStatus.COMPLETED));
        assertEquals("Переход OPEN → COMPLETED запрещён", e.getMessage());
        assertEquals(OrderStatus.OPEN, service.getById(order.getId()).getStatus());
    }

    @Test
    void changeStatus_openToInProgressWithoutFreelancer_throwsBusiness() {
        Order order = createOrder();
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.changeStatus(order.getId(), OrderStatus.IN_PROGRESS));
        assertTrue(e.getMessage().contains("Нельзя взять в работу заказ без исполнителя"));
        assertEquals(OrderStatus.OPEN, order.getStatus());
    }

    @Test
    void changeStatus_fullWorkflowWithRevision_completesOrder() {
        Order order = createOrder();
        service.assignFreelancer(order.getId(), freelancer.getId());
        for (OrderStatus next : List.of(OrderStatus.IN_PROGRESS, OrderStatus.ON_REVIEW,
                OrderStatus.IN_PROGRESS, OrderStatus.ON_REVIEW, OrderStatus.COMPLETED)) {
            assertEquals(next, service.changeStatus(order.getId(), next).getStatus());
            assertEquals(next, service.getById(order.getId()).getStatus());
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"OPEN", "IN_PROGRESS"})
    void changeStatus_cancelActiveOrder_savesFinalStatus(OrderStatus initial) {
        Order order = createOrder();
        service.assignFreelancer(order.getId(), freelancer.getId());
        if (initial == OrderStatus.IN_PROGRESS) {
            service.changeStatus(order.getId(), initial);
        }
        service.changeStatus(order.getId(), OrderStatus.CANCELLED);
        assertEquals(OrderStatus.CANCELLED, service.getById(order.getId()).getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"COMPLETED", "CANCELLED"})
    void changeStatus_finalOrder_rejectsEveryTransition(OrderStatus initial) {
        Order order = createOrder();
        order.setStatus(initial);
        for (OrderStatus next : OrderStatus.values()) {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.changeStatus(order.getId(), next));
            assertEquals("Переход " + initial + " → " + next + " запрещён", e.getMessage());
        }
        assertEquals(initial, order.getStatus());
    }

    @Test
    void changeStatus_nullOrSameStatus_throwsBusiness() {
        Order order = createOrder();
        assertThrows(BusinessException.class, () -> service.changeStatus(order.getId(), null));
        assertThrows(BusinessException.class, () -> service.changeStatus(order.getId(), OrderStatus.OPEN));
        assertEquals(OrderStatus.OPEN, order.getStatus());
    }

    @Test
    void assignAndChangeStatus_unknownOrder_throwEntityNotFound() {
        assertThrows(EntityNotFoundException.class, () -> service.assignFreelancer(999L, freelancer.getId()));
        assertThrows(EntityNotFoundException.class, () -> service.changeStatus(999L, OrderStatus.CANCELLED));
    }

    private Order createOrder() {
        return service.create("Заказ", "Описание", OrderCategory.OTHER, BigDecimal.TEN, deadline, customer.getId());
    }
}
