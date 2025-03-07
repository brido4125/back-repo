package link.sendwish.backend.service;

import link.sendwish.backend.common.exception.MemberNotFoundException;
import link.sendwish.backend.common.exception.ChatRoomNotFoundException;
import link.sendwish.backend.dtos.chat.ChatVoteEnterRequestDto;
import link.sendwish.backend.dtos.chat.ChatVoteEnterResponseDto;
import link.sendwish.backend.entity.*;
import link.sendwish.backend.repository.ChatRoomRepository;
import link.sendwish.backend.repository.ChatVoteMemberRepository;
import link.sendwish.backend.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class VoteService {
    private final ChatRoomRepository chatRoomRepository;
    private final MemberRepository memberRepository;
    private final ChatVoteMemberRepository chatVoteMemberRepository;

    @Transactional
    public ChatVoteEnterResponseDto enterVote(ChatVoteEnterRequestDto dto){

        Member member = memberRepository.findByNickname(dto.getNickname()).orElseThrow(MemberNotFoundException::new);
        ChatRoom chatRoom = chatRoomRepository.findById(dto.getRoomId()).orElseThrow(ChatRoomNotFoundException::new);

        // [todo] 해당 맴버가 이미 투표에 참여했는지 확인
        // 방금 참여한 맴버 저장
        ChatVoteMember chatVoteMember = ChatVoteMember.builder()
                .chatRoom(chatRoom)
                .member(member)
                .build();
        chatRoom.addChatVoteMember(chatVoteMember);
        member.addChatVoteMember(chatVoteMember);

        List<ChatVoteMember> voteMembers = chatVoteMemberRepository.findMemberByChatRoom(chatRoom).get();
        List<String> voteMemberIdList = voteMembers.stream().map(target -> target.getMember().getNickname()).toList();

        assert chatRoom.getId().equals(chatVoteMember.getChatRoom().getId());
        log.info("투표 입장 [roomId] : {}, [입장 인원] : {}", chatRoom.getId(), voteMemberIdList.size());
        return ChatVoteEnterResponseDto.builder()
                .roomId(chatRoom.getId())
                .memberIdList(voteMemberIdList)
                .build();
    }

}
