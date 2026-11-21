package ru.mirea.youdo;

import java.time.*;
import org.junit.jupiter.api.Test;
import ru.mirea.youdo.domain.*;
import ru.mirea.youdo.service.OrderPriorityCalculator;
import static org.assertj.core.api.Assertions.*;

class OrderPriorityTest {
    @Test void boundariesFinalStatusUrgencyAndMoscowMidnight() {
        var p=new OrderPriorityCalculator(Clock.fixed(Instant.parse("2026-11-21T21:30:00Z"),ZoneOffset.UTC));
        var today=LocalDate.of(2026,11,22);
        assertThat(p.today()).isEqualTo(today);
        assertThat(p.calculate(OrderStatus.OPEN,false,today.minusDays(1),today)).isEqualTo(OrderPriority.HIGH);
        assertThat(p.calculate(OrderStatus.OPEN,true,today.plusDays(30),today)).isEqualTo(OrderPriority.HIGH);
        for(int days=0;days<=3;days++)
            assertThat(p.calculate(OrderStatus.ON_REVIEW,false,today.plusDays(days),today)).isEqualTo(OrderPriority.MEDIUM);
        assertThat(p.calculate(OrderStatus.OPEN,false,today.plusDays(4),today)).isEqualTo(OrderPriority.LOW);
        for(var status:new OrderStatus[]{OrderStatus.COMPLETED,OrderStatus.CANCELLED})
            assertThat(p.calculate(status,true,today.minusDays(1),today)).isEqualTo(OrderPriority.LOW);
    }
}
