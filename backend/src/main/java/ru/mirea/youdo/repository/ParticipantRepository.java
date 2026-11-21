package ru.mirea.youdo.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import ru.mirea.youdo.domain.Participant;
public interface ParticipantRepository extends JpaRepository<Participant,Long>, JpaSpecificationExecutor<Participant> {
    Optional<Participant> findByEmailIgnoreCase(String email);
}
