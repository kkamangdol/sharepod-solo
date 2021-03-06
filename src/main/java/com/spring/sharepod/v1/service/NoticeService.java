package com.spring.sharepod.v1.service;


import com.spring.sharepod.entity.Notice;
import com.spring.sharepod.exception.CommonError.ErrorCode;
import com.spring.sharepod.exception.CommonError.ErrorCodeException;
import com.spring.sharepod.v1.dto.response.notice.NoticeInfoResponseDto;
import com.spring.sharepod.v1.repository.Notice.NoticeRepository;
import com.spring.sharepod.v1.validator.NoticeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.spring.sharepod.exception.CommonError.ErrorCode.NOTICELIST_NOT_EXIST;

@Service
@RequiredArgsConstructor
public class NoticeService {
    private final NoticeRepository noticeRepository;
    private final NoticeValidator noticeValidator;

    //24번 알림 갯수(구현 완료)
    @Transactional
    public int getNoticeCount(Long userid) {
        // user의 정보가 없을 경우에는 count를 0으로 보내준다.
        return noticeRepository.findByCOUNTBuyerOrSellerId(userid);
    }

    //25번 알림 목록 띄우기 (구현 완료)
    @Transactional
    public NoticeInfoResponseDto getNoticeList(Long userId) {
//        List<NoticeInfoList> noticeInfoList = noticeRepository.noticeInfoList(userId);
//        return noticeInfoList;
        //List<NoticeResponseDto.Notice> noticeResponseDtoLists = new ArrayList<>();

//        for (NoticeInfoList notice : noticeInfoList) {
//            //거래 요청의 경우 buyer의 nickname이 필요하다.
//            if (Objects.equals(notice.getNoticeInfo(), "거래 요청을 하였습니다.")) {
//                notice.setNickName();
//            }
//            //거래 거절의 경우 seller의 nickname이 필요하다.
//            if (Objects.equals(notice.getNoticeInfo(), "거래 거절을 하였습니다.")) {
//                NoticeResponseDto.Notice noticeResponseDto = NoticeResponseDto.Notice.builder()
//                        .noticeId(notice.getId())
//                        .noticeName(notice.getSeller().getNickName())
//                        .noticeMsg(notice.getNoticeInfo())
//                        .build();
//                noticeResponseDtoLists.add(noticeResponseDto);
//            }
//            //거래 수락의 경우 seller의 nickname이 필요하다.
//            if (Objects.equals(notice.getNoticeInfo(), "거래 수락을 하였습니다.")) {
//                NoticeResponseDto.Notice noticeResponseDto = NoticeResponseDto.Notice.builder()
//                        .noticeId(notice.getId())
//                        .noticeName(notice.getSeller().getNickName())
//                        .noticeMsg(notice.getNoticeInfo())
//                        .build();
//                noticeResponseDtoLists.add(noticeResponseDto);
//            }
//        }


        //해당하는 user의 알림에 대하여 둘 다 해당하는 모든 List<Notice>를 뽑아내고 그의 noticetype(명칭)에 따라 구분한다.
        //List<NoticeResponseDto.Notice> noticeResponseDtoList = new ArrayList<>();

        // userid에 의한 모든 경우 알림이 없다면 알림이 존재하지 않는다는 메시지를 호출한다.
        int noticeExist = noticeValidator.ValidnoticeList(userId);
        List<com.spring.sharepod.v1.dto.response.notice.Notice> noticeList = new ArrayList<>();

        if (noticeExist == 0) {
            throw new ErrorCodeException(NOTICELIST_NOT_EXIST);
        } else {
            noticeList = noticeRepository.noticeInfoList(userId);
        }

//        for (Notice notice : noticeList) {
//            NoticeResponseDto.Notice noticeResponseDto = NoticeResponseDto.Notice.builder()
//                    .noticeId(notice.getId())
//                    .noticeName(notice.getSender().getNickName())
//                    .userRegion(notice.getSender().getUserRegion())
//                    //.startRental(noticeList.get(i).getBoard().getReservationList().get(i).getStartRental())
//                    //.endRental(noticeList.get(i).getBoard().getReservationList().get(i).getEndRental())
//                    .otherUserImg(notice.getSender().getUserImg())
//                    .noticeMsg(notice.getNoticeInfo())
//                    .boardId(notice.getBoard().getId())
//                    .isChat(notice.getIsChat())
//                    .build();
//            noticeResponseDtoList.add(noticeResponseDto);
//        }
        return NoticeInfoResponseDto.builder()
                .result("success")
                .msg("알림 목록 성공")
                .noticeList(noticeList)
                .build();
    }

    //알림 확인 삭제
    @Transactional
    public void DeleteNotice(Long noticeId, Long userId) {
        //알림이 존재하지 않을 시 에러 메시지 호출
        Notice findNoticeId = noticeValidator.ValidDeleteNotice(noticeId);

        //findNoticeId를 통해 token으로 받은 정보와 일치하는지 확인한다.
        if (Objects.equals(userId, findNoticeId.getReceiver().getId()) || Objects.equals(userId, findNoticeId.getSender().getId())) {
            //존재할 시 해당 id로 알림 삭제
            noticeRepository.deleteByNoticeId(noticeId);
        } else {
            throw new ErrorCodeException(ErrorCode.USER_NOT_FOUND);
        }

    }

}
