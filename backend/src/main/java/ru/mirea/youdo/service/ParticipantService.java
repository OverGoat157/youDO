package ru.mirea.youdo.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import ru.mirea.youdo.domain.Participant;
import ru.mirea.youdo.dto.OrderDtos.ParticipantResponse;
import ru.mirea.youdo.dto.AccountDtos.PageResponse;
import ru.mirea.youdo.filter.QueryParameters;
import ru.mirea.youdo.repository.ParticipantRepository;
@Service @Transactional(readOnly=true)
public class ParticipantService {
    private final ParticipantRepository participants;
    public ParticipantService(ParticipantRepository participants) { this.participants=participants; }
    @PreAuthorize("hasAnyRole('VIEWER','EDITOR','ADMIN')")
    public PageResponse<ParticipantResponse> list(String q,Participant.Role role,int page,int size) {
        Specification<Participant> filter=(r,query,cb)->cb.conjunction();
        if(q!=null && !q.isBlank()) filter=filter.and((r,query,cb)->cb.or(
            cb.like(cb.lower(r.get("name")),QueryParameters.like(q),'\\'),cb.like(cb.lower(r.get("email")),QueryParameters.like(q),'\\')));
        if(role!=null) filter=filter.and((r,query,cb)->cb.equal(r.get("role"),role));
        var found=participants.findAll(filter,QueryParameters.page(page,size,Sort.by("name","id")));
        return new PageResponse<>(found.getContent().stream().map(ParticipantResponse::of).toList(),page,size,found.getTotalElements(),found.getTotalPages());
    }
}
