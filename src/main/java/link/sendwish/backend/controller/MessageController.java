package link.sendwish.backend.controller;

import link.sendwish.backend.dtos.chat.*;
import link.sendwish.backend.service.ChatService;
import link.sendwish.backend.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Controller
@RequiredArgsConstructor
public class MessageController {
    private final ChatService chatService;
    private final SimpMessagingTemplate template;
    private final ConcurrentHashMap<Long, List<String>> webRtcSessions = new ConcurrentHashMap<>();
    private final VoteService voteService;

    @MessageMapping("/chat")
    public void sendMessage(ChatMessageRequestDto dto){
        ChatMessageResponseDto responseDto = chatService.saveChatMessage(dto);
        this.template.convertAndSend("/sub/chat/" + responseDto.getChatRoomId(), responseDto);
    }

    @MessageMapping("/live")
    public void sendLiveMessage(ChatLiveMessageRequestDto dto){
        log.info("{}님이 Data Signal을 전송합니다.", dto.getNickname());
        ChatLiveMessageResponseDto responseDto =
                ChatLiveMessageResponseDto.builder()
                        .nickname(dto.getNickname())
                        .signal(dto.getSignal())
                        .build();
        this.template.convertAndSend("/sub/live/" + dto.getRoomId(), responseDto);
    }

    @MessageMapping("/live/enter")
    public void sendLiveMessage(LiveEnterRequestDto dto){
        log.info("{} 님이 통화에 참여합니다.", dto.getNickname());
        if (webRtcSessions.containsKey(dto.getRoomId())) {
            webRtcSessions.get(dto.getRoomId()).add(dto.getNickname());
        } else {
            webRtcSessions.put(dto.getRoomId(), List.of(dto.getNickname()));
        }
        this.template.convertAndSend("/sub/live/enter/" + dto.getRoomId(), webRtcSessions.get(dto.getRoomId()));      
    }

    @MessageMapping("/vote/enter")
    public void sendVoteEnter(ChatVoteEnterRequestDto dto){
        log.info("{} 님이 투표에 참여합니다.", dto.getNickname());
        ChatVoteEnterResponseDto responseDto = voteService.enterVote(dto);
        this.template.convertAndSend("/sub/vote/enter/" + dto.getRoomId(), responseDto);
    }
    
    @MessageMapping("/vote")
    public void sendVote(ChatVoteRequestDto dto){
        Long like = 1L;
        log.info("{} 님이 투표를 했습니다.", dto.getNickname());
        ChatVoteResponseDto responseDto = ChatVoteResponseDto.builder()
                .itemId(dto.getItemId())
                .like(like)
                .build();
        this.template.convertAndSend("/sub/vote/" + dto.getRoomId(), responseDto);
    }
}
