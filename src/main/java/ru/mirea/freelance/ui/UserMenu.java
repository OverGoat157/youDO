package ru.mirea.freelance.ui;

import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;
import ru.mirea.freelance.service.UserService;
import ru.mirea.freelance.util.InputReader;
import ru.mirea.freelance.util.TablePrinter;

import java.util.List;

public class UserMenu implements ConsoleMenu.Submenu {

    private final UserService userService;
    private final InputReader in;

    public UserMenu(UserService userService, InputReader in) {
        this.userService = userService;
        this.in = in;
    }

    @Override
    public void printOptions() {
        System.out.println("Пользователи");
        System.out.println("1. Список пользователей");
        System.out.println("2. Найти по ID");
        System.out.println("3. Создать пользователя");
        System.out.println("4. Изменить пользователя");
        System.out.println("5. Удалить пользователя");
    }

    @Override
    public void handle(int choice) {
        switch (choice) {
            case 1 -> TablePrinter.printUsers(userService.findAll());
            case 2 -> findById();
            case 3 -> create();
            case 4 -> update();
            case 5 -> delete();
            default -> System.out.println("Нет такого пункта");
        }
    }

    private void findById() {
        long id = in.readLong("ID пользователя: ");
        TablePrinter.printUsers(List.of(userService.getById(id)));
    }

    private void create() {
        String name = in.readNonEmpty("Имя: ");
        String email = in.readNonEmpty("Email: ");
        UserRole role = in.readEnum("Роль: ", UserRole.class);
        User user = userService.create(name, email, role);
        System.out.println("Пользователь создан, ID = " + user.getId());
    }

    private void update() {
        long id = in.readLong("ID пользователя: ");
        User current = userService.getById(id);
        System.out.println("Текущие данные:");
        TablePrinter.printUsers(List.of(current));
        String name = in.readNonEmpty("Новое имя: ");
        String email = in.readNonEmpty("Новый email: ");
        UserRole role = in.readEnum("Новая роль: ", UserRole.class);
        userService.update(id, name, email, role);
        System.out.println("Пользователь изменён");
    }

    private void delete() {
        long id = in.readLong("ID пользователя: ");
        userService.delete(id);
        System.out.println("Пользователь удалён");
    }
}
