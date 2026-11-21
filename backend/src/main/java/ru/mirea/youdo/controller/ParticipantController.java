package ru.mirea.youdo.controller;
import org.springframework.web.bind.annotation.*;
import ru.mirea.youdo.domain.Participant;
import ru.mirea.youdo.dto.OrderDtos.ParticipantResponse;
import ru.mirea.youdo.dto.AccountDtos.PageResponse;
import ru.mirea.youdo.service.ParticipantService;
@RestController @RequestMapping("/api/participants")
public class ParticipantController {
    private final ParticipantService participants;
    public ParticipantController(ParticipantService participants) { this.participants=participants; }
    @GetMapping public PageResponse<ParticipantResponse> list(@RequestParam(required=false) String q,
        @RequestParam(required=false) Participant.Role role,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return participants.list(q,role,page,size);
    }
}
